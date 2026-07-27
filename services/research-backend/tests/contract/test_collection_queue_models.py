"""Collection Queue v1 예제와 Pydantic 모델의 호환성을 검증한다."""

import json
from datetime import datetime
from pathlib import Path

from research_backend.clients.collector.models import CollectorResult
from research_backend.infrastructure.messaging.contracts import (
    CollectionResultEnvelope,
    CollectionTask,
    SearchPayload,
    build_idempotency_key,
)

REPOSITORY_ROOT = Path(__file__).resolve().parents[4]


def load_json(path: str) -> dict:
    """저장소 루트 기준 JSON fixture를 dict로 읽는다."""

    return json.loads((REPOSITORY_ROOT / path).read_text(encoding="utf-8"))


def test_collection_task_example_matches_python_contract() -> None:
    """공통 검색 작업 예제가 Pydantic CollectionTask를 통과해야 한다."""

    task = CollectionTask.model_validate(
        load_json("contracts/collection/v1/examples/collection-task.search.json")
    )

    assert task.merchant == "abcmart"
    assert task.payload.page == 1
    assert task.max_attempts == 2


def test_failed_result_example_matches_python_contract() -> None:
    """공통 실패 결과 예제가 Pydantic CollectionResultEnvelope를 통과해야 한다."""

    result = CollectionResultEnvelope.model_validate(
        load_json("contracts/collection/v1/examples/collection-result.failed.json")
    )

    assert result.status == "failed"
    assert result.error is not None
    assert result.error.retryable is True


def test_success_result_example_matches_python_contract() -> None:
    """공통 성공 결과 예제가 Pydantic CollectionResultEnvelope를 통과해야 한다."""

    result = CollectionResultEnvelope.model_validate(
        load_json("contracts/collection/v1/examples/collection-result.success.json")
    )

    assert result.status == "success"
    assert result.collector_result is not None


def test_successful_collector_result_can_be_wrapped() -> None:
    """기존 CollectorResult 성공 예제가 Queue 성공 봉투에 포함될 수 있어야 한다."""

    collector_result = CollectorResult.model_validate(
        load_json("contracts/collector/v1/examples/collector-result.success.json")
    )
    envelope = CollectionResultEnvelope(
        schemaVersion="1",
        taskId="task-success-001",
        jobId="job-success-001",
        status="success",
        startedAt=datetime.fromisoformat("2026-07-26T16:00:00+09:00"),
        completedAt=datetime.fromisoformat("2026-07-26T16:00:01+09:00"),
        durationMs=1000,
        collectorResult=collector_result,
        error=None,
    )

    assert envelope.collector_result == collector_result


def test_idempotency_key_is_stable_for_same_search() -> None:
    """같은 판매처와 검색 조건은 동일한 SHA-256 멱등성 키를 만들어야 한다."""

    first = SearchPayload(query="구두", limit=3)
    second = SearchPayload(query="구두", limit=3)
    changed = SearchPayload(query="가방", limit=3)

    assert build_idempotency_key("abcmart", "search", first) == build_idempotency_key(
        "abcmart", "search", second
    )
    assert build_idempotency_key("abcmart", "search", first) != build_idempotency_key(
        "abcmart", "search", changed
    )
