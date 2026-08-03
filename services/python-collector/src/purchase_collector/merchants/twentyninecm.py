"""29CM 공개 검색 화면의 JSON 응답을 비교 계약 상품으로 변환한다."""

from __future__ import annotations

from typing import Any

import httpx

from ..models import MerchantRequestError, PageResult
from .base import MerchantAdapter

_LISTING_ENDPOINT = "https://display-bff-api.29cm.co.kr/api/v1/listing/items?colorchipVariant=control"


def _won(value: Any) -> str:
    """29CM 정수 가격을 v1-unified 원화 문자열로 바꾼다."""

    if value in (None, ""):
        return ""
    try:
        return f"{int(value):,}원"
    except (TypeError, ValueError) as exc:
        raise MerchantRequestError(f"29CM 가격을 해석할 수 없습니다: {value!r}") from exc


def _category_path(item: dict[str, Any]) -> list[str]:
    """29CM eventProperties에서 대/중/소 카테고리를 순서대로 꺼낸다."""

    event = item.get("itemEvent") or {}
    properties = event.get("eventProperties") or {}
    values = (
        properties.get("largeCategoryName"),
        properties.get("middleCategoryName"),
        properties.get("smallCategoryName"),
    )
    return [str(value) for value in values if value]


def parse_item(item: dict[str, Any]) -> dict[str, Any] | None:
    """29CM 원본 상품 한 건을 v1-unified 상품으로 변환한다.

    PRODUCT가 아닌 광고/콘텐츠 항목은 None으로 제외한다.
    """

    if item.get("itemType") != "PRODUCT":
        return None
    product_id = str(item.get("itemId") or "")
    info = item.get("itemInfo") or {}
    title = str(info.get("productName") or "")
    if not product_id or not title:
        return None
    item_url = item.get("itemUrl") or {}
    category_path = _category_path(item)
    image = str(info.get("thumbnailUrl") or "")
    review_count = info.get("reviewCount")
    rating = info.get("reviewScore") if review_count and info.get("reviewScore") else None
    original_price = info.get("originalPrice")
    display_price = info.get("displayPrice", info.get("sellPrice"))
    sale_rate = info.get("saleRate")

    return {
        "source_product_id": product_id,
        "title": title,
        "brand": str(info.get("brandName") or ""),
        "price": _won(display_price),
        "price_original": _won(original_price),
        "discount_percent": round(float(sale_rate)) if sale_rate is not None else None,
        "image_url": image,
        "images": [image] if image else [],
        "color": "",
        "style_code": "",
        "link": str(item_url.get("webLink") or f"https://product.29cm.co.kr/catalog/{product_id}"),
        "site": "29cm",
        "rating": float(rating) if rating is not None else None,
        "review_count": int(review_count) if review_count is not None else None,
        "category": category_path[-1] if category_path else "",
        "category_path": " > ".join(category_path),
        "in_stock": not bool(info.get("isSoldOut")),
        "options": {"colors": [], "sizes": []},
        "reviews": [],
    }


class TwentyNineCmAdapter(MerchantAdapter):
    """29CM 검색 JSON의 pagination과 상품 변환을 담당한다."""

    merchant = "29cm"

    async def fetch_page(
        self,
        client: httpx.AsyncClient,
        query: str,
        page: int,
        page_size: int,
    ) -> PageResult:
        """29CM 공개 검색 JSON 한 페이지를 요청하고 검증한다."""

        body = {
            "keyword": query,
            "pageType": "SRP",
            "sortType": "RECOMMENDED",
            "facets": {},
            "pageRequest": {"page": page, "size": page_size},
        }
        response = await client.post(_LISTING_ENDPOINT, json=body)
        if response.status_code != 200:
            raise MerchantRequestError(
                f"29CM가 HTTP {response.status_code}를 반환했습니다",
                status_code=response.status_code,
                retryable=response.status_code >= 500,
            )
        try:
            payload = response.json()
        except ValueError as exc:
            raise MerchantRequestError("29CM 응답이 올바른 JSON이 아닙니다") from exc
        if (payload.get("meta") or {}).get("result") != "SUCCESS":
            raise MerchantRequestError("29CM 응답의 meta.result가 SUCCESS가 아닙니다")
        data = payload.get("data") or {}
        pagination = data.get("pagination")
        if not isinstance(data.get("list"), list) or not isinstance(pagination, dict):
            raise MerchantRequestError("29CM 응답에서 상품 목록 또는 pagination을 찾지 못했습니다")
        if "hasNext" not in pagination or "totalCount" not in pagination:
            raise MerchantRequestError("29CM pagination 필드가 누락됐습니다")
        products = [product for raw in data["list"] if (product := parse_item(raw)) is not None]
        return PageResult(
            products=products,
            has_next=bool(pagination["hasNext"]),
            total_count=int(pagination["totalCount"]),
        )
