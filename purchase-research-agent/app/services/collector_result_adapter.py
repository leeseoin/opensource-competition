"""정우님 Python 크롤러 결과를 현재 Product Backend 저장 계약으로 변환한다."""

from __future__ import annotations

import re
from datetime import datetime
from typing import Any

from app.services.collector_result_validation import validate_collector_result
from app.services.verification_summary import summarize_verifications


def parse_won(value: str | int | float | None) -> int | None:
    """원화 문자열에서 정수 금액을 추출한다.

    Args:
        value: ``19,000원`` 문자열 또는 숫자 금액이다.

    Returns:
        변환한 원화 금액이며 값이 없으면 ``None``이다.
    """

    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        return int(value)
    digits = re.sub(r"[^0-9]", "", value)
    return int(digits) if digits else None


def build_collector_result(
    products: list[dict[str, Any]],
    *,
    request_id: str,
    merchant: str,
    query: str,
    collected_at: str,
    crawler_errors: list[str] | None = None,
    total_count: int | None = None,
) -> dict[str, Any]:
    """Python 크롤링 상품을 Spring Boot ``CollectorResult``로 변환한다.

    Args:
        products: 정우님 크롤러가 반환한 원본 상품 목록이다.
        request_id: 검색과 저장을 추적할 고유 요청 ID다.
        merchant: ``abcmart`` 또는 ``29cm`` 판매처 식별자다.
        query: 실제 크롤링에 사용한 검색어다.
        collected_at: ISO 8601 형식의 수집 완료 시각이다.
        crawler_errors: 부분 실패로 기록할 크롤러 오류 문자열이다.
        total_count: 여러 저장 batch가 공유할 전체 수집 상품 수다.

    Returns:
        Product Backend의 ``POST /internal/v1/collection-results`` 입력 객체다.

    Raises:
        ValueError: 필수 식별자 또는 상품 필드가 비어 있는 경우다.
    """

    if not request_id or not merchant or not query:
        raise ValueError("request_id, merchant, query는 필수입니다")

    issues = crawler_errors or []
    status = "partial" if issues else "success"
    result = {
        "requestId": request_id,
        "operation": "search",
        "status": status,
        "merchant": merchant,
        "query": query,
        "filters": {},
        "totalCount": total_count if total_count is not None else len(products),
        "hasNext": None,
        "collectedAt": collected_at,
        "collectorVersion": "dev-jw-python-bridge-v1",
        "products": [
            _convert_product(product, collected_at)
            for product in products
        ],
        "warnings": [_issue(message) for message in issues],
        "errors": [],
    }
    verification_summary = summarize_verifications(products)
    if verification_summary is not None:
        result["verificationSummary"] = verification_summary
    validate_collector_result(result)
    return result


def build_collector_result_batches(
    products: list[dict[str, Any]],
    *,
    request_id_prefix: str,
    merchant: str,
    query: str,
    collected_at: str,
    crawler_errors: list[str] | None = None,
    batch_size: int = 50,
) -> list[dict[str, Any]]:
    """대량 Python 결과를 Spring Boot 상한에 맞는 CollectorResult 목록으로 나눈다.

    Args:
        products: 한 번의 검색에서 수집한 전체 상품 목록이다.
        request_id_prefix: 각 batch 요청 ID 앞에 붙일 실행 식별자다.
        merchant: 판매처 식별자다.
        query: 실제 검색어다.
        collected_at: 전체 수집 완료 시각이다.
        crawler_errors: 첫 batch에 기록할 크롤러 경고다.
        batch_size: 한 CollectorResult에 포함할 상품 수이며 최대 50이다.

    Returns:
        Product Backend에 순서대로 저장할 CollectorResult 목록이다.

    Raises:
        ValueError: batch 크기가 1에서 50 범위를 벗어난 경우다.
    """

    if not 1 <= batch_size <= 50:
        raise ValueError("batch_size는 1 이상 50 이하여야 합니다")
    total_count = len(products)
    batches: list[dict[str, Any]] = []
    for index, start in enumerate(range(0, total_count, batch_size), start=1):
        batches.append(build_collector_result(
            products[start:start + batch_size],
            request_id=f"{request_id_prefix}-b{index:03d}",
            merchant=merchant,
            query=query,
            collected_at=collected_at,
            crawler_errors=crawler_errors if index == 1 else None,
            total_count=total_count,
        ))
    return batches


def _convert_product(product: dict[str, Any], collected_at: str) -> dict[str, Any]:
    """Python 원본 상품 한 건을 저장 가능한 공통 상품으로 변환한다.

    Raises:
        ValueError: 상품 ID, 이름 또는 출처 URL이 없는 경우다.
    """

    external_id = str(product.get("source_product_id") or "")
    name = str(product.get("title") or "")
    source_url = str(product.get("link") or "")
    if not external_id or not name or not source_url:
        raise ValueError("상품에는 source_product_id, title, link가 필요합니다")

    provenance = _provenance(source_url, collected_at)
    amount = parse_won(product.get("price"))
    image_url = str(product.get("image_url") or "")
    converted = {
        "externalId": external_id,
        "name": name,
        "brand": str(product.get("brand") or ""),
        "categoryPath": [],
        "productUrl": source_url,
        "imageUrls": [image_url] if image_url else [],
        "price": _money(amount),
        "shipping": {
            "fee": None,
            "summary": None,
            "provenance": provenance,
        },
        "stockStatus": "unknown",
        "rating": None,
        "reviewCount": _optional_int(product.get("review_count")),
        "options": _convert_options(product, external_id, amount, provenance),
        "measurements": {},
        "reviews": _convert_reviews(product.get("reviews"), provenance),
        "provenance": provenance,
    }
    verification = _convert_verification(product.get("verification"))
    if verification is not None:
        converted["verification"] = verification
    return converted


def _convert_verification(raw: Any) -> dict[str, Any] | None:
    """Python snake case 검증 결과를 Spring Boot camel case 계약으로 변환한다."""

    if not isinstance(raw, dict):
        return None
    differences = raw.get("differences") if isinstance(raw.get("differences"), list) else []
    return {
        "status": raw.get("status") or "PENDING",
        "comparedFields": _unique_strings(raw.get("compared_fields")),
        "differences": [
            {
                "field": str(difference.get("field") or "unknown")[:100],
                "jsonValue": _optional_string(difference.get("json_value")),
                "htmlValue": _optional_string(difference.get("html_value")),
            }
            for difference in differences
            if isinstance(difference, dict)
        ],
        "jsonSourceUrl": raw.get("json_source_url"),
        "htmlSourceUrl": raw.get("html_source_url"),
        "verifiedAt": raw.get("verified_at"),
    }


def _convert_options(
    product: dict[str, Any],
    external_id: str,
    amount: int | None,
    provenance: dict[str, Any],
) -> list[dict[str, Any]]:
    """연결 관계가 없는 크롤러 옵션 목록을 과장하지 않고 개별 선택값으로 변환한다."""

    raw_options = product.get("options")
    if not isinstance(raw_options, dict):
        return []
    sizes = _unique_strings(raw_options.get("sizes"))
    colors = _unique_strings(raw_options.get("colors"))
    options: list[dict[str, Any]] = []
    for size in sizes:
        options.append(_option(f"{external_id}-size-{size}", size, size, None, amount, provenance))
    if not sizes:
        for color in colors:
            options.append(_option(f"{external_id}-color-{color}", color, None, color, amount, provenance))
    return options


def _option(
    external_id: str,
    label: str,
    size: str | None,
    color: str | None,
    amount: int | None,
    provenance: dict[str, Any],
) -> dict[str, Any]:
    """상품 옵션 한 건을 Product Backend 계약 형태로 만든다."""

    return {
        "externalId": external_id[:200],
        "label": label[:300],
        "size": size,
        "color": color,
        "stockStatus": "unknown",
        "price": _money(amount),
        "provenance": provenance,
    }


def _convert_reviews(raw_reviews: Any, provenance: dict[str, Any]) -> list[dict[str, Any]]:
    """작성자 식별정보 없이 공개 리뷰를 Product Backend 계약으로 변환한다."""

    if not isinstance(raw_reviews, list):
        return []
    reviews: list[dict[str, Any]] = []
    for review in raw_reviews:
        if not isinstance(review, dict):
            continue
        reviews.append({
            "externalId": _optional_string(review.get("review_source_id")),
            "rating": review.get("score"),
            "text": str(review.get("content") or ""),
            "hasImage": False,
            "purchasedOption": _optional_string(review.get("size")),
            "createdAt": _review_datetime(review.get("date")),
            "provenance": provenance,
        })
    return reviews


def _review_datetime(value: Any) -> str | None:
    """날짜만 있는 리뷰 작성일을 한국 표준시 자정의 ISO 8601 값으로 변환한다."""

    if not isinstance(value, str) or not value:
        return None
    try:
        datetime.strptime(value[:10], "%Y-%m-%d")
    except ValueError:
        return None
    return f"{value[:10]}T00:00:00+09:00"


def _provenance(source_url: str, collected_at: str) -> dict[str, Any]:
    """상품 사실의 공개 출처와 수집 버전을 공통 구조로 만든다."""

    return {
        "sourceUrl": source_url,
        "collectedAt": collected_at,
        "collectorVersion": "dev-jw-python-bridge-v1",
    }


def _money(amount: int | None) -> dict[str, Any] | None:
    """금액이 있을 때만 KRW Money 객체를 만든다."""

    return {"amount": amount, "currency": "KRW"} if amount is not None else None


def _issue(message: str) -> dict[str, Any]:
    """크롤러 문자열 오류를 저장 가능한 경고 객체로 변환한다."""

    return {
        "code": "PYTHON_CRAWLER_WARNING",
        "message": message[:1000],
        "retryable": False,
        "sourceUrl": None,
    }


def _optional_int(value: Any) -> int | None:
    """선택 숫자를 정수로 변환하고 변환할 수 없으면 ``None``을 반환한다."""

    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _optional_string(value: Any) -> str | None:
    """비어 있지 않은 선택 문자열만 반환한다."""

    text = str(value or "")
    return text if text else None


def _unique_strings(value: Any) -> list[str]:
    """원본 순서를 유지하며 중복과 빈 값을 제거한 문자열 목록을 만든다."""

    if not isinstance(value, list):
        return []
    return list(dict.fromkeys(str(item) for item in value if item))
