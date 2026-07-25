"""Collector와 repository adapter가 구현할 application port를 제공한다."""

from research_backend.application.ports.search_collection import SearchCollector, SearchResultStore

__all__ = ["SearchCollector", "SearchResultStore"]
