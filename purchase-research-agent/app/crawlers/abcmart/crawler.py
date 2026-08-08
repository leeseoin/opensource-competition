import re
import traceback
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode

import httpx
from bs4 import BeautifulSoup
from crawl4ai import AsyncWebCrawler, BrowserConfig

from ..base import SiteCrawler, jittered_sleep
from .detail_fetcher import DetailFetcher
from .json_fetcher import AbcJsonFetcher
from .verification import mark_failed, reconcile_page

_SEARCH_PAGE = "https://abcmart.a-rt.com/display/search-word/result"
_PAGE_SIZE = 30
_CATEGORY_URL = "https://abcmart.a-rt.com/display/category/main?genderGbnCode={gender}&ctgrNo={ctgrNo}&page={page}"
_BRAND_URL = (
    "https://abcmart.a-rt.com/product/brand/page"
    "?brandNo={brandNo}&tChnnlNo={tChnnlNo}&genderGbnCode={gender}&page={page}"
)
_PRODUCT_BASE = "https://abcmart.a-rt.com/product?prdtNo="
_HTML_DIR = Path("output/raw_html/abcmart")


def _save_html(html: str, label: str, page: int, ts_file: str) -> None:
    _HTML_DIR.mkdir(parents=True, exist_ok=True)
    out = _HTML_DIR / f"abcmart_{label}_{ts_file}_page{page}.html"
    out.write_text(html, encoding="utf-8")

# 카테고리명 -> (ctgrNo, genderGbnCode)
CATEGORIES: dict[str, tuple[str, str]] = {
    # 신발 전체
    "신발_남성":     ("1000000441", "10000"),
    "신발_여성":     ("1000000441", "10001"),
    "신발_아동":     ("1000000441", "10002"),
    # 스니커즈
    "스니커즈_남성": ("1000000245", "10000"),
    "스니커즈_여성": ("1000000245", "10001"),
    # 스포츠
    "스포츠_남성":   ("1000000249", "10000"),
    "스포츠_여성":   ("1000000249", "10001"),
    # 러닝화
    "러닝화_남성":   ("1000000250", "10000"),
    "러닝화_여성":   ("1000000250", "10001"),
    # 샌들
    "샌들_남성":     ("1000000260", "10000"),
    "샌들_여성":     ("1000000260", "10001"),
    # 부츠
    "부츠_남성":     ("1000000266", "10000"),
    "부츠_여성":     ("1000000266", "10001"),
    # 구두
    "구두_남성":     ("1000000254", "10000"),
    "구두_여성":     ("1000000254", "10001"),
    # 의류
    "의류_남성":     ("1000000442", "10000"),
    "의류_여성":     ("1000000442", "10001"),
    # 잡화
    "잡화_남성":     ("1000000443", "10000"),
    "잡화_여성":     ("1000000443", "10001"),
}

# 브랜드명 -> (brandNo, tChnnlNo). tChnnlNo: 10001=ABC-MART 일반, 10002=GRAND STAGE(프리미엄).
# 사이트에서 실제로 확인된 일부만 채워둔 시드 목록이라, 필요한 브랜드는 직접
# https://abcmart.a-rt.com/product/brand 에서 brandNo/tChnnlNo를 확인해 추가해야 한다.
BRANDS: dict[str, tuple[str, str]] = {
    "나이키": ("000050", "10001"),
    "아디다스": ("000003", "10001"),
    "뉴발란스": ("000048", "10001"),
    "조던": ("090050", "10002"),
}


def _extract_prdtno(url: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", url)
    return m.group(1) if m else None


class AbcMartCrawler(SiteCrawler):

    site_id = "abcmart"

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """JSON을 기본 수집하고 같은 모든 검색 페이지의 HTML을 전수 검증한다."""

        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        json_fetcher = AbcJsonFetcher()
        page = 1
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        headers = {
            "User-Agent": "PurchaseResearchAgent/0.1 (+public product verification; low rate)",
            "Accept": "application/json",
            "Accept-Language": "ko-KR",
        }
        async with httpx.AsyncClient(headers=headers, timeout=15, follow_redirects=False) as client, \
                AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                html_url = self._search_url(keyword, page, _PAGE_SIZE)
                print(f"[ABCMART:full] page={page} keyword={keyword}")

                # 페이지 요청 1건의 일시적 네트워크 예외로 남은 페이지 전체를 포기하지
                # 않도록, 실패 시 짧은 대기 후 한 번 더 시도한다(2026-08-06 실측:
                # 동시 워커 부하 중 순간적인 연결 예외가 재시도 없이 pagination을
                # 통째로 끊어 목표치의 88%를 날린 사례 확인).
                page_succeeded = False
                last_tb = ""
                for attempt in range(2):
                    try:
                        json_page = await json_fetcher.fetch_page(
                            client, keyword, page, _PAGE_SIZE, ts_file,
                        )
                        result = await crawler.arun(url=html_url, delay_before_return_html=2.0)
                        remaining = max_items - len(all_products)
                        selected_json_products = json_page.products[:remaining]
                        if result.success:
                            _save_html(result.html, keyword, page, ts_file)
                            html_items = self._parse(BeautifulSoup(result.html, "html.parser"))
                            selected_ids = {
                                str(product.get("source_product_id") or "")
                                for product in selected_json_products
                            }
                            selected_html_items = [
                                product for product in html_items
                                if str(product.get("source_product_id") or "") in selected_ids
                            ]
                            page_items, verification_errors = reconcile_page(
                                selected_json_products,
                                selected_html_items,
                                json_source_url=json_page.source_url,
                                html_source_url=html_url,
                            )
                        else:
                            page_items, verification_errors = mark_failed(
                                selected_json_products,
                                json_source_url=json_page.source_url,
                                html_source_url=html_url,
                                reason=f"page {page} browser 로드 실패",
                            )
                        errors.extend(verification_errors)
                        new = self._dedup(page_items, seen_prdt_nos)
                        all_products.extend(new)
                        print(f"[ABCMART:full] page={page} +{len(new)}개 / 누적 {len(all_products)}개")
                        page_succeeded = True
                        break

                    except Exception:
                        last_tb = traceback.format_exc()
                        if attempt == 0:
                            print(f"[ABCMART:full] page={page} 예외, 재시도 1회")
                            await jittered_sleep(2.0, spread=1.0)

                if not page_succeeded:
                    errors.append(f"page {page} 예외(재시도 후에도 실패):\n{last_tb}")
                    break

                if not new or not json_page.has_next:
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:full] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _search_url(self, keyword: str, page: int, page_size: int) -> str:
        """JSON 요청과 동일한 검색어, 페이지 및 페이지 크기의 화면 URL을 만든다."""

        params = {
            "channel": "10001",
            "searchWord": keyword,
            "smartSearchCheck": "true",
            "page": page,
            "perPage": page_size,
            "pageColumn": 3,
            "sort": "point",
            "tabGubun": "total",
            "searchPageGubun": "product",
        }
        return f"{_SEARCH_PAGE}?{urlencode(params)}"

    async def crawl_category(
        self, category: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """카테고리 기반 크롤링 (CATEGORIES 딕셔너리 키 사용)"""
        if category not in CATEGORIES:
            return [], [f"알 수 없는 카테고리: {category}. 가능한 값: {list(CATEGORIES.keys())}"]

        ctgrNo, gender = CATEGORIES[category]
        return await self.crawl_category_by_no(ctgrNo, gender, max_items, label=category)

    async def crawl_category_by_no(
        self, ctgrNo: str, gender: str, max_items: int, label: str = ""
    ) -> tuple[list[dict], list[str]]:
        """ctgrNo + genderGbnCode 직접 지정 크롤링"""
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1
        tag = label or f"ctgrNo={ctgrNo}/gender={gender}"
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _CATEGORY_URL.format(ctgrNo=ctgrNo, gender=gender, page=page)
                print(f"[ABCMART:category] {tag} page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"[{tag}] page {page} 로드 실패")
                        break

                    _save_html(result.html, tag, page, ts_file)
                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:category] {tag} page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception:
                    tb = traceback.format_exc()
                    errors.append(f"[{tag}] page {page} 예외:\n{tb}")
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:category] {tag} 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def crawl_by_brand(
        self, brand: str, max_items: int, gender: str = "10000"
    ) -> tuple[list[dict], list[str]]:
        """브랜드 기반 크롤링 (BRANDS 딕셔너리 키 사용, gender는 genderGbnCode)."""
        if brand not in BRANDS:
            return [], [f"알 수 없는 브랜드: {brand}. 가능한 값: {list(BRANDS.keys())}"]

        brand_no, channel_no = BRANDS[brand]
        return await self.crawl_by_brand_no(brand_no, channel_no, gender, max_items, label=brand)

    async def crawl_by_brand_no(
        self, brand_no: str, channel_no: str, gender: str, max_items: int, label: str = ""
    ) -> tuple[list[dict], list[str]]:
        """brandNo + tChnnlNo + genderGbnCode 직접 지정 크롤링"""
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1
        tag = label or f"brandNo={brand_no}/channel={channel_no}/gender={gender}"
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _BRAND_URL.format(brandNo=brand_no, tChnnlNo=channel_no, gender=gender, page=page)
                print(f"[ABCMART:brand] {tag} page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"[{tag}] page {page} 로드 실패")
                        break

                    _save_html(result.html, tag, page, ts_file)
                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:brand] {tag} page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception:
                    tb = traceback.format_exc()
                    errors.append(f"[{tag}] page {page} 예외:\n{tb}")
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:brand] {tag} 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def attach_details(
        self, products: list[dict], limit: int
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 리뷰/옵션을 덧붙인다 (ABC마트 내부 API 사용)."""
        return await DetailFetcher().attach(products, limit=limit)

    def _dedup(self, items: list[dict], seen: set[str]) -> list[dict]:
        new = []
        for prod in items:
            pno = _extract_prdtno(prod["link"])
            if pno:
                if pno not in seen:
                    seen.add(pno)
                    new.append(prod)
            else:
                new.append(prod)
        return new

    def _parse(self, soup: BeautifulSoup) -> list[dict]:
        products: list[dict] = []
        items = soup.select("li.col-list-item.prod-item")
        for item in items:
            prdtno = item.get("data-product-no", "")

            title_el = item.select_one(".prod-name")
            brand_el = item.select_one(".prod-brand")
            cost_el = item.select_one(".price-cost") or item.select_one(".price-normal-cost")
            normal_cost_el = item.select_one(".price-normal-cost")
            unit_el = item.select_one(".price-unit")
            discount_el = item.select_one(".price-sale-percent")
            img_el = item.select_one(".img-wrap img")
            prod_link_el = item.select_one("a.prod-link")

            if not title_el or not cost_el:
                continue

            # badge-gender 텍스트(남성/여성) 제거 후 제목 추출
            for badge in title_el.select(".badge-gender"):
                badge.decompose()
            title = title_el.get_text(strip=True)
            if not title:
                continue

            cost = cost_el.get_text(strip=True)
            unit = unit_el.get_text(strip=True) if unit_el else "원"
            brand = brand_el.get_text(strip=True) if brand_el else ""
            normal_cost = normal_cost_el.get_text(strip=True) if normal_cost_el else ""

            discount_text = discount_el.get_text(strip=True) if discount_el else ""
            discount_match = re.search(r"\d+", discount_text)
            discount_percent = int(discount_match.group()) if discount_match else None

            image_url = img_el.get("src", "") if img_el else ""
            color = prod_link_el.get("data-prdt-color-info", "") if prod_link_el else ""
            style_code = prod_link_el.get("data-style-info", "") if prod_link_el else ""

            if prdtno:
                link = f"{_PRODUCT_BASE}{prdtno}"
            else:
                link_el = prod_link_el or item.select_one("a[href*='prdtNo']") or item.select_one("a[href^='/product']")
                href = link_el.get("href", "") if link_el else ""
                pno = _extract_prdtno(href)
                link = f"{_PRODUCT_BASE}{pno}" if pno else f"https://www.abcmart.co.kr{href}"

            source_product_id = prdtno or _extract_prdtno(link) or ""

            products.append({
                "source_product_id": source_product_id,
                "title": title,
                "brand": brand,
                "price": f"{cost}{unit}",
                "price_original": f"{normal_cost}{unit}" if normal_cost else "",
                "discount_percent": discount_percent,
                "image_url": image_url,
                "color": color,
                "style_code": style_code,
                "link": link,
                "site": "abcmart",
            })
        return products
