"""Go Collector HTTP client의 요청 직렬화와 응답 검증을 테스트한다."""

import json
from datetime import datetime
from pathlib import Path

import httpx

from research_backend.clients.collector.http import CollectorHttpClient
from research_backend.clients.collector.models import SearchRequest


def test_search_posts_request_and_validates_response() -> None:
    """검색 요청은 정해진 경로로 전송되고 검증된 결과로 반환되어야 한다."""

    repository_root = Path(__file__).resolve().parents[5]
    payload = json.loads(
        (repository_root / "contracts/collector/v1/examples/collector-result.success.json").read_text(
            encoding="utf-8"
        )
    )

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/v1/collect/search"
        body = json.loads(request.content)
        assert body["merchant"] == "abcmart"
        assert body["query"] == "구두"
        return httpx.Response(200, json=payload)

    http_client = httpx.Client(
        base_url="http://collector.test",
        transport=httpx.MockTransport(handler),
    )
    collector = CollectorHttpClient("http://collector.test", client=http_client)
    result = collector.search(
        SearchRequest(
            requestId="manual-test",
            merchant="abcmart",
            query="구두",
            requestedAt=datetime.now().astimezone(),
        )
    )

    assert result.status == "success"
    assert len(result.products) == 1
    http_client.close()
