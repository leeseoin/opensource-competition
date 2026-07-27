"""RabbitMQ Collection Queue 계약과 adapter를 제공한다."""

from research_backend.infrastructure.messaging.contracts import (
    CollectionResultEnvelope,
    CollectionTask,
    SearchPayload,
    TaskError,
    build_idempotency_key,
)
from research_backend.infrastructure.messaging.rabbitmq import (
    DEFAULT_RABBITMQ_URL,
    RabbitMQBroker,
)

__all__ = [
    "CollectionResultEnvelope",
    "CollectionTask",
    "DEFAULT_RABBITMQ_URL",
    "RabbitMQBroker",
    "SearchPayload",
    "TaskError",
    "build_idempotency_key",
]
