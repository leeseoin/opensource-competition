"""RabbitMQ CollectionResult를 검증해 PostgreSQL에 저장하는 Worker CLI를 제공한다."""

import argparse
import json
import os
import sys
from dataclasses import asdict

import pika
from pydantic import ValidationError
from sqlalchemy.exc import SQLAlchemyError

from research_backend.application.use_cases import StoreCollectedSearchResult
from research_backend.infrastructure.database.session import (
    create_database_engine,
    create_session_factory,
    get_database_url,
)
from research_backend.infrastructure.messaging import (
    CollectionResultEnvelope,
    DEFAULT_RABBITMQ_URL,
    RabbitMQBroker,
)
from research_backend.repositories import SqlAlchemySearchResultRepository


def build_parser() -> argparse.ArgumentParser:
    """RabbitMQ·DB 연결과 한 건 처리 여부를 받는 CLI parser를 생성한다."""

    parser = argparse.ArgumentParser(description="RabbitMQ 수집 결과를 PostgreSQL에 저장합니다.")
    parser.add_argument("--once", action="store_true", help="결과 한 건을 처리한 뒤 종료")
    parser.add_argument("--wait-timeout", type=float, default=30, help="--once 결과 대기 시간(초)")
    parser.add_argument(
        "--rabbitmq-url",
        default=os.getenv("PURCHASE_RESEARCH_RABBITMQ_URL", DEFAULT_RABBITMQ_URL),
        help="RabbitMQ AMQP URL",
    )
    parser.add_argument("--database-url", default=get_database_url(), help="PostgreSQL SQLAlchemy URL")
    return parser


def run(argv: list[str] | None = None) -> int:
    """결과 메시지를 계약 검증하고 success·partial만 기존 Repository로 저장한다.

    계약 오류는 결과 DLQ로 보내고 DB 일시 오류는 원래 Queue로 되돌린다. --once가
    없으면 사용자가 중단할 때까지 다음 결과를 기다린다.
    """

    args = build_parser().parse_args(argv)
    engine = create_database_engine(args.database_url)
    store = StoreCollectedSearchResult(
        repository=SqlAlchemySearchResultRepository(),
        session_factory=create_session_factory(engine),
    )

    try:
        with RabbitMQBroker(args.rabbitmq_url) as broker:
            while True:
                delivery = broker.get_result(args.wait_timeout)
                if delivery is None:
                    if args.once:
                        print("대기 시간 안에 CollectionResult가 도착하지 않았습니다.", file=sys.stderr)
                        return 1
                    continue

                try:
                    envelope = CollectionResultEnvelope.model_validate_json(delivery.body)
                except ValidationError as exc:
                    broker.reject(delivery, requeue=False)
                    print(f"CollectionResult 계약 오류로 DLQ 이동: {exc}", file=sys.stderr)
                    if args.once:
                        return 1
                    continue

                if envelope.status == "failed":
                    broker.ack(delivery)
                    print(envelope.model_dump_json(by_alias=True, indent=2))
                else:
                    try:
                        if envelope.collector_result is None:
                            raise RuntimeError("성공 결과에 collectorResult가 없습니다")
                        outcome = store.execute(envelope.collector_result)
                    except SQLAlchemyError as exc:
                        broker.reject(delivery, requeue=True)
                        print(f"DB 저장 실패로 결과를 Queue에 반환: {exc}", file=sys.stderr)
                        return 1
                    broker.ack(delivery)
                    print(json.dumps(asdict(outcome), ensure_ascii=False, indent=2))

                if args.once:
                    return 0
    except (pika.exceptions.AMQPError, RuntimeError) as exc:
        print(f"결과 Worker 실패: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 0
    finally:
        engine.dispose()


def main() -> None:
    """console script 종료 코드를 운영체제에 전달한다."""

    raise SystemExit(run())


if __name__ == "__main__":
    main()
