"""Python 크롤링 결과를 현재 Product Backend에 바로 저장하는 수동 검증 API다."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal
from uuid import uuid4

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field, model_validator

from app.services.backend_store_service import BackendStoreError, BackendStoreService
from app.services.collector_result_adapter import build_collector_result_batches
from app.services.crawler_service import CrawlerService
from app.crawlers.abcmart.verification import verification_summary

router = APIRouter()


class CollectAndStoreRequest(BaseModel):
    """Swagger에서 입력할 Python 크롤링 및 DB 적재 요청이다."""

    merchant: Literal["abcmart", "29cm"]
    query: str = Field(min_length=1, max_length=200)
    limit: int = Field(default=3, ge=1, le=500)
    detail_limit: int = Field(default=0, ge=0, le=50)

    @model_validator(mode="after")
    def validate_detail_limit(self) -> "CollectAndStoreRequest":
        """상세 수집 개수가 전체 상품 상한을 넘지 않는지 검증한다."""

        if self.detail_limit > self.limit:
            raise ValueError("detail_limit은 limit보다 클 수 없습니다")
        return self


@router.post("/collect-and-store")
async def collect_and_store(request: CollectAndStoreRequest) -> dict:
    """상품을 Python으로 수집하고 현재 Spring Boot를 통해 PostgreSQL에 저장한다.

    Args:
        request: 판매처, 검색어, 상품 상한과 상세 수집 상한이다.

    Returns:
        요청 ID, 수집 개수와 Product Backend 저장 개수다.

    Raises:
        HTTPException: 상품을 하나도 수집하지 못했거나 Backend 저장이 실패한 경우다.
    """

    crawler = CrawlerService()
    products, errors = await crawler.search_items(
        request.query,
        request.merchant,
        max_items=request.limit,
        detail_limit=request.detail_limit,
    )
    if not products:
        raise HTTPException(
            status_code=502,
            detail={"message": "수집된 상품이 없어 저장하지 않았습니다", "errors": errors},
        )

    request_id = f"python-manual-{uuid4()}"
    collected_at = datetime.now(timezone.utc).isoformat()
    try:
        collector_results = build_collector_result_batches(
            products,
            request_id_prefix=request_id,
            merchant=request.merchant,
            query=request.query,
            collected_at=collected_at,
            crawler_errors=errors,
        )
        backend = BackendStoreService()
        store_result = {
            "productCount": 0,
            "snapshotCount": 0,
            "optionCount": 0,
            "evidenceCount": 0,
            "verificationCount": 0,
        }
        for batch in collector_results:
            batch_result = await backend.store(batch)
            for key in store_result:
                store_result[key] += int(batch_result.get(key, 0))
    except (ValueError, BackendStoreError) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return {
        "requestId": request_id,
        "status": "STORED",
        "merchant": request.merchant,
        "query": request.query,
        "collectedCount": len(products),
        "batchCount": len(collector_results),
        "verificationSummary": verification_summary(products),
        "crawlerWarnings": errors,
        "storeResult": store_result,
    }
