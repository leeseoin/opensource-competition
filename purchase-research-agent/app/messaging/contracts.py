"""CollectionTask/CollectionResult Queue v1 계약을 Python 값으로 검증한다."""

from __future__ import annotations

import json
from dataclasses import dataclass, replace
from datetime import datetime
from pathlib import Path
from typing import Any

import jsonschema

from app.services.collector_result_validation import validate_collector_result

SCHEMA_VERSION = "1"
STATUS_SUCCESS = "success"
STATUS_PARTIAL = "partial"
STATUS_FAILED = "failed"

_REPO_ROOT = Path(__file__).resolve().parents[3]
_TASK_SCHEMA_PATH = _REPO_ROOT / "contracts" / "collection" / "v1" / "collection-task.schema.json"
_RESULT_SCHEMA_PATH = _REPO_ROOT / "contracts" / "collection" / "v1" / "collection-result.schema.json"
_task_validator: jsonschema.Draft202012Validator | None = None
_result_validator: jsonschema.Draft202012Validator | None = None


@dataclass(frozen=True)
class CollectionTask:
    """검증된 CollectionTask v1과 재시도 횟수를 보관한다."""

    schema_version: str
    task_id: str
    job_id: str
    merchant: str
    operation: str
    priority: int
    attempt: int
    max_attempts: int
    requested_at: str
    idempotency_key: str
    payload: dict[str, Any]

    def can_retry(self) -> bool:
        """최초 실행을 포함한 최대 시도 횟수 안에서 다시 실행할 수 있는지 반환한다."""

        return self.attempt + 1 < self.max_attempts

    def next_attempt(self) -> "CollectionTask":
        """현재 작업의 attempt만 하나 증가시킨 새 값을 반환한다."""

        return replace(self, attempt=self.attempt + 1)

    def to_dict(self) -> dict[str, Any]:
        """Spring Boot가 발행한 camelCase Queue JSON 구조로 변환한다."""

        return {
            "schemaVersion": self.schema_version,
            "taskId": self.task_id,
            "jobId": self.job_id,
            "merchant": self.merchant,
            "operation": self.operation,
            "priority": self.priority,
            "attempt": self.attempt,
            "maxAttempts": self.max_attempts,
            "requestedAt": self.requested_at,
            "idempotencyKey": self.idempotency_key,
            "payload": self.payload,
        }


def decode_collection_task(body: bytes) -> CollectionTask:
    """RabbitMQ body 하나를 엄격한 CollectionTask로 변환한다.

    Args:
        body: UTF-8 JSON 객체 한 개가 들어 있는 메시지 body다.

    Returns:
        Schema와 의미 검증을 통과한 작업이다.

    Raises:
        ValueError: JSON, Schema, 날짜 또는 attempt 의미가 올바르지 않은 경우다.
    """

    try:
        raw = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("CollectionTask JSON을 해석할 수 없습니다") from exc
    if not isinstance(raw, dict):
        raise ValueError("CollectionTask는 JSON 객체여야 합니다")
    issues = sorted(_task_schema_validator().iter_errors(raw), key=lambda issue: list(issue.path))
    if issues:
        raise ValueError(_schema_error("CollectionTask", issues))
    try:
        requested_at = datetime.fromisoformat(raw["requestedAt"].replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("requestedAt은 timezone을 포함한 ISO 8601 값이어야 합니다") from exc
    if requested_at.tzinfo is None:
        raise ValueError("requestedAt은 timezone을 포함한 ISO 8601 값이어야 합니다")
    if raw["attempt"] >= raw["maxAttempts"]:
        raise ValueError("attempt는 maxAttempts보다 작아야 합니다")
    return CollectionTask(
        schema_version=raw["schemaVersion"],
        task_id=raw["taskId"],
        job_id=raw["jobId"],
        merchant=raw["merchant"],
        operation=raw["operation"],
        priority=raw["priority"],
        attempt=raw["attempt"],
        max_attempts=raw["maxAttempts"],
        requested_at=raw["requestedAt"],
        idempotency_key=raw["idempotencyKey"],
        payload=raw["payload"],
    )


def validate_collection_result_envelope(envelope: dict[str, Any]) -> None:
    """Queue 결과 봉투와 내부 CollectorResult의 계약 및 상태 의미를 검사한다.

    Args:
        envelope: Python Worker가 발행하기 직전인 결과 객체다.

    Raises:
        ValueError: Queue 또는 CollectorResult 계약을 위반한 경우다.
    """

    issues = sorted(_result_schema_validator().iter_errors(envelope), key=lambda issue: list(issue.path))
    if issues:
        raise ValueError(_schema_error("CollectionResult", issues))
    status = envelope["status"]
    collector_result = envelope["collectorResult"]
    error = envelope["error"]
    if status in {STATUS_SUCCESS, STATUS_PARTIAL}:
        if not isinstance(collector_result, dict) or error is not None:
            raise ValueError("success/partial 결과에는 collectorResult만 필요합니다")
        validate_collector_result(collector_result)
        if collector_result["requestId"] != envelope["taskId"]:
            raise ValueError("taskId와 collectorResult.requestId가 일치해야 합니다")
    elif not isinstance(error, dict):
        raise ValueError("failed 결과에는 error가 필요합니다")


def _task_schema_validator() -> jsonschema.Draft202012Validator:
    """공유 CollectionTask Schema validator를 지연 생성해 반환한다."""

    global _task_validator
    if _task_validator is None:
        _task_validator = jsonschema.Draft202012Validator(_load_schema(_TASK_SCHEMA_PATH))
    return _task_validator


def _result_schema_validator() -> jsonschema.Draft202012Validator:
    """외부 CollectorResult 참조를 의미 검증으로 분리한 결과 validator를 반환한다."""

    global _result_validator
    if _result_validator is None:
        schema = _load_schema(_RESULT_SCHEMA_PATH)
        schema["properties"]["collectorResult"] = {"type": ["object", "null"]}
        schema["allOf"] = []
        _result_validator = jsonschema.Draft202012Validator(schema)
    return _result_validator


def _load_schema(path: Path) -> dict[str, Any]:
    """지정한 repository JSON Schema를 읽고 없거나 잘못됐으면 실패한다."""

    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Queue 계약 파일을 읽을 수 없습니다: {path}") from exc


def _schema_error(label: str, issues: list[jsonschema.ValidationError]) -> str:
    """여러 Schema 오류를 field 경로가 포함된 안전한 한 문장으로 합친다."""

    messages = [
        f"{'/'.join(str(part) for part in issue.path) or '<root>'}: {issue.message}"
        for issue in issues
    ]
    return f"{label} 계약 위반: " + "; ".join(messages)
