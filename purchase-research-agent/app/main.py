from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import manual_test, search, store
from app.messaging.processor import CollectionTaskProcessor
from app.messaging.rabbitmq import RabbitCollectionWorker
from app.services.crawler_service import SUPPORTED_SITES
from app.services.rate_limiter import create_rate_limiter_from_env

logger = logging.getLogger(__name__)


def _environment_flag(name: str) -> bool:
    """환경변수의 명시적인 true 값을 boolean으로 변환한다.

    Args:
        name: 확인할 환경변수 이름이다.

    Returns:
        값이 1, true, yes 또는 on이면 참이다.
    """

    return os.getenv(name, "").strip().lower() in {"1", "true", "yes", "on"}


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Swagger 통합 모드에서 Queue Worker를 시작하고 종료 시 안전하게 취소한다.

    Args:
        app: lifecycle 상태를 저장할 FastAPI application이다.

    Yields:
        HTTP 요청을 처리할 수 있는 application 실행 구간이다.
    """

    worker_task: asyncio.Task[None] | None = None
    enabled = _environment_flag("PYTHON_COLLECTION_WORKER_ENABLED")
    app.state.collection_worker_enabled = enabled
    app.state.collection_worker_task = None
    if enabled:
        rabbitmq_url = os.getenv("PURCHASE_RESEARCH_RABBITMQ_URL", "")
        processor = CollectionTaskProcessor(rate_limiter=create_rate_limiter_from_env())
        worker = RabbitCollectionWorker(rabbitmq_url, processor)
        worker_task = asyncio.create_task(worker.run(), name="python-collection-worker")
        app.state.collection_worker_task = worker_task
        logger.info("Swagger 통합 모드의 Python Queue Worker를 시작했습니다")
    try:
        yield
    finally:
        if worker_task is not None:
            worker_task.cancel()
            try:
                await worker_task
            except asyncio.CancelledError:
                pass


app = FastAPI(
    title="Purchase Research Agent",
    description=(
        "Python 상품 Collector입니다. 수동 검증은 `00 준비 확인`부터 `03 저장 상품 조회`까지 "
        "번호 순서대로 실행하세요."
    ),
    version="1.0.0",
    lifespan=lifespan,
    openapi_tags=[
        {"name": "00 준비 확인", "description": "Backend와 Python Worker 준비 상태 확인"},
        {"name": "01 작업 등록", "description": "실제 판매처 소량 수집 작업을 Queue에 등록"},
        {"name": "02 진행 조회", "description": "jobId로 수집 처리 상태 확인"},
        {"name": "03 결과 조회", "description": "PostgreSQL에 저장된 최신 상품 확인"},
        {"name": "직접 수집 및 저장", "description": "Queue를 우회하는 개발용 단일 요청"},
        {"name": "기존 검색", "description": "기존 Python 크롤러 API"},
        {"name": "Root", "description": "Python API 자체 상태"},
    ],
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(manual_test.router, prefix="/api/v1")
app.include_router(search.router, prefix="/api/v1", tags=["기존 검색"])
app.include_router(store.router, prefix="/api/v1", tags=["직접 수집 및 저장"])


@app.get("/", tags=["Root"])
async def root():
    """서비스 상태와 Swagger 경로 및 지원 판매처를 반환한다."""

    return {
        "status": "ok",
        "service": "Purchase Research Agent",
        "docs": "/docs",
        "supported_sites": SUPPORTED_SITES,
    }


@app.get("/health", tags=["Root"])
async def health():
    """프로세스가 HTTP 요청을 처리할 수 있는지 확인한다."""

    return {"status": "ok"}
