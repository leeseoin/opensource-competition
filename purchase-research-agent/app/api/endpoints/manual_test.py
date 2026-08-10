"""Python Swagger에서 Queue 수집 전체 경로를 단계별로 검증하는 API다."""

from __future__ import annotations

import asyncio
import os
from typing import Any, Literal

from fastapi import APIRouter, HTTPException, Path, Query, Request
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.services.backend_store_service import BackendStoreError, BackendStoreService

router = APIRouter()


class ManualSearchFilters(BaseModel):
    """판매처가 확인할 수 있는 가격, 옵션과 재고 조건을 표현한다."""

    model_config = ConfigDict(populate_by_name=True)

    price_min: int | None = Field(default=None, alias="priceMin", ge=0)
    price_max: int | None = Field(default=None, alias="priceMax", ge=0)
    categories: list[str] = Field(default_factory=list, max_length=50)
    sizes: list[str] = Field(default_factory=list, max_length=50)
    colors: list[str] = Field(default_factory=list, max_length=50)
    in_stock_only: bool = Field(default=False, alias="inStockOnly")
    attributes: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_price_range(self) -> "ManualSearchFilters":
        """최소 가격이 최대 가격을 넘는 입력을 거부한다."""

        if self.price_min is not None and self.price_max is not None:
            if self.price_min > self.price_max:
                raise ValueError("priceMin은 priceMax보다 클 수 없습니다")
        return self


class ManualCollectionTaskRequest(BaseModel):
    """Swagger 단계 테스트에서 Queue에 등록할 소량 검색 조건이다."""

    model_config = ConfigDict(populate_by_name=True)

    merchant: Literal["abcmart", "29cm"] = "abcmart"
    query: str = Field(default="구두", min_length=1, max_length=200)
    page: int = Field(default=1, ge=1, le=200)
    limit: int = Field(default=3, ge=1, le=10)
    locale: Literal["ko-KR"] = "ko-KR"
    currency: Literal["KRW"] = "KRW"
    priority: int = Field(default=10, ge=0, le=100)
    max_attempts: int = Field(default=2, alias="maxAttempts", ge=1, le=5)
    filters: ManualSearchFilters = Field(default_factory=ManualSearchFilters)


def _worker_status(request: Request) -> dict[str, Any]:
    """FastAPI lifespan에서 시작한 내장 Worker의 현재 상태를 안전하게 반환한다.

    Args:
        request: application state에 접근할 FastAPI 요청이다.

    Returns:
        활성화 여부, 실행 여부와 사용자 확인용 설명이다.
    """

    enabled = bool(getattr(request.app.state, "collection_worker_enabled", False))
    task = getattr(request.app.state, "collection_worker_task", None)
    running = isinstance(task, asyncio.Task) and not task.done()
    if running:
        detail = "Python Queue Worker가 실행 중입니다."
    elif enabled:
        detail = "Python Queue Worker가 중지됐습니다. 서버 로그를 확인하세요."
    else:
        detail = "일반 API 모드입니다. Swagger 통합 실행 명령을 사용하면 Worker도 함께 시작됩니다."
    return {"enabled": enabled, "running": running, "detail": detail}


def _backend_error(exc: BackendStoreError) -> HTTPException:
    """Product Backend 호출 실패를 Swagger에서 이해할 수 있는 502 응답으로 변환한다.

    Args:
        exc: Backend client에서 발생한 안전한 오류다.

    Returns:
        다음 확인 항목을 포함하는 FastAPI 예외다.
    """

    return HTTPException(
        status_code=502,
        detail={
            "message": str(exc),
            "check": "0단계 준비 상태와 Product Backend 로그를 확인하세요.",
        },
    )


@router.get(
    "/manual-test/00-readiness",
    tags=["00 준비 확인"],
    summary="0단계 준비 상태 확인",
    description="Product Backend 연결과 Python Queue Worker 실행 여부를 확인합니다.",
)
async def readiness(request: Request) -> dict[str, Any]:
    """단계 테스트에 필요한 Backend와 내장 Worker 준비 상태를 반환한다.

    Args:
        request: application state를 포함한 FastAPI 요청이다.

    Returns:
        구성요소별 준비 상태와 다음 실행 단계다.
    """

    worker = _worker_status(request)
    try:
        backend_health = await BackendStoreService(timeout_seconds=3).health()
        backend = {
            "ready": backend_health.get("status") == "UP",
            "status": backend_health.get("status", "UNKNOWN"),
        }
    except BackendStoreError as exc:
        backend = {"ready": False, "status": "DOWN", "detail": str(exc)}
    ready = bool(backend["ready"] and worker["running"])
    return {
        "ready": ready,
        "pythonApi": {"ready": True},
        "productBackend": backend,
        "pythonQueueWorker": worker,
        "nextStep": (
            "1단계 수집 작업 등록을 실행하세요."
            if ready
            else "make python-crawler-swagger로 통합 실행한 뒤 다시 확인하세요."
        ),
    }


@router.post(
    "/manual-test/01-collection-tasks",
    tags=["01 작업 등록"],
    status_code=202,
    summary="1단계 소량 수집 작업 등록",
    description=(
        "기본값은 ABC마트 구두 3개이며 조건 필터는 비어 있습니다. 실제 판매처를 요청하므로 반복 실행을 피하고 "
        "응답의 jobId를 2단계에 입력하세요."
    ),
)
async def create_collection_task(request: ManualCollectionTaskRequest) -> dict[str, Any]:
    """Swagger 입력을 Product Backend 작업 등록 API로 전달한다.

    Args:
        request: 판매처, 검색어, 개수와 확인 필터다.

    Returns:
        Queue 접수 상태와 2단계에 넣을 jobId다.

    Raises:
        HTTPException: Product Backend 연결 또는 Queue 등록이 실패한 경우다.
    """

    try:
        result = await BackendStoreService().create_collection_task(
            request.model_dump(by_alias=True, exclude_none=True)
        )
    except BackendStoreError as exc:
        raise _backend_error(exc) from exc
    result["nextStep"] = "2단계 경로의 job_id에 이 응답의 jobId를 입력하세요."
    return result


@router.get(
    "/manual-test/02-collection-jobs/{job_id}",
    tags=["02 진행 조회"],
    summary="2단계 수집 진행 상태 조회",
    description="1단계 응답의 jobId를 입력합니다. SUCCESS/PARTIAL/FAILED가 될 때까지 확인할 수 있습니다.",
)
async def get_collection_job(
    job_id: str = Path(min_length=1, max_length=100, description="1단계 응답의 jobId"),
) -> dict[str, Any]:
    """jobId에 해당하는 Queue 작업 진행률과 수집 개수를 반환한다.

    Args:
        job_id: 1단계 작업 등록 응답의 jobId다.

    Returns:
        전체 상태, 페이지 작업, 상품 수와 검증 집계다.

    Raises:
        HTTPException: Product Backend 연결 또는 job 조회가 실패한 경우다.
    """

    try:
        result = await BackendStoreService().get_collection_job(job_id)
    except BackendStoreError as exc:
        raise _backend_error(exc) from exc
    if result.get("status") in {"SUCCESS", "PARTIAL"}:
        result["nextStep"] = "3단계 저장 상품 조회를 실행하세요."
    elif result.get("status") == "FAILED":
        result["nextStep"] = "tasks의 error를 확인한 뒤 1단계 조건을 조정하세요."
    else:
        result["nextStep"] = "잠시 뒤 이 단계를 다시 실행하세요."
    return result


@router.get(
    "/manual-test/03-products",
    tags=["03 결과 조회"],
    summary="3단계 PostgreSQL 저장 상품 조회",
    description="1단계에서 사용한 판매처와 검색어로 최종 저장 결과를 확인합니다.",
)
async def search_stored_products(
    merchant: Literal["abcmart", "29cm"] | None = Query(default="abcmart"),
    query: str | None = Query(default="구두", max_length=200),
    limit: int = Query(default=10, ge=1, le=100),
) -> dict[str, Any]:
    """Swagger에서 PostgreSQL에 저장된 최신 상품을 조회한다.

    Args:
        merchant: 선택 판매처다.
        query: 상품명 또는 브랜드 검색어다.
        limit: 최대 반환 상품 수다.

    Returns:
        Product Backend가 반환한 최신 상품 목록이다.

    Raises:
        HTTPException: Product Backend 연결 또는 상품 조회가 실패한 경우다.
    """

    try:
        return await BackendStoreService().search_products(
            merchant=merchant,
            query=query,
            limit=limit,
        )
    except BackendStoreError as exc:
        raise _backend_error(exc) from exc
