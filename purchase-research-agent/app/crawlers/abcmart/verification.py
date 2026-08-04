"""ABC마트 JSON 상품과 렌더링 HTML 상품을 전수 대조한다."""

from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Any
from urllib.parse import urlsplit, urlunsplit

_FIELDS = (
    "title",
    "brand",
    "price",
    "price_original",
    "discount_percent",
    "image_url",
    "link",
)


def reconcile_page(
    json_products: list[dict[str, Any]],
    html_products: list[dict[str, Any]],
    *,
    json_source_url: str,
    html_source_url: str,
) -> tuple[list[dict[str, Any]], list[str]]:
    """JSON 상품 전체에 HTML 비교 결과를 붙이고 누락/불일치 경고를 반환한다.

    Args:
        json_products: 기본 저장값으로 사용할 JSON 상품 목록이다.
        html_products: 같은 검색 페이지에서 파싱한 HTML 상품 목록이다.
        json_source_url: JSON 검색 응답 URL이다.
        html_source_url: 렌더링한 검색 화면 URL이다.

    Returns:
        검증 상태가 추가된 JSON 상품과 페이지 단위 경고 목록이다.
    """

    html_by_id = {str(item.get("source_product_id") or ""): item for item in html_products}
    json_ids = {str(item.get("source_product_id") or "") for item in json_products}
    warnings: list[str] = []
    verified_at = datetime.now(timezone.utc).isoformat()

    for product in json_products:
        product_id = str(product.get("source_product_id") or "")
        html_product = html_by_id.get(product_id)
        if html_product is None:
            product["verification"] = _verification(
                "MISSING_IN_HTML", [], json_source_url, html_source_url, verified_at,
            )
            warnings.append(f"HTML에 상품이 없음: {product_id}")
            continue

        differences = _differences(product, html_product)
        status = "MATCHED" if not differences else "MISMATCH"
        product["verification"] = _verification(
            status, differences, json_source_url, html_source_url, verified_at,
        )
        if differences:
            fields = ",".join(diff["field"] for diff in differences)
            warnings.append(f"JSON/HTML 불일치 {product_id}: {fields}")

    missing_json = sorted(product_id for product_id in html_by_id if product_id not in json_ids)
    warnings.extend(f"JSON에 상품이 없음: {product_id}" for product_id in missing_json)
    return json_products, warnings


def mark_failed(
    products: list[dict[str, Any]],
    *,
    json_source_url: str,
    html_source_url: str,
    reason: str,
) -> tuple[list[dict[str, Any]], list[str]]:
    """HTML 페이지 검증 실패를 페이지의 모든 JSON 상품에 명시한다."""

    verified_at = datetime.now(timezone.utc).isoformat()
    for product in products:
        product["verification"] = _verification(
            "FAILED",
            [{"field": "html", "json_value": None, "html_value": reason[:500]}],
            json_source_url,
            html_source_url,
            verified_at,
        )
    return products, [f"HTML 전수 검증 실패: {reason}"]


def verification_summary(products: list[dict[str, Any]]) -> dict[str, int]:
    """상품 목록의 검증 상태별 개수를 계산한다."""

    summary: dict[str, int] = {}
    for product in products:
        status = str((product.get("verification") or {}).get("status") or "PENDING")
        summary[status] = summary.get(status, 0) + 1
    return summary


def _differences(json_product: dict[str, Any], html_product: dict[str, Any]) -> list[dict[str, Any]]:
    """비교 대상 필드의 정규화된 값이 다른 경우 차이 목록을 만든다."""

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
    return differences


def _equivalent(
    field: str,
    json_value: Any,
    html_value: Any,
    json_product: dict[str, Any],
) -> bool:
    """HTML 생략 표현을 반영해 두 수집값이 의미상 같은지 판단한다."""

    if field == "price_original" and html_value in (None, ""):
        json_original = _normalized(field, json_value)
        return json_original in ("", _normalized("price", json_product.get("price")))
    if field == "discount_percent" and html_value in (None, ""):
        return _normalized(field, json_value) in (None, 0)
    return _normalized(field, json_value) == _normalized(field, html_value)


def _normalized(field: str, value: Any) -> Any:
    """표시 형식 차이로 인한 오탐을 줄이도록 비교값을 정규화한다."""

    if field in {"price", "price_original"}:
        return re.sub(r"[^0-9]", "", str(value or ""))
    if field == "discount_percent":
        return int(value) if value not in (None, "") else None
    if field == "image_url":
        parsed = urlsplit(str(value or ""))
        return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))
    if field == "link":
        match = re.search(r"prdtNo=(\d+)", str(value or ""))
        return match.group(1) if match else str(value or "").strip()
    return " ".join(str(value or "").split()).casefold()


def _display(value: Any) -> str | None:
    """DB와 Contract에 기록할 비교값을 문자열 또는 null로 제한한다."""

    if value is None:
        return None
    return str(value)[:2000]


def _verification(
    status: str,
    differences: list[dict[str, Any]],
    json_source_url: str,
    html_source_url: str,
    verified_at: str,
) -> dict[str, Any]:
    """상품별 JSON/HTML 검증 결과 구조를 만든다."""

    return {
        "status": status,
        "compared_fields": list(_FIELDS),
        "differences": differences,
        "json_source_url": json_source_url,
        "html_source_url": html_source_url,
        "verified_at": verified_at,
    }
