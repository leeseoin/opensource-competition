"""ABC마트 공개 검색 JSON을 페이지 단위로 수집하고 원본 형태를 정규화한다."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from app.crawlers.access_safety import ensure_success

_SEARCH_ENDPOINT = "https://abcmart.a-rt.com/display/search-word/result-total/list"
_PRODUCT_BASE = "https://abcmart.a-rt.com/product?prdtNo="
_JSON_DIR = Path("output/raw_json/abcmart")


@dataclass(slots=True)
class AbcJsonPage:
    """ABC마트 JSON 한 페이지의 상품과 pagination 및 원본 출처를 보관한다."""

    products: list[dict[str, Any]]
    total_count: int
    has_next: bool
    source_url: str


class AbcJsonFetcher:
    """ABC마트 검색 JSON을 요청하고 Python 원본 상품 구조로 변환한다."""

    async def fetch_page(
        self,
        client: httpx.AsyncClient,
        keyword: str,
        page: int,
        page_size: int,
        ts_file: str,
    ) -> AbcJsonPage:
        """검색 JSON 한 페이지를 요청하고 원본 저장 및 정규화를 수행한다.

        Args:
            client: 요청 간 연결을 재사용할 HTTP client다.
            keyword: ABC마트 검색어다.
            page: 1부터 시작하는 페이지 번호다.
            page_size: 페이지당 상품 수다.
            ts_file: 원본 JSON 파일명에 사용할 실행 시각이다.

        Returns:
            상품 목록과 전체 개수 및 다음 페이지 정보다.

        Raises:
            MerchantAccessError: redirect, 차단, rate limit 또는 HTTP 실패인 경우다.
            RuntimeError: 필수 JSON 구조가 없거나 JSON을 해석할 수 없는 경우다.
        """

        params = {
            "sort": "point",
            "page": page,
            "perPage": page_size,
            "pageColumn": 3,
            "smartSearchCheck": "true",
            "deviceCode": "10000",
            "searchWord": keyword,
            "firstSearchWord": keyword,
            "tabGubun": "total",
            "searchPageGubun": "product",
            "firstSearchYn": "Y",
            "channel": "10001",
            "resultChannel": "10001",
            "memberTypeCode": "10002",
        }
        response = await client.get(_SEARCH_ENDPOINT, params=params)
        ensure_success(response, "abcmart")
        try:
            payload = response.json()
        except ValueError as exc:
            raise RuntimeError("ABC마트 응답이 올바른 JSON이 아닙니다") from exc
        raw_items = payload.get("SEARCH")
        page_info = payload.get("PAGE")
        if not isinstance(raw_items, list) or not isinstance(page_info, dict):
            raise RuntimeError("ABC마트 JSON에서 SEARCH 또는 PAGE를 찾지 못했습니다")

        try:
            _JSON_DIR.mkdir(parents=True, exist_ok=True)
            safe_keyword = "".join(c if c not in r'\/:*?"<>|' else "_" for c in keyword)
            output = _JSON_DIR / f"abcmart_{safe_keyword}_{ts_file}_page{page}.json"
            output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        except OSError:
            pass

        final_page = int(page_info.get("finalPageNo") or page)
        return AbcJsonPage(
            products=[self._parse_item(item) for item in raw_items],
            total_count=int(payload.get("SEARCH_COUNT") or len(raw_items)),
            has_next=page < final_page,
            source_url=str(response.url),
        )

    def _parse_item(self, item: dict[str, Any]) -> dict[str, Any]:
        """ABC마트 JSON 상품 한 건을 기존 Python 크롤러 상품 구조로 변환한다.

        Args:
            item: 공개 검색 JSON의 상품 객체다.

        Returns:
            가격, category, 판매 가능 사이즈와 재고 상태가 포함된 원본 상품이다.

        Raises:
            RuntimeError: 상품 ID 또는 이름이 없는 경우다.
        """

        product_id = str(item.get("PRDT_NO") or "")
        title = str(item.get("PRDT_NAME") or "")
        if not product_id or not title:
            raise RuntimeError("ABC마트 JSON 상품 ID 또는 이름이 비어 있습니다")
        color = str(item.get("COLOR_ID") or "")
        category_path = str(item.get("CTGR_NAME_ALL") or "")
        sizes = _sizes(item.get("PRDT_OPTION"), item.get("SIZE_LIST"))
        available_sizes = sizes if isinstance(item.get("SIZE_LIST"), dict) else []
        return {
            "source_product_id": product_id,
            "title": title,
            "brand": str(item.get("BRAND_NAME") or ""),
            "price": _won(item.get("PRDT_DC_PRICE")),
            "price_original": _won(item.get("NRMAL_AMT")),
            "discount_percent": _optional_int(item.get("DISCOUNT_RATE")),
            "image_url": str(item.get("PRDT_IMAGE_URL") or ""),
            "color": color,
            "style_code": str(item.get("STYLE_INFO") or ""),
            "link": f"{_PRODUCT_BASE}{product_id}",
            "site": "abcmart",
            "review_count": _optional_int(item.get("RVW_COUNT")) or 0,
            "category": category_path.split(">")[-1].strip() if category_path else "",
            "category_path": category_path,
            "in_stock": _in_stock(item.get("SOLD_OUT")),
            "options": {
                "colors": [color] if color else [],
                "sizes": sizes,
                "available_sizes": available_sizes,
            },
        }


def _won(value: Any) -> str:
    """숫자 또는 숫자 문자열을 쉼표가 포함된 원화 문자열로 변환한다."""

    if value in (None, ""):
        return ""
    return f"{int(value):,}원"


def _optional_int(value: Any) -> int | None:
    """비어 있을 수 있는 숫자를 정수로 변환한다."""

    if value in (None, ""):
        return None
    return int(value)


def _sizes(option_text: Any, size_list: Any) -> list[str]:
    """재고 수량이 확인되면 구매 가능한 사이즈만 표시 순서대로 반환한다.

    Args:
        option_text: 수량 필드가 없을 때 사용할 쉼표 구분 사이즈다.
        size_list: 사이즈별 재고 수량 객체다.

    Returns:
        양수 재고 사이즈 또는 수량을 알 수 없을 때의 표시 사이즈다.
    """

    if isinstance(size_list, dict):
        values = [str(size) for size, quantity in size_list.items() if _positive_int(quantity)]
    else:
        values = [part.strip() for part in str(option_text or "").split(",") if part.strip()]
    return list(dict.fromkeys(values))


def _positive_int(value: Any) -> bool:
    """재고 원본값이 0보다 큰 정수인지 안전하게 반환한다.

    Args:
        value: 문자열 또는 숫자 재고 수량이다.

    Returns:
        정수 변환이 가능하고 양수인 경우 참이다.
    """

    try:
        return int(value) > 0
    except (TypeError, ValueError):
        return False


def _in_stock(value: Any) -> bool | None:
    """ABC마트 SOLD_OUT 값을 판매 가능 여부로 변환한다.

    Args:
        value: ``y`` 또는 ``n``으로 예상되는 원본 값이다.

    Returns:
        명시된 값의 반대 재고 상태이며 알 수 없으면 ``None``이다.
    """

    normalized = str(value or "").strip().casefold()
    if normalized == "y":
        return False
    if normalized == "n":
        return True
    return None
