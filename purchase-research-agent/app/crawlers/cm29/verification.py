"""29CM 검색 JSON과 상품 상세 HTML의 JSON-LD를 상품별로 전수 대조한다."""

from __future__ import annotations

import asyncio
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit, urlunsplit

import httpx
from bs4 import BeautifulSoup

_HTML_DIR = Path("output/raw_html/29cm")
_FIELDS = (
    "title",
    "brand",
    "price",
    "price_original",
    "discount_percent",
    "image_url",
    "link",
)


async def verify_products(
    client: httpx.AsyncClient,
    products: list[dict[str, Any]],
    *,
    json_source_url: str,
    ts_file: str,
) -> tuple[list[dict[str, Any]], list[str]]:
    """수집 대상 전체의 상세 HTML을 요청하고 JSON-LD 비교 결과를 붙인다.

    Args:
        client: 검색 요청과 연결을 공유하는 HTTP client다.
        products: 검색 JSON에서 선택한 모든 상품이다.
        json_source_url: 검색 JSON 응답 URL이다.
        ts_file: 원본 HTML 파일명에 사용할 실행 시각이다.

    Returns:
        상품별 검증 결과가 추가된 상품 목록과 검증 경고다.
    """

    warnings: list[str] = []
    _HTML_DIR.mkdir(parents=True, exist_ok=True)
    for index, product in enumerate(products):
        product_id = str(product.get("source_product_id") or "")
        html_source_url = str(product.get("link") or "")
        try:
            response = await client.get(html_source_url)
            response.raise_for_status()
            html_source_url = str(response.url)
            (_HTML_DIR / f"29cm_{product_id}_{ts_file}.html").write_text(
                response.text,
                encoding="utf-8",
            )
            html_product = parse_product_json_ld(response.text, html_source_url)
            product["verification"] = compare_product(
                product,
                html_product,
                json_source_url=json_source_url,
                html_source_url=html_source_url,
            )
            if product["verification"]["status"] != "MATCHED":
                fields = ",".join(
                    difference["field"]
                    for difference in product["verification"]["differences"]
                )
                warnings.append(f"29CM JSON/HTML 불일치 {product_id}: {fields}")
        except (httpx.HTTPError, ValueError, OSError) as exc:
            product["verification"] = failed_verification(
                json_source_url=json_source_url,
                html_source_url=html_source_url,
                reason=str(exc),
            )
            warnings.append(f"29CM 상세 HTML 검증 실패 {product_id}: {exc}")

        if index < len(products) - 1:
            await asyncio.sleep(1)
    return products, warnings


def parse_product_json_ld(html: str, source_url: str) -> dict[str, Any]:
    """29CM 상세 HTML의 Product JSON-LD를 공통 비교 필드로 변환한다.

    Args:
        html: 공개 상품 상세 HTML이다.
        source_url: redirect 후 최종 상세 URL이다.

    Returns:
        상품 ID, 이름, 브랜드, 가격, 이미지와 URL을 포함한 비교 상품이다.

    Raises:
        ValueError: Product JSON-LD가 없거나 필수 상품 ID가 없는 경우다.
    """

    soup = BeautifulSoup(html, "html.parser")
    for script in soup.select('script[type="application/ld+json"]'):
        try:
            payload = json.loads(script.string or script.get_text())
        except (TypeError, json.JSONDecodeError):
            continue
        if not isinstance(payload, dict) or payload.get("@type") != "Product":
            continue
        product_id = str(payload.get("sku") or "")
        if not product_id:
            raise ValueError("29CM Product JSON-LD의 sku가 비어 있습니다")
        offer = _first_mapping(payload.get("offers"))
        price_specification = _first_mapping(offer.get("priceSpecification"))
        price = _optional_number(offer.get("price"))
        original_price = _optional_number(price_specification.get("price"))
        return {
            "source_product_id": product_id,
            "title": str(payload.get("name") or ""),
            "brand": _brand_name(payload.get("brand")),
            "price": _format_won(price),
            "price_original": _format_won(original_price),
            "discount_percent": _discount_percent(price, original_price),
            "image_url": _first_image(payload.get("image")),
            "link": str(offer.get("url") or source_url),
        }
    raise ValueError("29CM 상세 HTML에서 Product JSON-LD를 찾지 못했습니다")


def compare_product(
    json_product: dict[str, Any],
    html_product: dict[str, Any],
    *,
    json_source_url: str,
    html_source_url: str,
) -> dict[str, Any]:
    """검색 JSON 상품과 상세 HTML 상품의 비교 결과를 만든다."""

    differences: list[dict[str, Any]] = []
    for field in _FIELDS:
        json_value = json_product.get(field)
        html_value = html_product.get(field)
        if not _equivalent(field, json_value, html_value, json_product):
            differences.append({
                "field": field,
                "json_value": _display(json_value),
                "html_value": _display(html_value),
            })
    return _verification(
        "MATCHED" if not differences else "MISMATCH",
        differences,
        json_source_url,
        html_source_url,
    )


def _equivalent(
    field: str,
    json_value: Any,
    html_value: Any,
    json_product: dict[str, Any],
) -> bool:
    """29CM 상세 HTML의 무할인 필드 생략을 반영해 의미상 일치를 판단한다."""

    if field == "price_original" and html_value in (None, ""):
        json_original = _normalized(field, json_value)
        return json_original in ("", _normalized("price", json_product.get("price")))
    if field == "discount_percent" and html_value in (None, ""):
        return _normalized(field, json_value) in (None, 0)
    return _normalized(field, json_value) == _normalized(field, html_value)


def failed_verification(
    *,
    json_source_url: str,
    html_source_url: str,
    reason: str,
) -> dict[str, Any]:
    """상세 HTML 요청이나 JSON-LD 파싱 실패 결과를 만든다."""

    return _verification(
        "FAILED",
        [{"field": "html", "json_value": None, "html_value": reason[:500]}],
        json_source_url,
        html_source_url,
    )


def _verification(
    status: str,
    differences: list[dict[str, Any]],
    json_source_url: str,
    html_source_url: str,
) -> dict[str, Any]:
    """Python Adapter와 공통 Contract가 읽을 검증 결과를 구성한다."""

    return {
        "status": status,
        "compared_fields": list(_FIELDS),
        "differences": differences,
        "json_source_url": json_source_url,
        "html_source_url": html_source_url,
        "verified_at": datetime.now(timezone.utc).isoformat(),
    }


def _normalized(field: str, value: Any) -> Any:
    """표시 형식이 다른 JSON과 HTML 비교값을 같은 형태로 정규화한다."""

    if field in {"price", "price_original"}:
        return re.sub(r"[^0-9]", "", str(value or ""))
    if field == "discount_percent":
        return int(value) if value not in (None, "") else None
    if field == "image_url":
        parsed = urlsplit(str(value or ""))
        return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))
    if field == "link":
        match = re.search(r"(?:catalog|products)/(\d+)", str(value or ""))
        return match.group(1) if match else str(value or "").strip()
    return " ".join(str(value or "").split()).casefold()


def _first_mapping(value: Any) -> dict[str, Any]:
    """단일 객체 또는 배열로 제공되는 JSON-LD 값을 첫 객체로 정규화한다."""

    if isinstance(value, dict):
        return value
    if isinstance(value, list) and value and isinstance(value[0], dict):
        return value[0]
    return {}


def _brand_name(value: Any) -> str:
    """JSON-LD 브랜드 객체 또는 문자열에서 브랜드명을 꺼낸다."""

    if isinstance(value, dict):
        return str(value.get("name") or "")
    return str(value or "")


def _first_image(value: Any) -> str:
    """JSON-LD 이미지 배열의 첫 공개 이미지 URL을 꺼낸다."""

    candidate = value[0] if isinstance(value, list) and value else value
    if isinstance(candidate, dict):
        return str(candidate.get("contentUrl") or candidate.get("url") or "")
    return str(candidate or "")


def _optional_number(value: Any) -> int | None:
    """JSON-LD의 선택 가격을 정수 원화 금액으로 변환한다."""

    if value in (None, ""):
        return None
    return int(float(value))


def _format_won(value: int | None) -> str:
    """정수 원화 금액을 Python 크롤러 가격 문자열로 변환한다."""

    return f"{value:,}원" if value is not None else ""


def _discount_percent(price: int | None, original_price: int | None) -> int | None:
    """현재가와 정상가가 있을 때 화면 비교용 할인율을 계산한다."""

    if price is None or original_price in (None, 0):
        return None
    return round((original_price - price) * 100 / original_price)


def _display(value: Any) -> str | None:
    """DB 차이 기록에 사용할 비교값을 문자열 또는 null로 제한한다."""

    return None if value is None else str(value)[:2000]
