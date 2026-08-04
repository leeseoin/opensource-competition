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

        url = f"{self._base_url}/internal/v1/collection-results"
        headers: dict[str, str] = {"Accept": "application/json"}
        api_key = os.getenv("BACKEND_API_KEY")
        if api_key:
            headers["X-API-Key"] = api_key
        try:
            async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
                response = await client.post(url, json=collector_result, headers=headers)
        except httpx.HTTPError as exc:
            raise BackendStoreError(f"Product Backend 연결 실패: {exc}") from exc
        if response.status_code >= 400:
            raise BackendStoreError(
                f"Product Backend 저장 실패 HTTP {response.status_code}: {response.text[:1000]}"
            )
        try:
            return response.json()
        except ValueError as exc:
            raise BackendStoreError("Product Backend 응답이 JSON이 아닙니다") from exc
