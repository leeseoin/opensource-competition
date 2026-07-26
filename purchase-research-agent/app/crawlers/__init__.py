from .abcmart import AbcMartCrawler, DetailFetcher
from .base import SiteCrawler

# 사이트 식별자 -> 크롤러 클래스. 새 사이트를 추가할 때는 여기에 등록만 하면
# CrawlerService(및 SUPPORTED_SITES)에 자동으로 반영된다.
SITE_CRAWLERS: dict[str, type[SiteCrawler]] = {
    "abcmart": AbcMartCrawler,
}

__all__ = ["SiteCrawler", "AbcMartCrawler", "DetailFetcher", "SITE_CRAWLERS"]
