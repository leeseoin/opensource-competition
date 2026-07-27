"""Collector 검색 결과를 하나의 DB transaction으로 저장하는 use case를 제공한다."""

from sqlalchemy.orm import Session, sessionmaker

from research_backend.application.dto import CollectSearchOutcome
from research_backend.application.ports import SearchCollector, SearchResultStore
from research_backend.clients.collector.models import SearchRequest
from research_backend.application.use_cases.store_search_result import (
    CollectorResultRejectedError,
    StoreCollectedSearchResult,
)


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
        return StoreCollectedSearchResult(
            repository=self._repository,
            session_factory=self._session_factory,
        ).execute(result)
