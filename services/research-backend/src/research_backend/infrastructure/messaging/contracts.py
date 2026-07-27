"""RabbitMQ CollectionTask와 CollectionResult v1 Pydantic 계약을 정의한다."""

import hashlib
import json
from datetime import datetime
from typing import Annotated, Literal

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    model_validator,
)

from research_backend.clients.collector.models import (
    CollectorResult,
    MerchantName,
    SearchFilters,
)

QueueIdentifier = Annotated[
    str,
    StringConstraints(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$"),
]
IdempotencyKey = Annotated[
    str,
    StringConstraints(pattern=r"^collection:v1:[a-f0-9]{64}$"),
]


class QueueModel(BaseModel):
    """Queue 계약의 JSON alias와 추가 필드 거부 규칙을 제공한다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class SearchPayload(QueueModel):
    """Go 판매처 Adapter가 검색 요청을 만들 때 사용할 조건을 표현한다."""

    query: str = Field(min_length=1, max_length=200)
    page: int = Field(default=1, ge=1)
    limit: int = Field(default=3, ge=1, le=50)
    locale: str = Field(default="ko-KR", pattern=r"^[a-z]{2}-[A-Z]{2}$")
    currency: str = Field(default="KRW", pattern=r"^[A-Z]{3}$")
    filters: SearchFilters = Field(default_factory=SearchFilters)

    @model_validator(mode="after")
    def require_supported_page(self) -> "SearchPayload":
        """현재 Adapter가 지원하지 않는 2페이지 이상 작업을 등록 전에 거부한다."""

        if self.page != 1:
            raise ValueError("현재 검색 Worker는 page=1만 지원합니다")
        return self


class CollectionTask(QueueModel):
    """Python Backend가 RabbitMQ 검색 Queue에 등록하는 작업 봉투다."""

    schema_version: Literal["1"] = Field(default="1", alias="schemaVersion")
    task_id: QueueIdentifier = Field(alias="taskId")
    job_id: QueueIdentifier = Field(alias="jobId")
    merchant: MerchantName
    operation: Literal["search"] = "search"
    priority: int = Field(default=20, ge=0, le=100)
    attempt: int = Field(default=0, ge=0)
    max_attempts: int = Field(default=2, alias="maxAttempts", ge=1, le=5)
    requested_at: AwareDatetime = Field(alias="requestedAt")
    idempotency_key: IdempotencyKey = Field(alias="idempotencyKey")
    payload: SearchPayload

    @model_validator(mode="after")
    def validate_attempt(self) -> "CollectionTask":
        """현재 attempt가 최대 시도 횟수보다 작도록 보장한다."""

        if self.attempt >= self.max_attempts:
            raise ValueError("attempt는 maxAttempts보다 작아야 합니다")
        return self


class TaskError(QueueModel):
    """작업 실패 코드, 안전한 설명과 재시도 가능 여부를 표현한다."""

    code: str = Field(min_length=1, max_length=100, pattern=r"^[A-Z][A-Z0-9_]*$")
    message: str = Field(min_length=1, max_length=1000)
    retryable: bool


class CollectionResultEnvelope(QueueModel):
    """Go Worker가 결과 Queue에 발행하는 작업 실행 결과를 표현한다."""

    schema_version: Literal["1"] = Field(alias="schemaVersion")
    task_id: QueueIdentifier = Field(alias="taskId")
    job_id: QueueIdentifier = Field(alias="jobId")
    status: Literal["success", "partial", "failed"]
    started_at: AwareDatetime = Field(alias="startedAt")
    completed_at: AwareDatetime = Field(alias="completedAt")
    duration_ms: int = Field(alias="durationMs", ge=0)
    collector_result: CollectorResult | None = Field(alias="collectorResult")
    error: TaskError | None

    @model_validator(mode="after")
    def validate_status_payload(self) -> "CollectionResultEnvelope":
        """성공에는 CollectorResult만, 실패에는 TaskError가 반드시 있도록 검사한다."""

        if self.completed_at < self.started_at:
            raise ValueError("completedAt은 startedAt보다 빠를 수 없습니다")
        if self.status in {"success", "partial"}:
            if self.collector_result is None or self.error is not None:
                raise ValueError("성공·부분 성공 결과에는 collectorResult만 필요합니다")
            if self.collector_result.status != self.status:
                raise ValueError("Queue status와 Collector status가 일치해야 합니다")
        elif self.error is None:
            raise ValueError("실패 결과에는 error가 필요합니다")
        return self


def build_idempotency_key(
    merchant: str,
    operation: str,
    payload: SearchPayload,
) -> str:
    """판매처·작업·검색 조건을 정렬한 SHA-256 멱등성 키로 변환한다.

    동일 입력은 동일 키를 반환하며 민감정보나 임의 URL을 포함하지 않는다. 실제 중복
    등록 차단은 Redis adapter가 구현될 때 이 키를 사용한다.
    """

    canonical = json.dumps(
        {
            "merchant": merchant,
            "operation": operation,
            "payload": payload.model_dump(mode="json", by_alias=True, exclude_none=True),
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return f"collection:v1:{digest}"


def now_with_timezone() -> datetime:
    """Queue CLI가 timezone이 포함된 현재 시각을 만들 수 있도록 반환한다."""

    return datetime.now().astimezone()
