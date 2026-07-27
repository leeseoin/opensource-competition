"""Pika BlockingConnection 기반 RabbitMQ 작업 발행과 결과 소비 adapter를 제공한다."""

import os
import time
from dataclasses import dataclass
from typing import Any

import pika

from research_backend.infrastructure.messaging.contracts import CollectionTask

DEFAULT_RABBITMQ_URL = (
    "amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research"
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


@dataclass(frozen=True)
class RabbitDelivery:
    """수동 ACK에 필요한 delivery tag와 JSON body를 전달한다."""

    delivery_tag: int
    body: bytes


class RabbitMQBroker:
    """Collection Queue topology, 작업 발행과 결과 수신을 소유한다.

    AMQP URL을 입력받아 context manager로 사용한다. 연결·인증·broker 오류는 Pika
    예외로 전달하고 메시지는 persistent delivery mode로 발행한다.
    """

    def __init__(self, url: str | None = None) -> None:
        self._url = url or os.getenv("PURCHASE_RESEARCH_RABBITMQ_URL", DEFAULT_RABBITMQ_URL)
        self._connection: pika.BlockingConnection | None = None
        self._channel: Any | None = None

    def __enter__(self) -> "RabbitMQBroker":
        """RabbitMQ에 연결하고 공통 topology를 멱등하게 선언한다."""

        self._connection = pika.BlockingConnection(pika.URLParameters(self._url))
        self._channel = self._connection.channel()
        self._declare_topology()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        """열린 channel과 connection을 안전하게 닫는다."""

        if self._channel is not None and self._channel.is_open:
            self._channel.close()
        if self._connection is not None and self._connection.is_open:
            self._connection.close()

    def publish_task(self, task: CollectionTask) -> None:
        """검증된 검색 작업을 durable task Queue로 발행하고 broker 확인을 기다린다."""

        channel = self._require_channel()
        channel.confirm_delivery()
        published = channel.basic_publish(
            exchange=COLLECTION_EXCHANGE,
            routing_key=SEARCH_ROUTING_KEY,
            body=task.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,
                message_id=task.task_id,
                priority=task.priority,
                type="collection-task.v1",
            ),
            mandatory=True,
        )
        if published is False:
            raise RuntimeError("RabbitMQ broker가 CollectionTask를 확인하지 않았습니다")

    def get_result(self, wait_timeout: float = 30.0) -> RabbitDelivery | None:
        """결과 Queue에서 메시지 하나를 제한 시간 동안 기다려 수동 ACK 정보와 반환한다."""

        channel = self._require_channel()
        connection = self._require_connection()
        deadline = time.monotonic() + max(wait_timeout, 0)
        while True:
            method, _, body = channel.basic_get(queue=RESULT_QUEUE, auto_ack=False)
            if method is not None:
                return RabbitDelivery(delivery_tag=method.delivery_tag, body=body)
            if time.monotonic() >= deadline:
                return None
            connection.sleep(min(0.2, max(deadline - time.monotonic(), 0)))

    def ack(self, delivery: RabbitDelivery) -> None:
        """DB 저장 또는 명시적 실패 처리가 끝난 결과 메시지를 확인 처리한다."""

        self._require_channel().basic_ack(delivery_tag=delivery.delivery_tag)

    def reject(self, delivery: RabbitDelivery, requeue: bool) -> None:
        """계약 오류는 결과 DLQ로, 일시 DB 오류는 원래 Queue로 되돌린다."""

        self._require_channel().basic_nack(
            delivery_tag=delivery.delivery_tag,
            multiple=False,
            requeue=requeue,
        )

    def _declare_topology(self) -> None:
        """Go Worker와 동일한 exchange, retry Queue와 DLQ 구성을 선언한다."""

        channel = self._require_channel()
        channel.exchange_declare(
            exchange=COLLECTION_EXCHANGE,
            exchange_type="direct",
            durable=True,
        )
        channel.exchange_declare(
            exchange=DEAD_LETTER_EXCHANGE,
            exchange_type="direct",
            durable=True,
        )

        definitions = [
            (
                SEARCH_TASK_QUEUE,
                SEARCH_ROUTING_KEY,
                COLLECTION_EXCHANGE,
                {
                    "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
                    "x-dead-letter-routing-key": SEARCH_DEAD_LETTER_KEY,
                    "x-max-priority": 100,
                },
            ),
            (
                SEARCH_RETRY_QUEUE,
                SEARCH_RETRY_KEY,
                COLLECTION_EXCHANGE,
                {
                    "x-message-ttl": 5000,
                    "x-dead-letter-exchange": COLLECTION_EXCHANGE,
                    "x-dead-letter-routing-key": SEARCH_ROUTING_KEY,
                },
            ),
            (
                SEARCH_DEAD_LETTER_QUEUE,
                SEARCH_DEAD_LETTER_KEY,
                DEAD_LETTER_EXCHANGE,
                None,
            ),
            (
                RESULT_QUEUE,
                RESULT_ROUTING_KEY,
                COLLECTION_EXCHANGE,
                {
                    "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
                    "x-dead-letter-routing-key": RESULT_DEAD_LETTER_KEY,
                },
            ),
            (
                RESULT_DEAD_LETTER_QUEUE,
                RESULT_DEAD_LETTER_KEY,
                DEAD_LETTER_EXCHANGE,
                None,
            ),
        ]
        for queue, routing_key, exchange, arguments in definitions:
            channel.queue_declare(queue=queue, durable=True, arguments=arguments)
            channel.queue_bind(queue=queue, exchange=exchange, routing_key=routing_key)

    def _require_channel(self):
        """연결된 channel을 반환하고 context 밖에서 호출하면 실패한다."""

        if self._channel is None or not self._channel.is_open:
            raise RuntimeError("RabbitMQBroker context 안에서 사용해야 합니다")
        return self._channel

    def _require_connection(self) -> pika.BlockingConnection:
        """열린 connection을 반환하고 닫혀 있으면 실패한다."""

        if self._connection is None or not self._connection.is_open:
            raise RuntimeError("RabbitMQ 연결이 열려 있지 않습니다")
        return self._connection
