import asyncio
import json
import re
import traceback
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse, urlunparse

import httpx
from bs4 import BeautifulSoup
from crawl4ai import AsyncWebCrawler, BrowserConfig
from playwright.async_api import async_playwright


SITE_NAMES = {
    "abcmart": "ABC마트",
    "musinsa": "무신사",
}

SUPPORTED_SITES = list(SITE_NAMES.keys())

_ABCMART_URL = "https://www.abcmart.co.kr/abcmart/search/totalSearch.do?q={keyword}&pageIdx={page}"
_MUSINSA_URL = "https://www.musinsa.com/search/musinsa/integrated?q={keyword}"

_MUSINSA_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/json, text/plain, */*",
    "Referer": "https://www.musinsa.com/",
}


def _scroll_js(n: int) -> str:
    return f"""
(async () => {{
    for (let i = 0; i < {n}; i++) {{
        window.scrollTo(0, document.body.scrollHeight);
        await new Promise(r => setTimeout(r, 800));
    }}
}})();
"""


def _extract_prdtno(url: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", url)
    return m.group(1) if m else None


class CrawlerService:

    async def search_items(
        self, keyword: str, site: str, max_items: int = 500,
        with_reviews: bool = False, reviews_per_item: int = 5,
    ) -> tuple[list[dict], list[str]]:
        if site not in SUPPORTED_SITES:
            raise ValueError(f"지원하지 않는 사이트: {site}")

        if site == "musinsa":
            products, errors = await self._crawl_musinsa_intercept(keyword, max_items)
        elif site == "abcmart":
            products, errors = await self._crawl_abcmart(keyword, max_items)
        else:
            products, errors = [], []

        if with_reviews and site == "musinsa":
            products, rev_errors = await self._attach_reviews(products, reviews_per_item)
            errors.extend(rev_errors)

        self._save_raw(keyword, site, products)
        return products, errors

    # ── 무신사: Playwright intercept + 직접 API 페이지네이션 ──────────────

    async def _crawl_musinsa_intercept(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        collected: list[dict] = []
        seen_ids: set[str] = set()
        captured_req_url: list[str] = []  # mutable ref for closure

        try:
            async with async_playwright() as p:
                browser = await p.chromium.launch(headless=True)
                ctx = await browser.new_context(user_agent=_MUSINSA_HEADERS["User-Agent"])
                page = await ctx.new_page()

                def _parse_goods(goods_list: list) -> int:
                    added = 0
                    for g in goods_list:
                        gno = str(g.get("goodsNo", g.get("goodsId", "")))
                        if not gno or gno in seen_ids:
                            continue
                        seen_ids.add(gno)
                        collected.append({
                            "title": g.get("goodsName", g.get("name", "")),
                            "price": f"{g.get('price', g.get('salePrice', 0)):,}원",
                            "link": f"https://www.musinsa.com/products/{gno}",
                            "brand": g.get("brandName", ""),
                            "site": "musinsa",
                        })
                        added += 1
                    return added

                async def on_response(resp):
                    if "api.musinsa.com/api2/dp/v2/plp/goods" not in resp.url:
                        return
                    try:
                        data = await resp.json()
                        goods_list = (
                            data.get("data", {}).get("list", [])
                            or data.get("data", {}).get("goods", [])
                            or data.get("goods", [])
                            or []
                        )
                        added = _parse_goods(goods_list)
                        if added:
                            print(f"[API] 응답 +{added}개 -> 누적 {len(collected)}개")
                    except Exception as e:
                        errors.append(f"API 파싱 오류: {e}")

                async def on_request(req):
                    if "api.musinsa.com/api2/dp/v2/plp/goods" in req.url:
                        if not captured_req_url:
                            captured_req_url.append(req.url)

                page.on("response", on_response)
                page.on("request", on_request)

                url = _MUSINSA_URL.format(keyword=keyword)
                await page.goto(url, wait_until="networkidle", timeout=25000)

                # 스크롤로 초기 데이터 로드
                prev_count = 0
                no_new = 0
                scroll_total = 0
                while len(collected) < min(max_items, 300) and no_new < 4 and scroll_total < 60:
                    await page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                    await asyncio.sleep(2.0)
                    scroll_total += 1
                    if len(collected) == prev_count:
                        no_new += 1
                    else:
                        no_new = 0
                    prev_count = len(collected)
                    print(f"[SCROLL#{scroll_total}] {len(collected)}개 (no_new={no_new})")

                await browser.close()

            # 캡처된 request URL로 직접 API 페이지네이션
            if len(collected) < max_items and captured_req_url:
                print(f"[API-DIRECT] 스크롤 종료 {len(collected)}개 -> 직접 API 페이지네이션 시도")
                await self._paginate_musinsa_api(
                    captured_req_url[0], collected, seen_ids, max_items, errors
                )

        except Exception:
            tb = traceback.format_exc()
            errors.append(f"무신사 크롤링 예외:\n{tb}")
            print(f"[ERROR] {tb}")

        print(f"[CRAWL][musinsa] 최종 {len(collected)}개")
        return collected[:max_items], errors

    async def _paginate_musinsa_api(
        self,
        base_url: str,
        collected: list[dict],
        seen_ids: set[str],
        max_items: int,
        errors: list[str],
    ) -> None:
        parsed = urlparse(base_url)
        params = {k: v[0] for k, v in parse_qs(parsed.query).items()}

        page_num = int(params.get("page", 1)) + 1
        seen_count = len(collected)

        async with httpx.AsyncClient(headers=_MUSINSA_HEADERS, timeout=15, follow_redirects=True) as client:
            while len(collected) < max_items:
                params["page"] = str(page_num)
                params["seen"] = str(seen_count)
                new_url = urlunparse(parsed._replace(query=urlencode(params)))

                try:
                    resp = await client.get(new_url)
                    if resp.status_code != 200:
                        errors.append(f"API 직접 호출 page={page_num} HTTP {resp.status_code}")
                        break

                    data = resp.json()
                    goods_list = (
                        data.get("data", {}).get("list", [])
                        or data.get("data", {}).get("goods", [])
                        or data.get("goods", [])
                        or []
                    )
                    if not goods_list:
                        print(f"[API-DIRECT] page={page_num} 빈 응답 -> 종료")
                        break

                    added = 0
                    for g in goods_list:
                        gno = str(g.get("goodsNo", g.get("goodsId", "")))
                        if not gno or gno in seen_ids:
                            continue
                        seen_ids.add(gno)
                        collected.append({
                            "title": g.get("goodsName", g.get("name", "")),
                            "price": f"{g.get('price', g.get('salePrice', 0)):,}원",
                            "link": f"https://www.musinsa.com/products/{gno}",
                            "brand": g.get("brandName", ""),
                            "site": "musinsa",
                        })
                        added += 1

                    print(f"[API-DIRECT] page={page_num} +{added}개 -> 누적 {len(collected)}개")
                    if added == 0:
                        break

                    page_num += 1
                    seen_count = len(collected)
                    await asyncio.sleep(0.5)

                except Exception as e:
                    errors.append(f"API 직접 호출 page={page_num} 예외: {e}")
                    break

    # ── ABC마트: pageIdx 페이지네이션 + prdtNo 중복 제거 ─────────────────

    async def _crawl_abcmart(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1

        while len(all_products) < max_items:
            url = _ABCMART_URL.format(keyword=keyword, page=page)
            print(f"[CRAWL][abcmart] page={page}")

            try:
                async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
                    result = await crawler.arun(
                        url=url,
                        js_code=_scroll_js(5),
                        delay_before_return_html=5.0,
                    )
                    if not result.success:
                        errors.append(f"abcmart page {page} 로드 실패")
                        break

                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse_abcmart(soup)

                    # prdtNo 기준 중복 제거 (track= 파라미터 등 URL 변형 무시)
                    new = []
                    for prod in page_items:
                        pno = _extract_prdtno(prod["link"])
                        if pno:
                            if pno not in seen_prdt_nos:
                                seen_prdt_nos.add(pno)
                                new.append(prod)
                        else:
                            new.append(prod)

                    all_products.extend(new)
                    print(f"[CRAWL][abcmart] page={page} +{len(new)}개(중복제거) / 누적 {len(all_products)}개")

                    if not new:
                        break

            except Exception:
                tb = traceback.format_exc()
                errors.append(f"abcmart page {page} 예외:\n{tb}")
                break

            page += 1

        return all_products[:max_items], errors

    # ── 파서: ABC마트 ─────────────────────────────────────────────────────

    def _parse_abcmart(self, soup: BeautifulSoup) -> list[dict]:
        products: list[dict] = []
        items = [
            li for li in soup.select("li.col-list-item.prod-item")
            if li.select_one("a[href^='/product']")
        ]
        for item in items:
            title_el = item.select_one(".prod-name") or item.select_one("[class*='name']")
            link_el = item.select_one("a[href^='/product']")
            cost_el = item.select_one(".price-cost") or item.select_one(".price-normal-cost")
            unit_el = item.select_one(".price-unit")

            if not title_el or not cost_el:
                continue
            title = title_el.get_text(strip=True)
            if not title:
                continue
            cost = cost_el.get_text(strip=True)
            unit = unit_el.get_text(strip=True) if unit_el else "원"
            price = f"{cost}{unit}"
            href = f"https://www.abcmart.co.kr{link_el.get('href', '')}" if link_el else ""
            products.append({"title": title, "price": price, "link": href, "site": "abcmart"})
        return products

    # ── 리뷰 수집: 무신사 공개 API ───────────────────────────────────────

    async def _attach_reviews(
        self, products: list[dict], per_item: int = 5
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        headers = {"User-Agent": _MUSINSA_HEADERS["User-Agent"]}

        async with httpx.AsyncClient(headers=headers, timeout=10, follow_redirects=True) as client:
            for product in products:
                link = product.get("link", "")
                m = re.search(r"/products/(\d+)", link)
                if not m:
                    product["reviews"] = []
                    product["review_count"] = 0
                    continue

                goods_no = m.group(1)
                try:
                    resp = await client.get(
                        "https://goods.musinsa.com/api2/review/v1/view/list",
                        params={
                            "page": 0,
                            "pageSize": per_item,
                            "goodsNo": goods_no,
                            "sort": "up_cnt_desc",
                            "selectedSimilarNo": goods_no,
                            "myFilter": "false",
                            "hasPhoto": "false",
                            "isExperience": "false",
                        },
                    )
                    data = resp.json()
                    data_section = data.get("data", {})
                    reviews = []
                    for r in data_section.get("list", []):
                        reviews.append({
                            "grade": r.get("grade"),
                            "content": r.get("content", "")[:200],
                            "helpful": r.get("upCnt", 0),
                            "size_info": r.get("purchaseSizeInfo", {}).get("name", ""),
                        })
                    product["reviews"] = reviews
                    # totalCount 위치 다양한 경로 탐색
                    product["review_count"] = (
                        data_section.get("totalCount")
                        or data_section.get("total")
                        or data_section.get("count")
                        or data.get("totalCount")
                        or len(reviews)
                    )
                except Exception as e:
                    errors.append(f"리뷰 오류 goodsNo={goods_no}: {e}")
                    product["reviews"] = []
                    product["review_count"] = 0

                await asyncio.sleep(0.1)

        return products, errors

    # ── 원본 저장 ─────────────────────────────────────────────────────────

    def _save_raw(self, keyword: str, site: str, products: list[dict]) -> None:
        raw_dir = Path("output/raw")
        raw_dir.mkdir(parents=True, exist_ok=True)
        out = raw_dir / f"{site}_{keyword}_raw.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(products, f, ensure_ascii=False, indent=2)
        print(f"[RAW] {len(products)}개 -> {out}")
