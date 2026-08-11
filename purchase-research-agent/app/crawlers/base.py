import asyncio
import random
from abc import ABC, abstractmethod


async def jittered_sleep(seconds: float, *, spread: float = 0.2) -> None:
    """페이지/키워드 사이 대기를 고정 간격 대신 약간의 무작위 편차로 흩어지게 한다.

    여러 키워드를 동시에 병렬로 돌릴 때 사이트에는 매 요청이 정확히 N초 간격인
    기계적인 패턴 대신 자연스러운 간격으로 보이게 하려는 목적이다.
    """

    if seconds <= 0:
        return
    low = max(0.0, seconds - spread)
    high = seconds + spread
    await asyncio.sleep(random.uniform(low, high))


class SiteCrawler(ABC):
    """사이트별 크롤러가 구현해야 하는 공통 인터페이스.

    새 사이트를 추가할 때는 이 클래스를 상속하고 crawl()/crawl_category()를
    구현한다. 반환하는 상품 dict는 사이트에 관계없이 다음 공통 필드를 채운다
    (사이트에 없는 값은 빈 문자열/None으로 둔다):

    source_product_id, title, brand, price, price_original, discount_percent,
    image_url, color, style_code, link, site
    """

    site_id: str

    @abstractmethod
    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """지정한 시작 페이지부터 제한된 범위의 키워드 검색을 수행한다.

        Args:
            keyword: 판매처에 전달할 검색어다.
            max_items: 반환할 고유 상품 상한이다.
            start_page: 수집을 시작할 1 기반 페이지다.
            max_pages: 처리할 페이지 상한이며 없으면 상품 상한까지 진행한다.

        Returns:
            원본 상품 목록과 수집 경고 목록이다.
        """

    @abstractmethod
    async def crawl_category(self, category: str, max_items: int) -> tuple[list[dict], list[str]]:
        """카테고리 기반 크롤링. 카테고리를 지원하지 않는 사이트는 빈 결과와 에러 메시지를 반환한다."""

    async def attach_details(self, products: list[dict], limit: int) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 리뷰/옵션 등 상세 정보를 덧붙인다.

        상세 수집을 지원하지 않는 사이트는 기본 구현(no-op)을 그대로 쓰면 된다.
        """
        return products, []
