"""공통 JSON 예제가 Python Pydantic 계약과 일치하는지 검증한다."""

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from research_backend.clients.collector.models import CollectorResult, SearchRequest


def load_success_example() -> dict:
    """저장소의 Collector 성공 예제를 JSON 객체로 반환한다."""

    repository_root = Path(__file__).resolve().parents[4]
    example_path = repository_root / "contracts/collector/v1/examples/collector-result.success.json"
    return json.loads(example_path.read_text(encoding="utf-8"))


def test_success_example_matches_python_contract() -> None:
    """Go·Python 공통 성공 예제가 모든 Pydantic 필드를 통과해야 한다."""

    result = CollectorResult.model_validate(load_success_example())

    assert result.request_id == "research-20260714-001"
    assert result.products[0].options[0].size == "270"


def test_unknown_response_field_is_rejected() -> None:
    """계약에 없는 Collector 응답 필드는 조용히 무시하지 않아야 한다."""

    payload = load_success_example()
    payload["unexpected"] = True

    with pytest.raises(ValidationError):
        CollectorResult.model_validate(payload)


def test_search_request_requires_timezone() -> None:
    """timezone이 없는 요청 시각은 Collector 호출 전에 거부해야 한다."""

    with pytest.raises(ValidationError):
        SearchRequest(
            requestId="manual-test",
            merchant="abcmart",
            query="구두",
            requestedAt="2026-07-25T10:00:00",
        )
