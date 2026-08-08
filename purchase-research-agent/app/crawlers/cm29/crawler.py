import json
from datetime import datetime
from pathlib import Path

import httpx

from ..base import SiteCrawler, jittered_sleep
from .detail_fetcher import Cm29DetailFetcher
from .verification import verify_products

_LISTING_URL = "https://display-bff-api.29cm.co.kr/api/v1/listing/items?colorchipVariant=treatment"
_HEADERS = {
    "User-Agent": "PurchaseResearchAgent/0.1 (+public product verification; low rate)",
    "Referer": "https://www.29cm.co.kr/",
    "Content-Type": "application/json",
}
_PAGE_SIZE = 50
_JSON_DIR = Path("output/raw_json/29cm")


def _format_won(amount: int | float | None) -> str:
    """선택 숫자 금액을 쉼표가 포함된 원화 문자열로 변환한다."""

    if amount is None:
        return ""
    return f"{int(amount):,}원"


class Cm29Crawler(SiteCrawler):
    """29CM 상품 크롤러.

    검색 목록은 JSON을 기본값으로 사용하고, 수집 대상으로 선택한 모든 상품의 공개
    상세 HTML에 포함된 Product JSON-LD를 비교해 상품별 검증 결과를 만든다.
    """

    site_id = "29cm"

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """29CM 검색 JSON을 수집하고 선택 상품 전체의 상세 HTML을 검증한다.

        Args:
            keyword: 실제 29CM 검색어다.
            max_items: 저장하고 검증할 최대 고유 상품 수다.

        Returns:
            검증 결과가 포함된 상품 목록과 수집 또는 비교 경고다.
        """

        errors: list[str] = []
        all_products: list[dict] = []
        seen_ids: set[str] = set()
        page = 1
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

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
                    payload = r.json()
                    data = payload.get("data", {})
                    self._save_json(payload, keyword, page, ts_file)
                except Exception as e:
                    errors.append(f"page {page} 요청 실패: {e}")
                    break

                items = data.get("list", [])
                new = self._dedup(self._parse(items), seen_ids)
                remaining = max_items - len(all_products)
                selected = new[:remaining]
                selected, verification_errors = await verify_products(
                    client,
                    selected,
                    json_source_url=str(r.url),
                    ts_file=ts_file,
                )
                errors.extend(verification_errors)
                all_products.extend(selected)
                print(f"[29CM:search] page={page} +{len(selected)}개 / 누적 {len(all_products)}개")

                if not new or not data.get("pagination", {}).get("hasNext", False):
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[29CM:search] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _save_json(self, payload: dict, keyword: str, page: int, ts_file: str) -> None:
        """29CM 검색 JSON 원본을 실행 시각과 페이지별 파일로 저장한다."""

        _JSON_DIR.mkdir(parents=True, exist_ok=True)
        output = _JSON_DIR / f"29cm_{keyword}_{ts_file}_page{page}.json"
        output.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    async def crawl_category(
        self, category: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """29CM 카테고리 코드 매핑은 아직 조사하지 않았다 — 검색(crawl)만 지원한다."""
        return [], ["29CM 카테고리 크롤링은 아직 미구현 (키워드 검색만 지원)"]

    async def attach_details(
        self, products: list[dict], limit: int, review_limit: int = 0
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 상세 페이지 데이터(평점·리뷰수·다중이미지·카테고리·옵션·리뷰)를 추가.
        review_limit이 0이면 상품당 리뷰를 페이지네이션으로 전부 가져온다."""
        return await Cm29DetailFetcher().attach(products, limit=limit, review_limit=review_limit)

    def _dedup(self, items: list[dict], seen: set[str]) -> list[dict]:
        """이미 수집한 29CM 상품 ID를 제외하고 새 상품만 반환한다."""

        new = []
        for prod in items:
            pid = prod["source_product_id"]
            if pid and pid not in seen:
                seen.add(pid)
                new.append(prod)
        return new

    def _parse(self, items: list[dict]) -> list[dict]:
        """29CM 검색 JSON 상품을 Python 크롤러의 공통 원본 구조로 변환한다."""

        products: list[dict] = []
        for item in items:
            info = item.get("itemInfo", {})
            url = item.get("itemUrl", {})

            sell_price = info.get("sellPrice")
            original_price = info.get("originalPrice")
            discount_percent = _discount_percent(sell_price, original_price)

            products.append({
                "source_product_id": str(item.get("itemId", "")),
                "title": info.get("productName", ""),
                "brand": info.get("brandName") or "",
                "price": _format_won(sell_price),
                "price_original": _format_won(original_price),
                "discount_percent": discount_percent,
                "image_url": info.get("thumbnailUrl") or "",
                "color": "",  # 29CM 목록 API는 카드 단위 색상 정보를 안 준다
                "style_code": "",  # 29CM 목록 API는 style code를 안 준다
                "link": url.get("webLink", ""),
                "site": "29cm",
            })
        return products


def _discount_percent(
    sell_price: int | float | None,
    original_price: int | float | None,
) -> int | None:
    """저장하는 일반 판매가와 정상가를 기준으로 할인율을 계산한다.

    Args:
        sell_price: 쿠폰 적용 전 일반 판매가다.
        original_price: 할인 전 정상가다.

    Returns:
        반올림한 일반 판매 할인율이며 계산할 수 없으면 ``None``이다.
    """

    if sell_price is None or original_price in (None, 0):
        return None
    return round((float(original_price) - float(sell_price)) * 100 / float(original_price))
