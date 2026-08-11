"""Python Collector의 RabbitMQ Queue v1 topology와 안전한 메시지 처리를 제공한다."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Literal, Protocol

import aio_pika
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractIncomingMessage, AbstractQueue

from app.messaging.contracts import (
    STATUS_FAILED,
    STATUS_RUNNING,
    CollectionTask,
    decode_collection_task,
    extract_collection_task_identity,
    validate_collection_result_envelope,
)

COLLECTION_EXCHANGE = "purchase-research.collection.v1"
DEAD_LETTER_EXCHANGE = "purchase-research.collection.dlx.v1"
SEARCH_TASK_QUEUE = "purchase-research.collection.search.v1"
SEARCH_RETRY_QUEUE = "purchase-research.collection.search.retry.v1"
SEARCH_DEAD_LETTER_QUEUE = "purchase-research.collection.search.dlq.v1"
RESULT_QUEUE = "purchase-research.collection.result.v1"
RESULT_DEAD_LETTER_QUEUE = "purchase-research.collection.result.dlq.v1"

SEARCH_ROUTING_KEY = "collection.search"
SEARCH_RETRY_KEY = "collection.search.retry"
SEARCH_DEAD_LETTER_KEY = "collection.search.dead"
RESULT_ROUTING_KEY = "collection.result"
RESULT_DEAD_LETTER_KEY = "collection.result.dead"


class TaskProcessor(Protocol):
    """Rabbit Worker가 사용하는 비동기 CollectionTask 처리 계약이다."""

    async def process(self, task: CollectionTask) -> dict:
        """검증된 작업을 Queue 결과 봉투로 변환한다."""


@dataclass(frozen=True)
class WorkerDecision:
    """메시지 발행이 성공한 뒤 수행할 ACK 또는 reject 결정을 표현한다."""

    action: Literal["ack", "reject"]
    routing_key: str | None = None
    body: bytes | None = None
    message_id: str | None = None


async def decide_message(body: bytes, processor: TaskProcessor) -> WorkerDecision:
    """메시지를 검증 및 실행하고 발행 대상과 최종 확인 처리를 결정한다.

    Args:
        body: RabbitMQ에서 받은 원본 JSON body다.
        processor: 판매처 검색을 실행할 비동기 처리기다.

    Returns:
        발행할 body와 routing key 및 발행 성공 뒤의 확인 처리다. 계약 위반 입력도 안전한
        taskId와 jobId를 복구할 수 있으면 실패 결과를 발행한 뒤 reject한다.
    """

    try:
        task = decode_collection_task(body)
    except ValueError:
        identity = extract_collection_task_identity(body)
        if identity is None:
            return WorkerDecision(action="reject")
        task_id, job_id = identity
        result = _invalid_task_result(task_id, job_id)
        return WorkerDecision(
            action="reject",
            routing_key=RESULT_ROUTING_KEY,
            body=_json_body(result),
            message_id=task_id,
        )

    result = await processor.process(task)
    error = result.get("error")
    if (
        result.get("status") == STATUS_FAILED
        and isinstance(error, dict)
        and error.get("retryable") is True
        and task.can_retry()
    ):
        next_task = task.next_attempt()
        return WorkerDecision(
            action="ack",
            routing_key=SEARCH_RETRY_KEY,
            body=_json_body(next_task.to_dict()),
            message_id=next_task.task_id,
        )

    return WorkerDecision(
        action="reject" if result.get("status") == STATUS_FAILED else "ack",
        routing_key=RESULT_ROUTING_KEY,
        body=_json_body(result),
        message_id=task.task_id,
    )


class RabbitCollectionWorker:
    """Queue v1 검색 작업을 소비하고 confirm 뒤 ACK/retry/DLQ를 수행한다."""

    def __init__(
        self,
        url: str,
        processor: TaskProcessor,
        logger: logging.Logger | None = None,
    ) -> None:
        """AMQP URL, 작업 처리기와 선택 logger를 보관한다.

        Args:
            url: RabbitMQ AMQP 연결 URL이다.
            processor: 검증된 검색 작업을 실행할 처리기다.
            logger: 작업 식별자와 상태만 기록할 logger다.

        Raises:
            ValueError: URL이 비었거나 processor가 없는 경우다.
        """

        if not url:
            raise ValueError("PURCHASE_RESEARCH_RABBITMQ_URL이 비어 있습니다")
        if processor is None:
            raise ValueError("CollectionTask Processor가 필요합니다")
        self._url = url
        self._processor = processor
        self._logger = logger or logging.getLogger(__name__)

    async def run(self, *, once: bool = False) -> None:
        """연결이 취소될 때까지 또는 작업 하나를 처리할 때까지 Worker를 실행한다.

        Args:
            once: 참이면 메시지 한 건을 최종 확인 처리한 뒤 종료한다.

        Raises:
            AMQPException: 연결, topology 또는 publisher confirm에 실패한 경우다.
        """

        connection = await aio_pika.connect_robust(self._url)
        async with connection:
            channel = await connection.channel(publisher_confirms=True, on_return_raises=True)
            await channel.set_qos(prefetch_count=1)
            exchange, search_queue = await declare_topology(channel)
            async with search_queue.iterator() as messages:
                async for message in messages:
                    await self._handle_message(exchange, message)
                    if once:
                        return

    async def _handle_message(
        self,
        exchange: AbstractExchange,
        message: AbstractIncomingMessage,
    ) -> None:
        """발행 confirm 성공 뒤에만 원본 메시지를 ACK 또는 reject한다.

        Args:
            exchange: retry 또는 결과를 발행할 Collection exchange다.
            message: 수동 확인 모드로 받은 검색 작업이다.

        Raises:
            AMQPException: 발행 confirm 실패 후 원본을 requeue한 경우다.
        """

        try:
            task = decode_collection_task(message.body)
        except ValueError:
            task = None

        if task is not None:
            try:
                await self._publish_result(
                    exchange,
                    _running_task_result(task),
                    task.task_id,
                )
            except Exception:
                await message.reject(requeue=True)
                raise

        decision = await decide_message(message.body, self._processor)
        if decision.routing_key is not None and decision.body is not None:
            try:
                await self._publish(
                    exchange,
                    decision.routing_key,
                    decision.body,
                    decision.message_id,
                )
            except Exception:
                await message.reject(requeue=True)
                raise

        if decision.action == "ack":
            await message.ack()
            self._logger.info("CollectionTask 처리 완료 message_id=%s", decision.message_id)
        else:
            await message.reject(requeue=False)
            self._logger.warning("CollectionTask를 DLQ로 이동했습니다 message_id=%s", decision.message_id)

    async def _publish_result(
        self,
        exchange: AbstractExchange,
        result: dict,
        message_id: str,
    ) -> None:
        """작업 상태 또는 최종 결과를 persistent 결과 메시지로 발행한다.

        Args:
            exchange: 결과 routing key가 연결된 Collection exchange다.
            result: CollectionResult v1 계약을 만족하는 상태 또는 결과다.
            message_id: 추적할 원본 작업 식별자다.
        """

        await self._publish(exchange, RESULT_ROUTING_KEY, _json_body(result), message_id)

    async def _publish(
        self,
        exchange: AbstractExchange,
        routing_key: str,
        body: bytes,
        message_id: str | None,
    ) -> None:
        """publisher confirm을 요구하는 persistent 메시지를 지정 경로에 발행한다.

        Args:
            exchange: 메시지를 전달할 Collection exchange다.
            routing_key: 결과 또는 retry routing key다.
            body: UTF-8 JSON body다.
            message_id: broker와 로그에서 추적할 작업 식별자다.
        """

        await exchange.publish(
            aio_pika.Message(
                body=body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                message_id=message_id,
            ),
            routing_key=routing_key,
            mandatory=True,
        )


async def declare_topology(
    channel: AbstractChannel,
) -> tuple[AbstractExchange, AbstractQueue]:
    """Go/Spring과 동일한 durable direct exchange, Queue와 binding을 멱등 선언한다.

    Args:
        channel: publisher confirm이 활성화된 aio-pika channel이다.

    Returns:
        결과 발행에 사용할 Collection exchange와 소비할 검색 Queue다.

    Raises:
        AMQPException: 기존 topology와 속성이 다르거나 broker 요청이 실패한 경우다.
    """

    exchange = await channel.declare_exchange(
        COLLECTION_EXCHANGE,
        aio_pika.ExchangeType.DIRECT,
        durable=True,
    )
    dead_exchange = await channel.declare_exchange(
        DEAD_LETTER_EXCHANGE,
        aio_pika.ExchangeType.DIRECT,
        durable=True,
    )
    search_queue = await channel.declare_queue(
        SEARCH_TASK_QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
            "x-dead-letter-routing-key": SEARCH_DEAD_LETTER_KEY,
            "x-max-priority": 100,
        },
    )
    retry_queue = await channel.declare_queue(
        SEARCH_RETRY_QUEUE,
        durable=True,
        arguments={
            "x-message-ttl": 5000,
            "x-dead-letter-exchange": COLLECTION_EXCHANGE,
            "x-dead-letter-routing-key": SEARCH_ROUTING_KEY,
        },
    )
    search_dead_queue = await channel.declare_queue(SEARCH_DEAD_LETTER_QUEUE, durable=True)
    result_queue = await channel.declare_queue(
        RESULT_QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
            "x-dead-letter-routing-key": RESULT_DEAD_LETTER_KEY,
        },
    )
    result_dead_queue = await channel.declare_queue(RESULT_DEAD_LETTER_QUEUE, durable=True)

    await search_queue.bind(exchange, SEARCH_ROUTING_KEY)
    await retry_queue.bind(exchange, SEARCH_RETRY_KEY)
    await search_dead_queue.bind(dead_exchange, SEARCH_DEAD_LETTER_KEY)
    await result_queue.bind(exchange, RESULT_ROUTING_KEY)
    await result_dead_queue.bind(dead_exchange, RESULT_DEAD_LETTER_KEY)
    return exchange, search_queue


def _json_body(value: dict) -> bytes:
    """Queue 객체를 UTF-8 compact JSON body로 직렬화한다.

    Args:
        value: JSON으로 표현 가능한 Queue 객체다.

    Returns:
        UTF-8로 인코딩된 compact JSON body다.
    """

    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def _invalid_task_result(task_id: str, job_id: str) -> dict:
    """식별 가능한 계약 위반 작업을 Spring이 종료할 수 있는 실패 결과로 만든다.

    Args:
        task_id: 공통 식별자 형식을 만족한 작업 식별자다.
        job_id: 공통 식별자 형식을 만족한 상위 job 식별자다.

    Returns:
        CollectionResult v1 계약을 만족하는 non-retryable 실패 결과다.
    """

    completed_at = datetime.now(timezone.utc).isoformat()
    result = {
        "schemaVersion": "1",
        "taskId": task_id,
        "jobId": job_id,
        "status": "failed",
        "startedAt": completed_at,
        "completedAt": completed_at,
        "durationMs": 0,
        "collectorResult": None,
        "error": {
            "code": "COLLECTION_TASK_CONTRACT_INVALID",
            "message": "CollectionTask 계약을 위반해 실행하지 않았습니다.",
            "retryable": False,
        },
    }
    validate_collection_result_envelope(result)
    return result


def _running_task_result(task: CollectionTask) -> dict:
    """검증된 작업을 Spring 상태 갱신용 running 결과로 만든다.

    Args:
        task: Worker가 RabbitMQ에서 소비한 유효한 CollectionTask다.

    Returns:
        완료 정보와 상품 결과가 없는 CollectionResult v1 running 상태다.
    """

    result = {
        "schemaVersion": "1",
        "taskId": task.task_id,
        "jobId": task.job_id,
        "status": STATUS_RUNNING,
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "completedAt": None,
        "durationMs": None,
        "collectorResult": None,
        "error": None,
    }
    validate_collection_result_envelope(result)
    return result
