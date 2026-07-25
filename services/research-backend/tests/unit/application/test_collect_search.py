"""검색 수집 use case의 실패 상태 차단과 transaction 호출을 검증한다."""

import json
from datetime import datetime
from pathlib import Path

import pytest
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from research_backend.application.use_cases import CollectorResultRejectedError, CollectSearchProducts
from research_backend.clients.collector.models import CollectorIssue, CollectorResult, SearchRequest
from research_backend.infrastructure.database import Base
from research_backend.infrastructure.database.models import Product
from research_backend.infrastructure.database.session import create_session_factory
from research_backend.repositories import SqlAlchemySearchResultRepository


class StubCollector:
    """테스트가 지정한 CollectorResult를 그대로 반환한다."""

    def __init__(self, result: CollectorResult) -> None:
        self.result = result

    def search(self, request: SearchRequest) -> CollectorResult:
        """입력 요청과 관계없이 준비된 결과를 반환한다."""

        return self.result


class FailingRepository:
    """행을 추가한 뒤 예외를 발생시켜 use case rollback을 검증한다."""

    def save(self, session: Session, result: CollectorResult):
        """임시 상품을 추가하고 의도적인 저장 실패를 발생시킨다."""

        session.add(
            Product(
                name="rollback-test",
                brand=None,
                created_at=result.collected_at,
            )
        )
        session.flush()
        raise RuntimeError("의도적인 저장 실패")


def load_result() -> CollectorResult:
    """공통 성공 예제를 검증된 CollectorResult로 반환한다."""

    repository_root = Path(__file__).resolve().parents[5]
    payload = json.loads(
        (repository_root / "contracts/collector/v1/examples/collector-result.success.json").read_text(
            encoding="utf-8"
        )
    )
    return CollectorResult.model_validate(payload)


def test_blocked_result_is_not_persisted() -> None:
    """blocked 결과는 DB transaction을 시작해 저장하지 않아야 한다."""

    blocked = load_result().model_copy(
        update={
            "status": "blocked",
            "products": [],
            "errors": [CollectorIssue(code="BLOCKED", message="접근 차단", retryable=False)],
        }
    )
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    use_case = CollectSearchProducts(
        collector=StubCollector(blocked),
        repository=SqlAlchemySearchResultRepository(),
        session_factory=create_session_factory(engine),
    )

    with pytest.raises(CollectorResultRejectedError):
        use_case.execute(
            SearchRequest(
                requestId="blocked-test",
                merchant="abcmart",
                query="구두",
                requestedAt=datetime.now().astimezone(),
            )
        )

    engine.dispose()


def test_repository_failure_rolls_back_transaction() -> None:
    """Repository가 중간에 실패하면 앞서 추가한 행도 DB에 남지 않아야 한다."""

    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)
    use_case = CollectSearchProducts(
        collector=StubCollector(load_result()),
        repository=FailingRepository(),
        session_factory=session_factory,
    )

    with pytest.raises(RuntimeError, match="의도적인 저장 실패"):
        use_case.execute(
            SearchRequest(
                requestId="rollback-test",
                merchant="abcmart",
                query="구두",
                requestedAt=datetime.now().astimezone(),
            )
        )

    with session_factory() as session:
        assert session.scalar(select(func.count()).select_from(Product)) == 0

    engine.dispose()
