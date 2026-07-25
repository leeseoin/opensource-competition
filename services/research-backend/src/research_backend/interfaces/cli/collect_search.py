"""실제 Collector 검색 결과를 PostgreSQL에 저장하는 개발용 CLI를 제공한다."""

import argparse
import json
import os
import sys
from dataclasses import asdict
from datetime import datetime
from uuid import uuid4

from pydantic import ValidationError
from sqlalchemy.exc import SQLAlchemyError

from research_backend.application.use_cases import (
    CollectorResultRejectedError,
    CollectSearchProducts,
)
from research_backend.clients.collector import CollectorClientError, CollectorHttpClient, SearchRequest
from research_backend.clients.collector.models import SearchFilters
from research_backend.infrastructure.database.session import (
    create_database_engine,
    create_session_factory,
    get_database_url,
)
from research_backend.repositories import SqlAlchemySearchResultRepository


def build_parser() -> argparse.ArgumentParser:
    """판매처, 검색어와 연결 설정을 받는 CLI parser를 생성한다."""

    parser = argparse.ArgumentParser(description="Go Collector 상품 검색 결과를 PostgreSQL에 저장합니다.")
    parser.add_argument("--merchant", required=True, help="판매처 코드: abcmart 또는 29cm")
    parser.add_argument("--query", required=True, help="검색어")
    parser.add_argument("--limit", type=int, default=3, help="수집할 최대 상품 수(기본 3, 최대 50)")
    parser.add_argument("--size", action="append", default=[], help="필요한 사이즈, 여러 번 지정 가능")
    parser.add_argument("--in-stock-only", action="store_true", help="재고가 있는 상품만 요청")
    parser.add_argument(
        "--collector-url",
        default=os.getenv("PURCHASE_RESEARCH_COLLECTOR_BASE_URL", "http://127.0.0.1:8090"),
        help="Go Collector 주소",
    )
    parser.add_argument(
        "--database-url",
        default=get_database_url(),
        help="PostgreSQL SQLAlchemy URL",
    )
    return parser


def run(argv: list[str] | None = None) -> int:
    """CLI 인자를 검증하고 Collector 요청부터 DB 저장까지 실행한다.

    인자 배열을 입력받아 성공 시 0, 연결·계약·DB 실패 시 1을 반환한다. 비밀번호가
    포함될 수 있는 DB URL은 정상 출력과 오류 메시지에 기록하지 않는다.
    """

    args = build_parser().parse_args(argv)
    engine = None

    try:
        request = SearchRequest(
            requestId=f"manual-{uuid4().hex}",
            merchant=args.merchant,
            query=args.query,
            requestedAt=datetime.now().astimezone(),
            limit=args.limit,
            filters=SearchFilters(sizes=args.size, inStockOnly=args.in_stock_only),
        )
        engine = create_database_engine(args.database_url)
        with CollectorHttpClient(args.collector_url) as collector:
            use_case = CollectSearchProducts(
                collector=collector,
                repository=SqlAlchemySearchResultRepository(),
                session_factory=create_session_factory(engine),
            )
            outcome = use_case.execute(request)
        print(json.dumps(asdict(outcome), ensure_ascii=False, indent=2))
        return 0
    except (CollectorClientError, CollectorResultRejectedError, SQLAlchemyError, ValidationError) as exc:
        print(f"수집·저장 실패: {exc}", file=sys.stderr)
        return 1
    finally:
        if engine is not None:
            engine.dispose()


def main() -> None:
    """console script 종료 코드를 운영체제에 전달한다."""

    raise SystemExit(run())


if __name__ == "__main__":
    main()
