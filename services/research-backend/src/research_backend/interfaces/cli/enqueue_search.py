"""검색 CollectionTask를 RabbitMQ에 등록하는 개발용 CLI를 제공한다."""

import argparse
import os
import sys
from uuid import uuid4

import pika
from pydantic import ValidationError

from research_backend.clients.collector.models import SearchFilters
from research_backend.infrastructure.messaging import (
    CollectionTask,
    DEFAULT_RABBITMQ_URL,
    RabbitMQBroker,
    SearchPayload,
    build_idempotency_key,
)
from research_backend.infrastructure.messaging.contracts import now_with_timezone


def build_parser() -> argparse.ArgumentParser:
    """판매처·검색어·Queue 연결 설정을 받는 CLI parser를 생성한다."""

    parser = argparse.ArgumentParser(description="RabbitMQ에 판매처 검색 작업을 등록합니다.")
    parser.add_argument("--merchant", required=True, help="판매처 코드: abcmart 또는 29cm")
    parser.add_argument("--query", required=True, help="검색어")
    parser.add_argument("--limit", type=int, default=3, help="수집할 최대 상품 수")
    parser.add_argument("--size", action="append", default=[], help="필요한 사이즈, 여러 번 지정 가능")
    parser.add_argument("--in-stock-only", action="store_true", help="재고가 있는 상품만 요청")
    parser.add_argument("--priority", type=int, default=20, help="0~100 작업 우선순위")
    parser.add_argument("--max-attempts", type=int, default=2, help="최초 실행을 포함한 최대 시도 횟수")
    parser.add_argument("--job-id", help="여러 작업을 묶는 jobId, 생략하면 자동 생성")
    parser.add_argument(
        "--rabbitmq-url",
        default=os.getenv("PURCHASE_RESEARCH_RABBITMQ_URL", DEFAULT_RABBITMQ_URL),
        help="RabbitMQ AMQP URL",
    )
    return parser


def run(argv: list[str] | None = None) -> int:
    """CLI 입력을 CollectionTask로 검증하고 RabbitMQ에 persistent 메시지로 발행한다."""

    args = build_parser().parse_args(argv)
    try:
        payload = SearchPayload(
            query=args.query,
            page=1,
            limit=args.limit,
            filters=SearchFilters(sizes=args.size, inStockOnly=args.in_stock_only),
        )
        task = CollectionTask(
            taskId=f"task-{uuid4().hex}",
            jobId=args.job_id or f"job-{uuid4().hex}",
            merchant=args.merchant,
            priority=args.priority,
            maxAttempts=args.max_attempts,
            requestedAt=now_with_timezone(),
            idempotencyKey=build_idempotency_key(args.merchant, "search", payload),
            payload=payload,
        )
        with RabbitMQBroker(args.rabbitmq_url) as broker:
            broker.publish_task(task)
        print(task.model_dump_json(by_alias=True, indent=2))
        return 0
    except (ValidationError, pika.exceptions.AMQPError, RuntimeError) as exc:
        print(f"검색 작업 등록 실패: {exc}", file=sys.stderr)
        return 1


def main() -> None:
    """console script 종료 코드를 운영체제에 전달한다."""

    raise SystemExit(run())


if __name__ == "__main__":
    main()
