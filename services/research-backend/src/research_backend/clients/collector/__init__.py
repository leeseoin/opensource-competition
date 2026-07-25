"""Go Collector 내부 HTTP client와 v1 계약 모델을 제공한다."""

from research_backend.clients.collector.http import CollectorClientError, CollectorHttpClient
from research_backend.clients.collector.models import CollectorResult, SearchRequest

__all__ = ["CollectorClientError", "CollectorHttpClient", "CollectorResult", "SearchRequest"]
