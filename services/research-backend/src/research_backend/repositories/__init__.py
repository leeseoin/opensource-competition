"""Python Research Backend가 소유하는 PostgreSQL repository를 제공한다."""

from research_backend.repositories.search_result import SqlAlchemySearchResultRepository

__all__ = ["SqlAlchemySearchResultRepository"]
