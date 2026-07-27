"""이미 수집된 CollectorResult를 PostgreSQL에 저장하는 use case를 제공한다."""

from sqlalchemy.orm import Session, sessionmaker

from research_backend.application.dto import CollectSearchOutcome
from research_backend.application.ports import SearchResultStore
from research_backend.clients.collector.models import CollectorResult


class CollectorResultRejectedError(RuntimeError):
    """저장할 수 없는 Collector 상태와 오류 요약을 호출자에게 전달한다."""


class StoreCollectedSearchResult:
    """검증된 Collector 검색 결과와 DB transaction을 연결한다.

    Queue 또는 HTTP에서 받은 CollectorResult, 저장 port와 세션 팩토리를 사용한다.
    success·partial만 저장하고 그 외 상태는 CollectorResultRejectedError를 발생시킨다.
    """

    def __init__(
        self,
        repository: SearchResultStore,
        session_factory: sessionmaker[Session],
    ) -> None:
        self._repository = repository
        self._session_factory = session_factory

    def execute(self, result: CollectorResult) -> CollectSearchOutcome:
        """성공 또는 부분 성공 결과를 하나의 transaction으로 저장한다."""

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
