from abc import ABC, abstractmethod


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
    async def crawl(self, keyword: str, max_items: int) -> tuple[list[dict], list[str]]:
        """키워드 검색 기반 크롤링"""

    @abstractmethod
    async def crawl_category(self, category: str, max_items: int) -> tuple[list[dict], list[str]]:
        """카테고리 기반 크롤링. 카테고리를 지원하지 않는 사이트는 빈 결과와 에러 메시지를 반환한다."""

    async def attach_details(self, products: list[dict], limit: int) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 리뷰/옵션 등 상세 정보를 덧붙인다.

        상세 수집을 지원하지 않는 사이트는 기본 구현(no-op)을 그대로 쓰면 된다.
        """
        return products, []
