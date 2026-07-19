import asyncio
import traceback
from urllib.parse import parse_qs, urlencode, urlparse, urlunparse

import httpx
from playwright.async_api import async_playwright

_URL = "https://www.musinsa.com/search/musinsa/integrated?q={keyword}"
_API_PATH = "api.musinsa.com/api2/dp/v2/plp/goods"
_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/json, text/plain, */*",
    "Referer": "https://www.musinsa.com/",
}


class MusinSaCrawler:

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        collected: list[dict] = []
        seen_ids: set[str] = set()
        captured_req_url: list[str] = []

        try:
            async with async_playwright() as p:
                browser = await p.chromium.launch(headless=True)
                ctx = await browser.new_context(user_agent=_HEADERS["User-Agent"])
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
                    if _API_PATH not in resp.url:
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
                            print(f"[MUSINSA] 응답 +{added}개 -> 누적 {len(collected)}개")
                    except Exception as e:
                        errors.append(f"API 파싱 오류: {e}")

                async def on_request(req):
                    if _API_PATH in req.url and not captured_req_url:
                        captured_req_url.append(req.url)

                page.on("response", on_response)
                page.on("request", on_request)

                await page.goto(_URL.format(keyword=keyword), wait_until="networkidle", timeout=25000)

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
                    print(f"[MUSINSA] scroll#{scroll_total} {len(collected)}개 (no_new={no_new})")

                await browser.close()

            if len(collected) < max_items and captured_req_url:
                print(f"[MUSINSA] 직접 API 페이지네이션 시도 ({len(collected)}개 -> {max_items}개 목표)")
                await self._paginate_api(captured_req_url[0], collected, seen_ids, max_items, errors)

        except Exception:
            tb = traceback.format_exc()
            errors.append(f"무신사 크롤링 예외:\n{tb}")
            print(f"[MUSINSA][ERROR] {tb}")

        print(f"[MUSINSA] 최종 {len(collected)}개")
        return collected[:max_items], errors

    async def _paginate_api(
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

        async with httpx.AsyncClient(headers=_HEADERS, timeout=15, follow_redirects=True) as client:
            while len(collected) < max_items:
                params["page"] = str(page_num)
                params["seen"] = str(seen_count)
                url = urlunparse(parsed._replace(query=urlencode(params)))

                try:
                    resp = await client.get(url)
                    if resp.status_code != 200:
                        errors.append(f"직접 API page={page_num} HTTP {resp.status_code}")
                        break

                    data = resp.json()
                    goods_list = (
                        data.get("data", {}).get("list", [])
                        or data.get("data", {}).get("goods", [])
                        or data.get("goods", [])
                        or []
                    )
                    if not goods_list:
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

                    print(f"[MUSINSA] 직접 API page={page_num} +{added}개 -> 누적 {len(collected)}개")
                    if added == 0:
                        break

                    page_num += 1
                    seen_count = len(collected)
                    await asyncio.sleep(0.5)

                except Exception as e:
                    errors.append(f"직접 API page={page_num} 예외: {e}")
                    break
