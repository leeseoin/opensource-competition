"""Collector 검색 결과를 하나의 DB transaction으로 저장하는 use case를 제공한다."""

from sqlalchemy.orm import Session, sessionmaker

from research_backend.application.dto import CollectSearchOutcome
from research_backend.application.ports import SearchCollector, SearchResultStore
from research_backend.clients.collector.models import SearchRequest


class CollectorResultRejectedError(RuntimeError):
    """저장할 수 없는 Collector 상태와 오류 요약을 호출자에게 전달한다."""


class CollectSearchProducts:
    """Collector 검색과 DB 저장을 연결하고 transaction 경계를 소유한다.

    Collector port, 저장 port, 세션 팩토리를 입력받아 CollectSearchOutcome을 반환한다.
    Collector 실패 상태는 저장하지 않으며 HTTP·검증·DB 오류는 rollback 후 전달한다.
    """

    def __init__(
        self,
        collector: SearchCollector,
        repository: SearchResultStore,
        session_factory: sessionmaker[Session],
    ) -> None:
        self._collector = collector
        self._repository = repository
        self._session_factory = session_factory

    def execute(self, request: SearchRequest) -> CollectSearchOutcome:
        """검색 요청을 실행하고 success 또는 partial 결과를 transaction으로 저장한다."""

        result = self._collector.search(request)
        if result.status not in {"success", "partial"}:
            codes = ", ".join(issue.code for issue in result.errors) or "UNKNOWN"
            raise CollectorResultRejectedError(
                f"Collector 상태가 {result.status}라서 DB에 저장하지 않았습니다: {codes}"
            )

        with self._session_factory() as session:
            with session.begin():
                summary = self._repository.save(session, result)

        return CollectSearchOutcome(
            request_id=result.request_id,
            merchant=result.merchant,
            status=result.status,
            products_received=len(result.products),
            total_count=result.total_count,
            has_next=result.has_next,
            saved=summary,
        )
