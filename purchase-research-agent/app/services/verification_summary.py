"""상품별 JSON/HTML 검증 결과를 공통 CollectorResult 집계 형태로 변환한다."""

from __future__ import annotations

from typing import Any

_STATUS_FIELDS = {
    "MATCHED": "matched",
    "MISMATCH": "mismatched",
    "FAILED": "failed",
    "MISSING_IN_HTML": "missingInHtml",
    "MISSING_IN_JSON": "missingInJson",
    "PENDING": "pending",
}


def summarize_verifications(products: list[dict[str, Any]]) -> dict[str, int] | None:
    """검증 결과가 있는 상품을 Go Collector와 같은 상태별 구조로 집계한다.

    Args:
        products: 선택적으로 ``verification.status``를 포함하는 상품 목록이다.

    Returns:
        검증 상품 수와 상태별 개수이며 검증 결과가 하나도 없으면 ``None``이다.
    """

    summary = {
        "total": 0,
        "matched": 0,
        "mismatched": 0,
        "failed": 0,
        "missingInHtml": 0,
        "missingInJson": 0,
        "pending": 0,
    }
    for product in products:
        verification = product.get("verification")
        if not isinstance(verification, dict):
            continue
        status = str(verification.get("status") or "PENDING")
        field = _STATUS_FIELDS.get(status)
        if field is None:
            continue
        summary["total"] += 1
        summary[field] += 1

    return summary if summary["total"] > 0 else None
