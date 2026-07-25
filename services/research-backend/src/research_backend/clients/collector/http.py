"""Go Collector 내부 HTTP API를 호출하는 동기 client를 제공한다."""

import httpx
from pydantic import ValidationError

from research_backend.clients.collector.models import CollectorResult, SearchRequest


class CollectorClientError(RuntimeError):
    """Collector 연결, HTTP 상태 또는 응답 계약 검증 실패를 호출자에게 전달한다."""


class CollectorHttpClient:
    """검색 요청을 Collector에 보내고 v1 계약으로 검증한 결과를 반환한다.

    base URL과 timeout을 입력받아 CollectorResult를 출력한다. 연결 실패, 2xx가 아닌
    응답 또는 Pydantic 계약 불일치는 CollectorClientError로 변환한다.
    """

    def __init__(
        self,
        base_url: str,
        timeout_seconds: float = 20.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._owns_client = client is None
        self._client = client or httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout_seconds)

    def search(self, request: SearchRequest) -> CollectorResult:
        """검색 요청을 POST하고 응답 JSON을 검증해 반환한다.

        HTTP 연결·상태·JSON decoding·계약 검증 문제는 CollectorClientError로 반환하며
        원본 응답 body 전체는 오류 메시지에 포함하지 않는다.
        """

        try:
            response = self._client.post(
                "/internal/v1/collect/search",
                json=request.model_dump(by_alias=True, mode="json", exclude_none=True),
            )
            response.raise_for_status()
            return CollectorResult.model_validate(response.json())
        except (httpx.HTTPError, ValueError, ValidationError) as exc:
            raise CollectorClientError(f"Collector 검색 요청 또는 응답 검증에 실패했습니다: {exc}") from exc

    def close(self) -> None:
        """이 객체가 직접 만든 HTTP 연결 자원을 닫는다."""

        if self._owns_client:
            self._client.close()

    def __enter__(self) -> "CollectorHttpClient":
        """with 문에서 사용할 client 자신을 반환한다."""

        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        """with 문 종료 시 소유한 HTTP 연결 자원을 닫는다."""

        self.close()
