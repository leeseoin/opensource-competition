"""Python Collection Queue Worker 실행 진입점이다."""

from __future__ import annotations

import argparse
import asyncio
import logging
import os

from app.messaging.processor import CollectionTaskProcessor
from app.messaging.rabbitmq import RabbitCollectionWorker
from app.services.rate_limiter import create_rate_limiter_from_env


def parse_args() -> argparse.Namespace:
    """한 건 실행 여부와 작업 timeout CLI 인자를 해석해 반환한다."""

    parser = argparse.ArgumentParser(description="Python Collection Queue Worker")
    parser.add_argument("--once", action="store_true", help="작업 한 건 처리 후 종료")
    parser.add_argument("--timeout", type=float, default=30.0, help="작업 timeout 초")
    return parser.parse_args()


async def run_worker(args: argparse.Namespace) -> None:
    """환경변수에서 AMQP URL을 읽어 Python Queue Worker를 실행한다.

    Args:
        args: parse_args가 생성한 실행 옵션이다.

    Raises:
        ValueError: URL 또는 timeout 설정이 올바르지 않은 경우다.
        AMQPException: RabbitMQ 연결이나 메시지 처리에 실패한 경우다.
    """

    url = os.environ.get("PURCHASE_RESEARCH_RABBITMQ_URL", "")
    processor = CollectionTaskProcessor(timeout_seconds=args.timeout, rate_limiter=create_rate_limiter_from_env())
    worker = RabbitCollectionWorker(url, processor)
    await worker.run(once=args.once)


def main() -> None:
    """logging을 설정하고 비동기 Worker를 실행한다."""

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    asyncio.run(run_worker(parse_args()))


if __name__ == "__main__":
    main()
