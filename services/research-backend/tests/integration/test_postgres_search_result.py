"""실제 PostgreSQL에서 Collector 검색 결과 저장을 opt-in으로 검증한다."""

import json
import os
from pathlib import Path
from uuid import uuid4

import pytest
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session, sessionmaker

from research_backend.clients.collector.models import CollectorResult
from research_backend.infrastructure.database.models import MerchantProduct, Product
from research_backend.infrastructure.database.session import create_database_engine, create_session_factory
from research_backend.repositories import SqlAlchemySearchResultRepository

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_POSTGRES_INTEGRATION") != "1",
    reason="RUN_POSTGRES_INTEGRATION=1일 때만 실제 PostgreSQL 테스트를 실행합니다.",
)


def test_search_result_is_saved_to_postgres() -> None:
    """실제 PostgreSQL transaction에서 판매처 상품과 연결 상품을 저장하고 정리한다."""

    repository_root = Path(__file__).resolve().parents[4]
    payload = json.loads(
        (repository_root / "contracts/collector/v1/examples/collector-result.success.json").read_text(
            encoding="utf-8"
        )
    )
    suffix = uuid4().hex
    payload["merchant"] = "integration-test"
    payload["products"][0]["externalId"] = suffix
    result = CollectorResult.model_validate(payload)
    engine = create_database_engine()
    session_factory = create_session_factory(engine)

    try:
        _cleanup_integration_rows(session_factory)
        with session_factory.begin() as session:
            repository = SqlAlchemySearchResultRepository()
            summary = repository.save(session, result)

        with session_factory() as session:
            merchant_product = session.scalar(
                select(MerchantProduct).where(
                    MerchantProduct.merchant == "integration-test",
                    MerchantProduct.external_id == suffix,
                )
            )
            assert summary.offer_snapshots == 1
            assert merchant_product is not None
    finally:
        _cleanup_integration_rows(session_factory)
        engine.dispose()


def _cleanup_integration_rows(session_factory: sessionmaker[Session]) -> None:
    """통합 테스트 전후에 전용 판매처 행과 연결된 공통 상품만 제거한다."""

    with session_factory.begin() as session:
        product_ids = list(
            session.scalars(
                select(MerchantProduct.product_id).where(MerchantProduct.merchant == "integration-test")
            )
        )
        session.execute(delete(MerchantProduct).where(MerchantProduct.merchant == "integration-test"))
        if product_ids:
            session.execute(delete(Product).where(Product.id.in_(product_ids)))

    with session_factory() as session:
        assert session.scalar(
            select(func.count()).select_from(MerchantProduct).where(
                MerchantProduct.merchant == "integration-test"
            )
        ) == 0
