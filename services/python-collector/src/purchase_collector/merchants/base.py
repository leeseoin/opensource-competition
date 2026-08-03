"""판매처별 페이지 수집 구현이 따라야 할 공통 경계를 정의한다."""

from __future__ import annotations

from abc import ABC, abstractmethod

import httpx

from ..models import PageResult


class MerchantAdapter(ABC):
    """공개 검색 응답을 v1-unified 상품으로 변환하는 판매처 Adapter다."""

    merchant: str
    page_size_limit: int = 100

    @abstractmethod
    async def fetch_page(
        self,
        client: httpx.AsyncClient,
        query: str,
        page: int,
        page_size: int,
    ) -> PageResult:
        """판매처의 한 검색 페이지를 요청하고 변환한다.

        Raises:
            MerchantRequestError: HTTP 오류, 응답 변경 또는 해석 실패가 발생한 경우다.
        """
