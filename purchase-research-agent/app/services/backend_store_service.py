"""현재 Product Backend의 수동 CollectorResult 적재 API를 호출한다."""

from __future__ import annotations

import os
from typing import Any

import httpx


class BackendStoreError(RuntimeError):
    """Product Backend 연결 또는 저장 실패를 호출 계층에 전달한다."""


class BackendStoreService:
    """검증된 CollectorResult를 Spring Boot Product Backend에 저장한다."""

    def __init__(self, base_url: str | None = None, timeout_seconds: float = 30.0):
        """환경 변수 또는 인자로 Product Backend 주소와 timeout을 설정한다.

        Args:
            base_url: Product Backend 기본 주소이며 기본값은 ``BACKEND_BASE_URL``이다.
            timeout_seconds: 저장 요청 timeout 초다.
        """

        self._base_url = (base_url or os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8080")).rstrip("/")
        self._timeout_seconds = timeout_seconds

    async def store(self, collector_result: dict[str, Any]) -> dict[str, Any]:
        """CollectorResult를 저장 API로 보내고 저장 개수 응답을 반환한다.

        Args:
            collector_result: 현재 Spring Boot ``CollectorResult`` 계약 객체다.

        Returns:
            상품, snapshot, 옵션과 근거 저장 개수다.

        Raises:
            BackendStoreError: 연결 실패, 오류 HTTP 상태 또는 잘못된 JSON 응답인 경우다.
        """

        return await self._request_json(
            "POST",
            "/internal/v1/collection-results",
            json=collector_result,
            operation="저장",
        )

    async def health(self) -> dict[str, Any]:
        """Product Backend health 응답을 반환한다.

        Returns:
            Spring Boot actuator health JSON이다.

        Raises:
            BackendStoreError: 연결 실패, 오류 HTTP 상태 또는 잘못된 JSON 응답인 경우다.
        """

        return await self._request_json("GET", "/actuator/health", operation="상태 확인")

    async def create_collection_task(self, request: dict[str, Any]) -> dict[str, Any]:
        """검색 수집 작업을 Product Backend와 RabbitMQ Queue에 등록한다.

        Args:
            request: Product Backend CollectionTaskRequest 계약 객체다.

        Returns:
            taskId와 jobId를 포함한 Queue 접수 응답이다.

        Raises:
            BackendStoreError: 연결, 작업 등록 또는 JSON 해석이 실패한 경우다.
        """

        return await self._request_json(
            "POST",
            "/internal/v1/collection-tasks",
            json=request,
            operation="수집 작업 등록",
        )

    async def get_collection_job(self, job_id: str) -> dict[str, Any]:
        """Product Backend에서 수집 job 진행 상태를 조회한다.

        Args:
            job_id: 작업 등록 응답에서 받은 job 식별자다.

        Returns:
            작업 수와 수집 결과를 포함한 현재 job 상태다.

        Raises:
            BackendStoreError: 연결, 조회 또는 JSON 해석이 실패한 경우다.
        """

        return await self._request_json(
            "GET",
            f"/internal/v1/collection-jobs/{job_id}",
            operation="수집 상태 조회",
        )

    async def search_products(
        self,
        *,
        merchant: str | None,
        query: str | None,
        limit: int,
    ) -> dict[str, Any]:
        """Product Backend에서 PostgreSQL 최신 상품을 조회한다.

        Args:
            merchant: 선택 판매처 식별자다.
            query: 상품명 또는 브랜드 검색어다.
            limit: 최대 반환 상품 수다.

        Returns:
            전체 개수와 최신 상품 목록이다.

        Raises:
            BackendStoreError: 연결, 조회 또는 JSON 해석이 실패한 경우다.
        """

        params: dict[str, str | int] = {"limit": limit}
        if merchant:
            params["merchant"] = merchant
        if query:
            params["query"] = query
        return await self._request_json(
            "GET",
            "/internal/v1/products",
            params=params,
            operation="저장 상품 조회",
        )

    async def _request_json(
        self,
        method: str,
        path: str,
        *,
        json: dict[str, Any] | None = None,
        params: dict[str, str | int] | None = None,
        operation: str,
    ) -> dict[str, Any]:
        """인증정보를 노출하지 않고 Product Backend JSON API를 호출한다.

        Args:
            method: HTTP method다.
            path: Product Backend 내부 API 경로다.
            json: 선택 JSON 요청 body다.
            params: 선택 query parameter다.
            operation: 안전한 오류 메시지에 사용할 작업 이름이다.

        Returns:
            JSON 객체 응답이다.

        Raises:
            BackendStoreError: 연결 실패, 오류 HTTP 상태 또는 JSON 객체가 아닌 응답인 경우다.
        """

        url = f"{self._base_url}{path}"
        headers: dict[str, str] = {"Accept": "application/json"}
        api_key = os.getenv("BACKEND_API_KEY")
        if api_key:
            headers["X-API-Key"] = api_key
        try:
            async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
                response = await client.request(
                    method,
                    url,
                    json=json,
                    params=params,
                    headers=headers,
                )
        except httpx.HTTPError as exc:
            raise BackendStoreError(f"Product Backend 연결 실패: {exc}") from exc
        if response.status_code >= 400:
            raise BackendStoreError(
                f"Product Backend {operation} 실패 HTTP {response.status_code}: {response.text[:1000]}"
            )
        try:
            payload = response.json()
        except ValueError as exc:
            raise BackendStoreError("Product Backend 응답이 JSON이 아닙니다") from exc
        if not isinstance(payload, dict):
            raise BackendStoreError("Product Backend 응답이 JSON 객체가 아닙니다")
        return payload
