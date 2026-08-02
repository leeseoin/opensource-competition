import asyncio

import httpx

from ..base import SiteCrawler
from .detail_fetcher import Cm29DetailFetcher

_LISTING_URL = "https://display-bff-api.29cm.co.kr/api/v1/listing/items?colorchipVariant=treatment"
_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://www.29cm.co.kr/",
    "Content-Type": "application/json",
}
_PAGE_SIZE = 50


def _format_won(amount: int | float | None) -> str:
    if amount is None:
        return ""
    return f"{int(amount):,}원"


class Cm29Crawler(SiteCrawler):
    """29CM 상품 크롤러.

    29CM 검색 결과는 SPA(Next.js)라 HTML에 상품 정보가 없다 — 실제로는
    display-bff-api.29cm.co.kr의 내부 검색 API(POST, JSON)를 호출해서 렌더링한다.
    ABC마트처럼 브라우저 렌더링(crawl4ai) 없이 httpx로 그 API를 직접 호출하면 된다.
    """

    site_id = "29cm"

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        all_products: list[dict] = []
        seen_ids: set[str] = set()
        page = 1

        async with httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=True) as client:
            while len(all_products) < max_items:
                body = {
                    "keyword": keyword,
                    "pageType": "SRP",
                    "sortType": "RECOMMENDED",
                    "facets": {},
                    "pageRequest": {"page": page, "size": _PAGE_SIZE},
                }
                print(f"[29CM:search] page={page} keyword={keyword}")

                try:
                    r = await client.post(_LISTING_URL, json=body)
                    r.raise_for_status()
                    data = r.json().get("data", {})
                except Exception as e:
                    errors.append(f"page {page} 요청 실패: {e}")
                    break

                items = data.get("list", [])
                new = self._dedup(self._parse(items), seen_ids)
                all_products.extend(new)
                print(f"[29CM:search] page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                if not new or not data.get("pagination", {}).get("hasNext", False):
                    break

                page += 1
                await asyncio.sleep(1)

        print(f"[29CM:search] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def crawl_category(
        self, category: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """29CM 카테고리 코드 매핑은 아직 조사하지 않았다 — 검색(crawl)만 지원한다."""
        return [], ["29CM 카테고리 크롤링은 아직 미구현 (키워드 검색만 지원)"]

    async def attach_details(
        self, products: list[dict], limit: int, review_limit: int = 20
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 상세 페이지 데이터(평점·리뷰수·다중이미지·카테고리·옵션·리뷰)를 추가."""
        return await Cm29DetailFetcher().attach(products, limit=limit, review_limit=review_limit)

    def _dedup(self, items: list[dict], seen: set[str]) -> list[dict]:
        new = []
        for prod in items:
            pid = prod["source_product_id"]
            if pid and pid not in seen:
                seen.add(pid)
                new.append(prod)
        return new

    def _parse(self, items: list[dict]) -> list[dict]:
        products: list[dict] = []
        for item in items:
            info = item.get("itemInfo", {})
            url = item.get("itemUrl", {})

            sale_rate = info.get("saleRate")
            discount_percent = round(sale_rate) if sale_rate is not None else None

            products.append({
                "source_product_id": str(item.get("itemId", "")),
                "title": info.get("productName", ""),
                "brand": info.get("brandName") or "",
                "price": _format_won(info.get("sellPrice")),
                "price_original": _format_won(info.get("originalPrice")),
                "discount_percent": discount_percent,
                "image_url": info.get("thumbnailUrl") or "",
                "color": "",  # 29CM 목록 API는 카드 단위 색상 정보를 안 준다
                "style_code": "",  # 29CM 목록 API는 style code를 안 준다
                "link": url.get("webLink", ""),
                "site": "29cm",
            })
        return products
