"""ABC마트 공개 검색 JSON을 비교 계약 상품으로 변환한다."""

from __future__ import annotations

from typing import Any

import httpx

from ..models import MerchantRequestError, PageResult
from .base import MerchantAdapter

_SEARCH_ENDPOINT = "https://abcmart.a-rt.com/display/search-word/result-total/list"
_PRODUCT_ENDPOINT = "https://abcmart.a-rt.com/product"


def _won(value: Any) -> str:
    """ABC마트 숫자 문자열을 v1-unified 원화 문자열로 바꾼다."""

    if value in (None, ""):
        return ""
    try:
        return f"{int(str(value).strip()):,}원"
    except ValueError as exc:
        raise MerchantRequestError(f"ABC마트 가격을 해석할 수 없습니다: {value!r}") from exc


def _optional_int(value: Any, field: str) -> int | None:
    """비어 있을 수 있는 0 이상의 정수 필드를 검증한다."""

    if value in (None, ""):
        return None
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise MerchantRequestError(f"ABC마트 {field} 값을 해석할 수 없습니다: {value!r}") from exc
    if parsed < 0:
        raise MerchantRequestError(f"ABC마트 {field} 값이 음수입니다: {parsed}")
    return parsed


def _category_path(value: Any) -> list[str]:
    """`신발 > 구두` 형식을 빈 값 없는 카테고리 목록으로 바꾼다."""

    return [part.strip() for part in str(value or "").split(">") if part.strip()]


def _ordered_sizes(option_text: Any, size_list: Any) -> list[str]:
    """ABC마트 옵션 표시 순서로 사이즈를 중복 없이 반환한다."""

    stock = size_list if isinstance(size_list, dict) else {}
    ordered: list[str] = []
    seen: set[str] = set()
    for raw in str(option_text or "").split(","):
        size = raw.strip()
        if size and size not in seen:
            ordered.append(size)
            seen.add(size)
    for size in sorted(str(key) for key in stock):
        if size not in seen:
            ordered.append(size)
    return ordered


def parse_item(item: dict[str, Any]) -> dict[str, Any]:
    """ABC마트 원본 상품 한 건을 v1-unified 상품으로 변환한다.

    Raises:
        MerchantRequestError: 필수 상품 번호/이름 또는 숫자 필드가 잘못된 경우다.
    """

    product_id = str(item.get("PRDT_NO") or "")
    title = str(item.get("PRDT_NAME") or "")
    if not product_id or not title:
        raise MerchantRequestError("ABC마트 상품 번호 또는 이름이 비어 있습니다")
    category_path = _category_path(item.get("CTGR_NAME_ALL"))
    color = str(item.get("COLOR_ID") or "")
    image = str(item.get("PRDT_IMAGE_URL") or "")
    sold_out = str(item.get("SOLD_OUT") or "").lower() == "y"
    discount = _optional_int(item.get("DISCOUNT_RATE"), "할인율")
    review_count = _optional_int(item.get("RVW_COUNT"), "리뷰 수")

    return {
        "source_product_id": product_id,
        "title": title,
        "brand": str(item.get("BRAND_NAME") or ""),
        "price": _won(item.get("PRDT_DC_PRICE")),
        "price_original": _won(item.get("NRMAL_AMT")),
        "discount_percent": discount,
        "image_url": image,
        "images": [image] if image else [],
        "color": color,
        "style_code": str(item.get("STYLE_INFO") or ""),
        "link": f"{_PRODUCT_ENDPOINT}?prdtNo={product_id}",
        "site": "abcmart",
        "rating": None,
        "review_count": review_count,
        "category": category_path[-1] if category_path else "",
        "category_path": " > ".join(category_path),
        "in_stock": not sold_out,
        "options": {
            "colors": [color] if color else [],
            "sizes": _ordered_sizes(item.get("PRDT_OPTION"), item.get("SIZE_LIST")),
        },
        "reviews": [],
    }


class AbcMartAdapter(MerchantAdapter):
    """ABC마트 검색 JSON의 pagination과 상품 변환을 담당한다."""

    merchant = "abcmart"

    async def fetch_page(
        self,
        client: httpx.AsyncClient,
        query: str,
        page: int,
        page_size: int,
    ) -> PageResult:
        """ABC마트 공개 검색 JSON 한 페이지를 요청하고 검증한다."""

        params = {
            "sort": "point",
            "page": page,
            "perPage": page_size,
            "pageColumn": 3,
            "smartSearchCheck": "true",
            "deviceCode": "10000",
            "searchWord": query,
            "firstSearchWord": query,
            "tabGubun": "total",
            "searchPageGubun": "product",
            "firstSearchYn": "Y",
            "channel": "10001",
            "resultChannel": "10001",
            "memberTypeCode": "10002",
        }
        response = await client.get(_SEARCH_ENDPOINT, params=params)
        if response.status_code != 200:
            raise MerchantRequestError(
                f"ABC마트가 HTTP {response.status_code}를 반환했습니다",
                status_code=response.status_code,
                retryable=response.status_code >= 500,
            )
        try:
            payload = response.json()
        except ValueError as exc:
            raise MerchantRequestError("ABC마트 응답이 올바른 JSON이 아닙니다") from exc
        if not isinstance(payload.get("SEARCH"), list):
            raise MerchantRequestError("ABC마트 응답에서 SEARCH 목록을 찾지 못했습니다")
        page_info = payload.get("PAGE")
        if not isinstance(page_info, dict) or "finalPageNo" not in page_info or "SEARCH_COUNT" not in payload:
            raise MerchantRequestError("ABC마트 응답에서 pagination 정보를 찾지 못했습니다")
        products = [parse_item(item) for item in payload["SEARCH"]]
        final_page = int(page_info["finalPageNo"])
        return PageResult(products=products, has_next=page < final_page, total_count=int(payload["SEARCH_COUNT"]))
