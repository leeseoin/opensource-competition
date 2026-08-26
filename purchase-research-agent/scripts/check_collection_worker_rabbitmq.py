"""격리된 RabbitMQ vhost에서 Python Worker의 성공/retry/DLQ 경로를 검증한다."""

from __future__ import annotations

import asyncio
import json
import os
from datetime import datetime, timezone
from typing import Any

import aio_pika

from app.messaging.processor import CollectionTaskProcessor
from app.messaging.rabbitmq import (
    COLLECTION_EXCHANGE,
    RESULT_QUEUE,
    SEARCH_DEAD_LETTER_QUEUE,
    SEARCH_RETRY_QUEUE,
    SEARCH_ROUTING_KEY,
    RabbitCollectionWorker,
    declare_topology,
)


class FixtureCrawler:
    """외부 판매처 대신 고정 상품 또는 일시 경고를 반환하는 통합 테스트 대역이다."""

    def __init__(self, *, warning: str | None = None) -> None:
        """선택적인 수집 경고를 보관한다."""

        self._warning = warning

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
        detail_limit: int = 0,
        option_limit: int = 0,
        page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """경고가 있으면 실패 fixture를, 없으면 상품 한 건을 반환한다."""

        if self._warning:
            return [], [self._warning]
        return [{
            "source_product_id": f"fixture-{page}",
            "title": f"Fixture 상품 {keyword}",
            "brand": "Fixture",
            "price": "99,000원",
            "link": f"https://example.com/products/fixture-{page}",
            "options": {"sizes": ["265"], "colors": ["BLACK"]},
            "in_stock": True,
        }], []


def _task(task_id: str, *, max_attempts: int = 3) -> dict[str, Any]:
    """통합 테스트용 CollectionTask v1 객체를 생성한다."""

    return {
        "schemaVersion": "1",
        "taskId": task_id,
        "jobId": f"job-{task_id}",
        "merchant": "abcmart",
        "operation": "search",
        "priority": 10,
        "attempt": 0,
        "maxAttempts": max_attempts,
        "requestedAt": datetime.now(timezone.utc).isoformat(),
        "idempotencyKey": f"collection:v1:{'b' * 64}",
        "payload": {
            "query": "검정 운동화",
            "page": 2,
            "limit": 5,
            "locale": "ko-KR",
            "currency": "KRW",
            "filters": {"priceMax": 150000, "sizes": ["265"], "colors": ["black"]},
        },
    }


async def _publish_task(url: str, body: bytes) -> None:
    """검색 exchange에 persistent 통합 테스트 작업을 발행한다."""

    connection = await aio_pika.connect_robust(url)
    async with connection:
        channel = await connection.channel(publisher_confirms=True, on_return_raises=True)
        exchange, _ = await declare_topology(channel)
        await exchange.publish(
            aio_pika.Message(
                body=body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ),
            routing_key=SEARCH_ROUTING_KEY,
            mandatory=True,
        )


async def _read_one(url: str, queue_name: str) -> bytes:
    """지정 Queue의 메시지 한 건을 읽어 ACK하고 body를 반환한다."""

    connection = await aio_pika.connect_robust(url)
    async with connection:
        channel = await connection.channel()
        queue = await channel.declare_queue(queue_name, passive=True)
        message = await queue.get(timeout=3, fail=True)
        await message.ack()
        return message.body


async def check_success(url: str) -> None:
    """성공 작업이 결과 Queue로 전달되고 page가 결과에 반영되는지 검증한다."""

    await _publish_task(url, json.dumps(_task("integration-success")).encode())
    worker = RabbitCollectionWorker(url, CollectionTaskProcessor(FixtureCrawler(), 1))
    await worker.run(once=True)
    result = json.loads(await _read_one(url, RESULT_QUEUE))
    assert result["status"] == "success", result
    assert result["collectorResult"]["products"][0]["externalId"] == "fixture-2", result


async def check_retry(url: str) -> None:
    """일시 오류 작업이 attempt 증가 후 retry Queue로 전달되는지 검증한다."""

    await _publish_task(url, json.dumps(_task("integration-retry", max_attempts=2)).encode())
    processor = CollectionTaskProcessor(FixtureCrawler(warning="connection timeout"), 1)
    await RabbitCollectionWorker(url, processor).run(once=True)
    retry_task = json.loads(await _read_one(url, SEARCH_RETRY_QUEUE))
    assert retry_task["attempt"] == 1, retry_task


async def check_invalid_dlq(url: str) -> None:
    """계약 위반 작업이 결과 없이 검색 DLQ로 이동하는지 검증한다."""

    invalid = b'{"schemaVersion":"unknown"}'
    await _publish_task(url, invalid)
    await RabbitCollectionWorker(url, CollectionTaskProcessor(FixtureCrawler(), 1)).run(once=True)
    dead_body = await _read_one(url, SEARCH_DEAD_LETTER_QUEUE)
    assert dead_body == invalid, dead_body


async def main() -> None:
    """환경 URL의 격리 vhost에서 세 가지 RabbitMQ 경로를 순서대로 검사한다."""

    url = os.environ.get("PURCHASE_RESEARCH_RABBITMQ_URL", "")
    if not url:
        raise ValueError("PURCHASE_RESEARCH_RABBITMQ_URL이 필요합니다")
    await check_success(url)
    await check_retry(url)
    await check_invalid_dlq(url)
    print("Python Collection Worker RabbitMQ integration: success/retry/DLQ PASS")


if __name__ == "__main__":
    asyncio.run(main())
