"""Python Collection Queue v1 계약, 처리와 RabbitMQ 결정을 검증한다."""

from __future__ import annotations

import asyncio
import json
import unittest
from typing import Any

from app.messaging.contracts import decode_collection_task, validate_collection_result_envelope
from app.messaging.processor import CollectionTaskProcessor
from app.messaging.rabbitmq import (
    RESULT_ROUTING_KEY,
    SEARCH_RETRY_KEY,
    RabbitCollectionWorker,
    decide_message,
)
from app.services.rate_limiter import RateLimitTimeoutError


def _task_body(
    *,
    attempt: int = 0,
    max_attempts: int = 3,
    filters: dict[str, Any] | None = None,
    page: int = 2,
) -> bytes:
    """테스트별 횟수와 필터를 반영한 유효한 Queue v1 JSON body를 만든다."""

    return json.dumps({
        "schemaVersion": "1",
        "taskId": "python-task-001",
        "jobId": "python-job-001",
        "merchant": "abcmart",
        "operation": "search",
        "priority": 25,
        "attempt": attempt,
        "maxAttempts": max_attempts,
        "requestedAt": "2026-08-09T02:00:00+09:00",
        "idempotencyKey": f"collection:v1:{'a' * 64}",
        "payload": {
            "query": "검정 운동화",
            "page": page,
            "limit": 5,
            "locale": "ko-KR",
            "currency": "KRW",
            "filters": filters or {},
        },
    }, ensure_ascii=False).encode("utf-8")


class FakeCrawler:
    """외부 판매처 요청 없이 Processor 호출 인자와 fixture 상품을 제공한다."""

    def __init__(
        self,
        products: list[dict[str, Any]] | None = None,
        warnings: list[str] | None = None,
        delay: float = 0,
    ) -> None:
        """반환 상품, 경고와 선택 지연 시간을 설정한다."""

        self.products = products or []
        self.warnings = warnings or []
        self.delay = delay
        self.calls: list[dict[str, Any]] = []

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
        detail_limit: int = 0,
        page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """호출 인자를 기록한 뒤 설정된 fixture 결과를 반환한다."""

        self.calls.append({
            "keyword": keyword,
            "site": site,
            "max_items": max_items,
            "detail_limit": detail_limit,
            "page": page,
            "max_pages": max_pages,
        })
        if self.delay:
            await asyncio.sleep(self.delay)
        return self.products, self.warnings


class FakeRateLimiter:
    """항상 허용하거나 항상 timeout을 던지도록 고정된 rate limiter 대역이다."""

    def __init__(self, *, timeout: bool = False) -> None:
        self.calls: list[str] = []
        self._timeout = timeout

    async def acquire(self, merchant: str) -> None:
        self.calls.append(merchant)
        if self._timeout:
            raise RateLimitTimeoutError(f"{merchant} 전역 rate limit 대기 시간을 초과했습니다")


class FixedProcessor:
    """RabbitMQ 결정 테스트에 지정 결과만 반환하는 처리기다."""

    def __init__(self, result: dict[str, Any]) -> None:
        """반환할 Queue 결과를 보관한다."""

        self.result = result

    async def process(self, task: Any) -> dict[str, Any]:
        """입력 작업과 무관하게 설정된 결과를 반환한다."""

        return self.result


class FakeExchange:
    """발행 호출 순서와 선택 실패를 기록하는 RabbitMQ exchange 대역이다."""

    def __init__(self, events: list[str], fail: bool = False) -> None:
        """공유 event 목록과 발행 실패 여부를 설정한다."""

        self.events = events
        self.fail = fail

    async def publish(self, message: Any, routing_key: str, mandatory: bool) -> None:
        """발행 정보와 AMQP 우선순위를 기록하고 설정된 경우 broker 오류를 발생시킨다."""

        status = json.loads(message.body).get("status", "retry")
        self.events.append(f"publish:{routing_key}:{status}:{mandatory}:priority={message.priority}")
        if self.fail:
            raise RuntimeError("publisher confirm failed")


class FakeMessage:
    """ACK와 reject 호출을 event 목록에 기록하는 수신 메시지 대역이다."""

    def __init__(self, body: bytes, events: list[str]) -> None:
        """원본 body와 공유 event 목록을 보관한다."""

        self.body = body
        self.events = events

    async def ack(self) -> None:
        """ACK 호출을 기록한다."""

        self.events.append("ack")

    async def reject(self, *, requeue: bool) -> None:
        """reject와 requeue 선택을 기록한다."""

        self.events.append(f"reject:{requeue}")


def _raw_product(
    product_id: str,
    *,
    price: str = "99,000원",
    sizes: list[str] | None = None,
    colors: list[str] | None = None,
    in_stock: bool | None = True,
) -> dict[str, Any]:
    """필터와 CollectorResult 변환에 필요한 최소 원본 상품을 만든다."""

    return {
        "source_product_id": product_id,
        "title": f"상품 {product_id}",
        "brand": "테스트",
        "price": price,
        "link": f"https://example.com/products/{product_id}",
        "image_url": "https://example.com/image.jpg",
        "options": {"sizes": sizes or [], "colors": colors or []},
        "in_stock": in_stock,
    }


class CollectionTaskContractTests(unittest.TestCase):
    """공유 JSON Schema와 재시도 의미 검증을 확인한다."""

    def test_decodes_multi_page_task(self) -> None:
        """Spring Boot가 발행하는 page 2 작업을 손실 없이 해석하는지 검증한다."""

        task = decode_collection_task(_task_body(page=2))

        self.assertEqual(task.payload["page"], 2)
        self.assertTrue(task.can_retry())
        self.assertEqual(task.next_attempt().attempt, 1)

    def test_rejects_unknown_field(self) -> None:
        """계약에 없는 필드가 추가된 작업을 거부하는지 검증한다."""

        raw = json.loads(_task_body())
        raw["secret"] = "unexpected"

        with self.assertRaisesRegex(ValueError, "계약 위반"):
            decode_collection_task(json.dumps(raw).encode())

    def test_rejects_exhausted_attempt(self) -> None:
        """attempt가 maxAttempts와 같아 실행 범위를 벗어난 작업을 거부하는지 검증한다."""

        with self.assertRaisesRegex(ValueError, "attempt"):
            decode_collection_task(_task_body(attempt=3, max_attempts=3))

    def test_rejects_requested_at_without_timezone(self) -> None:
        """timezone이 없는 requestedAt을 모호한 수집 시각으로 거부하는지 검증한다."""

        raw = json.loads(_task_body())
        raw["requestedAt"] = "2026-08-09T02:00:00"

        with self.assertRaisesRegex(ValueError, "timezone"):
            decode_collection_task(json.dumps(raw).encode())

    def test_rejects_running_result_with_completion_data(self) -> None:
        """running 상태에 완료 시각이나 실행 시간이 섞이면 결과 계약이 거부하는지 검증한다."""

        result = {
            "schemaVersion": "1",
            "taskId": "python-task-001",
            "jobId": "python-job-001",
            "status": "running",
            "startedAt": "2026-08-09T02:00:00+09:00",
            "completedAt": "2026-08-09T02:00:01+09:00",
            "durationMs": 1000,
            "collectorResult": None,
            "error": None,
        }

        with self.assertRaisesRegex(ValueError, "running"):
            validate_collection_result_envelope(result)


class CollectionTaskProcessorTests(unittest.IsolatedAsyncioTestCase):
    """페이지 전달, 필터, timeout과 결과 계약을 fixture로 검증한다."""

    async def test_processes_page_and_applies_hard_filters(self) -> None:
        """요청 페이지를 전달하고 가격/사이즈/색상/재고 조건을 모두 적용하는지 검증한다."""

        crawler = FakeCrawler(products=[
            _raw_product("matched", sizes=["265"], colors=["BLACK"]),
            _raw_product("expensive", price="160,000원", sizes=["265"], colors=["BLACK"]),
            _raw_product("wrong-size", sizes=["270"], colors=["BLACK"]),
            _raw_product("sold-out", sizes=["265"], colors=["BLACK"], in_stock=False),
        ])
        processor = CollectionTaskProcessor(crawler, timeout_seconds=1)
        task = decode_collection_task(_task_body(filters={
            "priceMax": 150000,
            "sizes": ["265", "260"],
            "colors": ["black"],
            "inStockOnly": True,
        }))

        result = await processor.process(task)

        validate_collection_result_envelope(result)
        self.assertEqual(result["status"], "success")
        self.assertEqual([product["externalId"] for product in result["collectorResult"]["products"]], ["matched"])
        self.assertEqual(result["collectorResult"]["filters"], task.payload["filters"])
        self.assertEqual(crawler.calls[0]["page"], 2)
        self.assertEqual(crawler.calls[0]["max_items"], 50)
        self.assertEqual(crawler.calls[0]["max_pages"], 1)

    async def test_returns_nonretryable_failure_for_unsupported_attributes(self) -> None:
        """적용할 수 없는 attributes를 성공으로 과장하지 않고 실패시키는지 검증한다."""

        processor = CollectionTaskProcessor(FakeCrawler(), timeout_seconds=1)
        task = decode_collection_task(_task_body(filters={"attributes": {"heel": "3cm"}}))

        result = await processor.process(task)

        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["error"]["code"], "INVALID_TASK")
        self.assertFalse(result["error"]["retryable"])

    async def test_marks_timeout_as_retryable(self) -> None:
        """작업 제한 시간을 넘기면 retry 가능한 안전한 오류만 반환하는지 검증한다."""

        processor = CollectionTaskProcessor(FakeCrawler(delay=0.05), timeout_seconds=0.001)
        result = await processor.process(decode_collection_task(_task_body()))

        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["error"]["code"], "COLLECTOR_TIMEOUT")
        self.assertTrue(result["error"]["retryable"])

    async def test_acquires_global_rate_limit_before_crawling(self) -> None:
        """rate_limiter가 있으면 판매처 검색 전에 전역 토큰을 확보하는지 검증한다."""

        crawler = FakeCrawler(products=[_raw_product("p1")])
        rate_limiter = FakeRateLimiter()
        processor = CollectionTaskProcessor(crawler, timeout_seconds=1, rate_limiter=rate_limiter)

        result = await processor.process(decode_collection_task(_task_body()))

        self.assertEqual(rate_limiter.calls, ["abcmart"])
        self.assertEqual(result["status"], "success")

    async def test_rate_limit_timeout_returns_retryable_failure(self) -> None:
        """전역 rate limit 대기가 초과되면 재시도 가능한 실패로 안전하게 끝나는지 검증한다."""

        crawler = FakeCrawler(products=[_raw_product("p1")])
        rate_limiter = FakeRateLimiter(timeout=True)
        processor = CollectionTaskProcessor(crawler, timeout_seconds=1, rate_limiter=rate_limiter)

        result = await processor.process(decode_collection_task(_task_body()))

        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["error"]["code"], "RATE_LIMIT_TIMEOUT")
        self.assertTrue(result["error"]["retryable"])
        self.assertEqual(crawler.calls, [], "rate limit이 막았으면 판매처 요청을 시작하면 안 된다")


class RabbitDecisionTests(unittest.IsolatedAsyncioTestCase):
    """원본 메시지 ACK/retry/DLQ 결정을 broker 없이 검증한다."""

    async def test_retryable_failure_publishes_next_attempt_then_ack(self) -> None:
        """남은 횟수가 있으면 증가된 작업을 retry routing key로 보내는지 검증한다."""

        decision = await decide_message(
            _task_body(attempt=0, max_attempts=2),
            FixedProcessor({"status": "failed", "error": {"retryable": True}}),
        )

        self.assertEqual(decision.action, "ack")
        self.assertEqual(decision.routing_key, SEARCH_RETRY_KEY)
        self.assertEqual(json.loads(decision.body)["attempt"], 1)
        self.assertEqual(
            decision.priority, 25, "재시도 메시지도 원본 작업의 우선순위를 그대로 유지해야 한다"
        )

    async def test_exhausted_failure_publishes_result_then_rejects(self) -> None:
        """마지막 실행 실패는 결과를 발행하고 원본을 DLQ로 보내는지 검증한다."""

        result = {"status": "failed", "error": {"retryable": True}}
        decision = await decide_message(
            _task_body(attempt=1, max_attempts=2),
            FixedProcessor(result),
        )

        self.assertEqual(decision.action, "reject")
        self.assertEqual(decision.routing_key, RESULT_ROUTING_KEY)
        self.assertEqual(json.loads(decision.body), result)

    async def test_invalid_task_rejects_without_result(self) -> None:
        """식별할 수 없는 입력은 결과를 만들지 않고 DLQ로 보내는지 검증한다."""

        decision = await decide_message(b"not-json", FixedProcessor({}))

        self.assertEqual(decision.action, "reject")
        self.assertIsNone(decision.routing_key)
        self.assertIsNone(decision.body)

    async def test_identifiable_invalid_task_publishes_failure_before_dlq(self) -> None:
        """식별 가능한 계약 위반은 실패 결과를 발행하고 원본을 DLQ로 보내는지 검증한다."""

        raw = json.loads(_task_body())
        raw["payload"]["filters"]["priceMin"] = None

        decision = await decide_message(json.dumps(raw).encode(), FixedProcessor({}))

        result = json.loads(decision.body)
        validate_collection_result_envelope(result)
        self.assertEqual(decision.action, "reject")
        self.assertEqual(decision.routing_key, RESULT_ROUTING_KEY)
        self.assertEqual(result["taskId"], "python-task-001")
        self.assertEqual(result["jobId"], "python-job-001")
        self.assertEqual(result["error"]["code"], "COLLECTION_TASK_CONTRACT_INVALID")
        self.assertFalse(result["error"]["retryable"])

    async def test_ack_happens_after_confirmed_publish(self) -> None:
        """성공 결과 발행이 완료된 뒤에만 원본 작업을 ACK하는지 검증한다."""

        events: list[str] = []
        worker = RabbitCollectionWorker(
            "amqp://unused",
            FixedProcessor({"status": "success", "error": None}),
        )

        await worker._handle_message(FakeExchange(events), FakeMessage(_task_body(), events))

        self.assertEqual(events, [
            f"publish:{RESULT_ROUTING_KEY}:running:True:priority=0",
            f"publish:{RESULT_ROUTING_KEY}:success:True:priority=0",
            "ack",
        ])

    async def test_retry_publish_keeps_original_priority(self) -> None:
        """재시도로 되돌아가는 검색 작업이 원본 우선순위를 AMQP 메시지 속성에도 유지하는지 검증한다."""

        events: list[str] = []
        worker = RabbitCollectionWorker(
            "amqp://unused",
            FixedProcessor({"status": "failed", "error": {"retryable": True}}),
        )

        await worker._handle_message(
            FakeExchange(events),
            FakeMessage(_task_body(attempt=0, max_attempts=2), events),
        )

        self.assertEqual(events, [
            f"publish:{RESULT_ROUTING_KEY}:running:True:priority=0",
            f"publish:{SEARCH_RETRY_KEY}:retry:True:priority=25",
            "ack",
        ])

    async def test_publish_failure_requeues_original(self) -> None:
        """결과 발행이 실패하면 원본 작업을 ACK하지 않고 requeue하는지 검증한다."""

        events: list[str] = []
        worker = RabbitCollectionWorker(
            "amqp://unused",
            FixedProcessor({"status": "success", "error": None}),
        )

        with self.assertRaisesRegex(RuntimeError, "publisher confirm"):
            await worker._handle_message(
                FakeExchange(events, fail=True),
                FakeMessage(_task_body(), events),
            )

        self.assertEqual(
            events, [f"publish:{RESULT_ROUTING_KEY}:running:True:priority=0", "reject:True"]
        )


if __name__ == "__main__":
    unittest.main()
