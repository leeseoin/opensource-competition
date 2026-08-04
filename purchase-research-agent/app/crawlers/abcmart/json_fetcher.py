"""ABC마트 공개 검색 JSON을 페이지 단위로 수집하고 원본 형태를 정규화한다."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

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
            RuntimeError: HTTP 실패 또는 필수 JSON 구조가 없는 경우다.
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
        if response.status_code != 200:
            raise RuntimeError(f"ABC마트 JSON HTTP {response.status_code}")
        try:
            payload = response.json()
        except ValueError as exc:
            raise RuntimeError("ABC마트 응답이 올바른 JSON이 아닙니다") from exc
        raw_items = payload.get("SEARCH")
        page_info = payload.get("PAGE")
        if not isinstance(raw_items, list) or not isinstance(page_info, dict):
            raise RuntimeError("ABC마트 JSON에서 SEARCH 또는 PAGE를 찾지 못했습니다")

        _JSON_DIR.mkdir(parents=True, exist_ok=True)
        output = _JSON_DIR / f"abcmart_{keyword}_{ts_file}_page{page}.json"
        output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

        final_page = int(page_info.get("finalPageNo") or page)
        return AbcJsonPage(
            products=[self._parse_item(item) for item in raw_items],
            total_count=int(payload.get("SEARCH_COUNT") or len(raw_items)),
            has_next=page < final_page,
            source_url=str(response.url),
        )

    def _parse_item(self, item: dict[str, Any]) -> dict[str, Any]:
        """ABC마트 JSON 상품 한 건을 기존 Python 크롤러 상품 구조로 변환한다.

        Raises:
            RuntimeError: 상품 ID 또는 이름이 없는 경우다.
        """

        product_id = str(item.get("PRDT_NO") or "")
        title = str(item.get("PRDT_NAME") or "")
        if not product_id or not title:
            raise RuntimeError("ABC마트 JSON 상품 ID 또는 이름이 비어 있습니다")
        color = str(item.get("COLOR_ID") or "")
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
            "options": {
                "colors": [color] if color else [],
                "sizes": _sizes(item.get("PRDT_OPTION"), item.get("SIZE_LIST")),
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
    """표시 순서를 유지하며 JSON 옵션 사이즈의 중복을 제거한다."""

    values = [part.strip() for part in str(option_text or "").split(",") if part.strip()]
    if isinstance(size_list, dict):
        values.extend(str(size) for size in size_list)
    return list(dict.fromkeys(values))
