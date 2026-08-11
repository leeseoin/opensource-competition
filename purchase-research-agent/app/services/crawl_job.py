"""워커가 개별 (키워드, 사이트) 조합 하나를 처리할 때 쓰는 공용 함수.

큐 트랜스포트(multiprocessing.Queue, RabbitMQ 등)가 무엇이든 워커가 실제로
수행하는 크롤링 작업은 이 함수 하나로 통일한다 — 트랜스포트를 교체해도
크롤링 로직은 이 파일 한 곳에서만 관리한다.
"""

from app.crawlers import SITE_CRAWLERS


async def crawl_one(
    site: str, keyword: str, max_items: int, detail_limit: int
) -> tuple[list[dict], list[str]]:
    crawler = SITE_CRAWLERS[site]()
    products, errors = await crawler.crawl(keyword, max_items)

    effective_limit = len(products) if detail_limit < 0 else detail_limit
    if effective_limit > 0 and products:
        products, detail_errors = await crawler.attach_details(products, limit=effective_limit)
        errors.extend(detail_errors)

    return products, errors
