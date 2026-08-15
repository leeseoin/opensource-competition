"""검증된 Queue 검색 작업을 Python CrawlerService에 전달한다."""

from __future__ import annotations

import asyncio
import re
from datetime import datetime, timezone
from time import monotonic
from typing import Any, Protocol

from app.messaging.contracts import (
    SCHEMA_VERSION,
    STATUS_FAILED,
    STATUS_PARTIAL,
    STATUS_SUCCESS,
    CollectionTask,
    validate_collection_result_envelope,
)
from app.services.collector_result_adapter import build_collector_result
from app.services.collector_result_adapter import parse_won
from app.services.crawler_service import CrawlerService
from app.services.rate_limiter import MerchantRateLimiter, RateLimitTimeoutError


class CrawlerSearch(Protocol):
    """Processor가 사용하는 Python 검색 service의 최소 비동기 계약이다."""

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
        detail_limit: int = 0,
        option_limit: int = 0,
        page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """지정 판매처와 페이지의 상품 및 경고를 반환한다."""


class CollectionTaskProcessor:
    """CollectionTask를 제한 시간 안에 실행하고 결과 봉투로 변환한다."""

    def __init__(
        self,
        crawler: CrawlerSearch | None = None,
        timeout_seconds: float = 30.0,
        rate_limiter: MerchantRateLimiter | None = None,
    ):
        """검색 service, 양수 작업 timeout과 선택적 전역 rate limiter를 설정한다.

        Args:
            crawler: fixture에서 교체할 수 있는 검색 service다.
            timeout_seconds: 작업 하나의 최대 실행 초다.
            rate_limiter: 여러 Worker instance가 같은 판매처를 동시에 처리할 때
                process 경계를 넘어 요청 속도를 나눠 쓸 MerchantRateLimiter다.
                없으면 이 Worker 안의 prefetch=1만으로 속도를 제어한다(기존 동작과 동일).

        Raises:
            ValueError: timeout이 0 이하인 경우다.
        """

        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds는 0보다 커야 합니다")
        self._crawler = crawler or CrawlerService()
        self._timeout_seconds = timeout_seconds
        self._rate_limiter = rate_limiter

    async def process(self, task: CollectionTask) -> dict[str, Any]:
        """검색 작업을 실행하고 success/partial/failed Queue 결과를 반환한다.

        Args:
            task: 공유 Schema와 의미 검증을 통과한 검색 작업이다.

        Returns:
            CollectionResult v1 검증을 통과한 결과 봉투다.
        """

        started_at = datetime.now(timezone.utc)
        started_clock = monotonic()
        payload = task.payload
        try:
            _validate_supported_search(payload)
            if self._rate_limiter is not None:
                try:
                    await self._rate_limiter.acquire(task.merchant)
                except RateLimitTimeoutError:
                    return self._failed(
                        task,
                        started_at,
                        started_clock,
                        "RATE_LIMIT_TIMEOUT",
                        f"{task.merchant} 전역 rate limit 대기 시간을 초과했습니다",
                        True,
                    )
            products, warnings = await asyncio.wait_for(
                self._crawler.search_items(
                    payload["query"],
                    task.merchant,
                    max_items=50,
                    detail_limit=0,
                    option_limit=_option_enrichment_limit(payload),
                    page=payload["page"],
                    max_pages=1,
                ),
                timeout=self._timeout_seconds,
            )
            products = _apply_filters(products, payload["filters"])[:payload["limit"]]
            if not products and warnings:
                return self._failed(
                    task,
                    started_at,
                    started_clock,
                    "COLLECTOR_FAILED",
                    warnings[0],
                    _is_retryable_message(warnings[0]),
                )
            completed_at = datetime.now(timezone.utc)
            collector_result = build_collector_result(
                products,
                request_id=task.task_id,
                merchant=task.merchant,
                query=payload["query"],
                collected_at=completed_at.isoformat(),
                crawler_errors=warnings,
                filters=payload["filters"],
            )
            status = STATUS_PARTIAL if warnings else STATUS_SUCCESS
            envelope = _envelope(
                task,
                status,
                started_at,
                completed_at,
                started_clock,
                collector_result=collector_result,
                error=None,
            )
        except asyncio.TimeoutError:
            return self._failed(
                task,
                started_at,
                started_clock,
                "COLLECTOR_TIMEOUT",
                "Python Collector 작업 시간이 제한을 초과했습니다",
                True,
            )
        except ValueError as exc:
            return self._failed(task, started_at, started_clock, "INVALID_TASK", str(exc), False)
        except Exception as exc:
            return self._failed(
                task,
                started_at,
                started_clock,
                "COLLECTOR_UNAVAILABLE",
                f"Python Collector 실행 실패: {type(exc).__name__}",
                True,
            )
        validate_collection_result_envelope(envelope)
        return envelope

    def _failed(
        self,
        task: CollectionTask,
        started_at: datetime,
        started_clock: float,
        code: str,
        message: str,
        retryable: bool,
    ) -> dict[str, Any]:
        """식별정보를 제외한 오류와 실행 시간을 포함한 failed 결과를 만든다."""

        completed_at = datetime.now(timezone.utc)
        envelope = _envelope(
            task,
            STATUS_FAILED,
            started_at,
            completed_at,
            started_clock,
            collector_result=None,
            error={"code": code, "message": message[:1000], "retryable": retryable},
        )
        validate_collection_result_envelope(envelope)
        return envelope


def _envelope(
    task: CollectionTask,
    status: str,
    started_at: datetime,
    completed_at: datetime,
    started_clock: float,
    *,
    collector_result: dict[str, Any] | None,
    error: dict[str, Any] | None,
) -> dict[str, Any]:
    """공통 식별자와 단조 시계 duration을 Queue 결과 봉투로 조립한다."""

    return {
        "schemaVersion": SCHEMA_VERSION,
        "taskId": task.task_id,
        "jobId": task.job_id,
        "status": status,
        "startedAt": started_at.isoformat(),
        "completedAt": completed_at.isoformat(),
        "durationMs": max(int((monotonic() - started_clock) * 1000), 0),
        "collectorResult": collector_result,
        "error": error,
    }


def _is_retryable_message(message: str) -> bool:
    """판매처 일시 오류 문자열만 제한된 Queue retry 대상으로 분류한다."""

    normalized = message.lower()
    return any(token in normalized for token in ("timeout", "timed out", "429", "5xx", "connection", "연결"))


def _validate_supported_search(payload: dict[str, Any]) -> None:
    """Python 판매처 구현이 정확히 처리할 수 있는 지역, 통화와 필터 범위를 검사한다.

    Args:
        payload: 검증된 Queue 검색 payload다.

    Raises:
        ValueError: 현재 Python Collector가 의미를 보존할 수 없는 조건인 경우다.
    """

    if payload["locale"] != "ko-KR":
        raise ValueError("Python Collector는 locale=ko-KR만 지원합니다")
    if payload["currency"] != "KRW":
        raise ValueError("Python Collector는 currency=KRW만 지원합니다")
    if payload["filters"].get("attributes"):
        raise ValueError("Python Collector는 attributes 필터를 아직 지원하지 않습니다")


def _option_enrichment_limit(payload: dict[str, Any]) -> int:
    """Queue 검색에서 실제로 저장하거나 필터할 상품의 옵션 수집 상한을 정한다.

    Args:
        payload: 검증된 검색 payload다.

    Returns:
        일반 검색은 반환 상한, 옵션 필터가 있으면 검색 pool 전체 크기다.
    """
    filters = payload["filters"]
    return 50 if filters.get("sizes") or filters.get("colors") else payload["limit"]


def _apply_filters(products: list[dict[str, Any]], filters: dict[str, Any]) -> list[dict[str, Any]]:
    """판매처에서 확인된 원본 필드만 사용해 모든 Queue 필터를 AND 방식으로 적용한다.

    Args:
        products: Python 크롤러가 반환한 원본 상품이다.
        filters: Queue v1의 정규화된 가격, 분류, 옵션과 재고 조건이다.

    Returns:
        모든 조건을 만족하며 입력 순서를 유지한 상품 목록이다.
    """

    return [product for product in products if _matches_filters(product, filters)]


def _matches_filters(product: dict[str, Any], filters: dict[str, Any]) -> bool:
    """상품 한 건이 확인 가능한 Queue 필터를 모두 만족하는지 반환한다.

    Args:
        product: Python 판매처 원본 상품이다.
        filters: Queue v1 검색 필터다.

    Returns:
        서로 다른 필드는 모두 만족하고 같은 필드의 값은 하나 이상 일치하면 참이다.
    """

    amount = parse_won(product.get("price"))
    if "priceMin" in filters and (amount is None or amount < filters["priceMin"]):
        return False
    if "priceMax" in filters and (amount is None or amount > filters["priceMax"]):
        return False

    categories = _normalized_values(filters.get("categories"))
    if categories:
        product_categories = _category_values(product)
        if not _contains_requested(product_categories, categories):
            return False

    size_values, color_values = _option_filter_values(product)
    if not _contains_requested(_normalized_values(size_values), _normalized_values(filters.get("sizes"))):
        return False
    if not _contains_requested(_normalized_values(color_values), _normalized_values(filters.get("colors"))):
        return False
    if filters.get("inStockOnly") and product.get("in_stock") is not True:
        return False
    return True


def _option_filter_values(product: dict[str, Any]) -> tuple[list[Any], list[Any]]:
    """판매처별 raw option에서 필터에 사용할 사이즈와 색상만 분리한다.

    Args:
        product: Python 판매처 원본 상품이다.

    Returns:
        원본 순서를 유지한 사이즈 값과 색상 값이다.
    """
    raw_options = product.get("options")
    sizes: list[Any] = []
    colors: list[Any] = []
    if isinstance(raw_options, dict):
        sizes.extend(raw_options.get("available_sizes") or [])
        if product.get("in_stock") is True:
            colors.append(product.get("color"))
        raw_colors = raw_options.get("colors") or []
        if product.get("in_stock") is True and len(raw_colors) == 1:
            colors.extend(raw_colors)
        return sizes, colors
    if not isinstance(raw_options, list):
        if product.get("in_stock") is True:
            colors.append(product.get("color"))
        return sizes, colors
    for option in raw_options:
        if not isinstance(option, dict):
            continue
        if str(option.get("stock_status") or "unknown").casefold() != "available":
            continue
        name = str(option.get("name") or "").upper()
        value = str(option.get("value") or "").strip()
        if "SIZE" in name or "사이즈" in name:
            match = re.search(r"\bKR\s*(\d{2,4})\b", value, re.I)
            if match is None:
                match = re.search(r"\b(\d{2,4})\s*mm\b", value, re.I)
            if match is None and re.fullmatch(r"\d{2,4}", value):
                match = re.fullmatch(r"(\d{2,4})", value)
            sizes.append(match.group(1) if match else value)
        if "COLOR" in name or "색상" in name or ("SIZE" in name and "/" in value):
            color = re.split(r"\s*/\s*|:", value, maxsplit=1)[0]
            colors.append(re.sub(r"\s*\([^)]*\)\s*$", "", color).strip())
    return sizes, colors


def _normalized_values(raw: Any) -> list[str]:
    """단일값 또는 목록을 비교 가능한 소문자 공백 제거 문자열 목록으로 정규화한다.

    Args:
        raw: 단일값, 목록 또는 빈 값이다.

    Returns:
        빈 값을 제외하고 casefold한 문자열 목록이다.
    """

    values = raw if isinstance(raw, list) else [raw]
    return [str(value).strip().casefold() for value in values if value not in (None, "")]


def _category_values(product: dict[str, Any]) -> list[str]:
    """상품 category와 구분된 category path를 비교 가능한 목록으로 반환한다.

    Args:
        product: category 원본 필드를 가진 상품이다.

    Returns:
        각 category 단계가 정규화된 문자열 목록이다.
    """

    values = [product.get("category")]
    path = product.get("category_path")
    if path not in (None, ""):
        values.extend(part.strip() for part in str(path).split(">"))
    return _normalized_values(values)


def _contains_requested(available: list[str], requested: list[str]) -> bool:
    """요청값이 없거나 가용값 중 하나가 요청값 하나와 정확히 일치하면 참을 반환한다.

    Args:
        available: 상품에서 확인된 정규화 값이다.
        requested: 사용자가 허용한 정규화 값이다.

    Returns:
        조건이 없거나 두 목록의 교집합이 있으면 참이다.
    """

    if not requested:
        return True
    return any(expected == actual for expected in requested for actual in available)
