"""Collector 검색 결과의 SQLAlchemy 저장과 중복 처리 정책을 검증한다."""

import json
from pathlib import Path

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from research_backend.clients.collector.models import CollectorResult
from research_backend.infrastructure.database import Base
from research_backend.infrastructure.database.models import (
    Evidence,
    MerchantProduct,
    OfferSnapshot,
    Product,
    ProductOption,
)
from research_backend.repositories import SqlAlchemySearchResultRepository


def load_result() -> CollectorResult:
    """공통 성공 예제를 검증된 CollectorResult로 반환한다."""

    repository_root = Path(__file__).resolve().parents[5]
    payload = json.loads(
        (repository_root / "contracts/collector/v1/examples/collector-result.success.json").read_text(
            encoding="utf-8"
        )
    )
    return CollectorResult.model_validate(payload)


def test_same_merchant_product_is_reused_and_snapshots_are_appended() -> None:
    """같은 판매처 상품은 중복 생성하지 않고 수집 스냅샷은 매번 추가해야 한다."""

    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    repository = SqlAlchemySearchResultRepository()
    result = load_result()

    with Session(engine) as session:
        with session.begin():
            first = repository.save(session, result)
        with session.begin():
            second = repository.save(session, result)

        assert first.offer_snapshots == 1
        assert second.offer_snapshots == 1
        assert session.scalar(select(func.count()).select_from(Product)) == 1
        assert session.scalar(select(func.count()).select_from(MerchantProduct)) == 1
        assert session.scalar(select(func.count()).select_from(OfferSnapshot)) == 2
        assert session.scalar(select(func.count()).select_from(ProductOption)) == 2
        assert session.scalar(select(func.count()).select_from(Evidence)) == 2

    engine.dispose()
