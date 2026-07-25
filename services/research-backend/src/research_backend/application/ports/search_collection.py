"""검색 수집 use case가 외부 HTTP와 저장 구현에 의존하지 않도록 port를 정의한다."""

from typing import Protocol

from sqlalchemy.orm import Session

from research_backend.application.dto import CollectionSaveSummary
from research_backend.clients.collector.models import CollectorResult, SearchRequest


class SearchCollector(Protocol):
    """검색 요청을 수행하고 검증된 Collector 결과를 반환하는 port다."""

    def search(self, request: SearchRequest) -> CollectorResult:
        """검색 요청을 입력받아 결과를 반환하며 연결·계약 실패 시 예외를 발생시킨다."""


class SearchResultStore(Protocol):
    """검증된 Collector 검색 결과를 현재 DB transaction에 저장하는 port다."""

    def save(self, session: Session, result: CollectorResult) -> CollectionSaveSummary:
        """세션과 결과를 입력받아 저장 개수를 반환하며 DB 실패 시 예외를 전달한다."""
