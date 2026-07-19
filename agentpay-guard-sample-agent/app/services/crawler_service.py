import json
from pathlib import Path

from app.crawlers import AbcMartCrawler, DetailFetcher
from app.crawlers.abcmart import CATEGORIES

SUPPORTED_SITES = ["abcmart"]
SUPPORTED_CATEGORIES = list(CATEGORIES.keys())


class CrawlerService:

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
    ) -> tuple[list[dict], list[str]]:
        if site not in SUPPORTED_SITES:
            raise ValueError(f"지원하지 않는 사이트: {site}. 지원 목록: {SUPPORTED_SITES}")

        if site == "abcmart":
            products, errors = await AbcMartCrawler().crawl(keyword, max_items)
        else:
            products, errors = [], []

        return products, errors

    async def search_by_category(
        self,
        category: str,
        max_items: int = 500,
        detail_limit: int = 10,
    ) -> tuple[list[dict], list[str]]:
        """ABC마트 카테고리 기반 수집 + 상위 detail_limit개 상품 리뷰/옵션 수집"""
        products, errors = await AbcMartCrawler().crawl_category(category, max_items)

        if detail_limit > 0 and products:
            products, detail_errors = await DetailFetcher().attach(products, limit=detail_limit)
            errors.extend(detail_errors)

        return products, errors

    def save_raw(self, keyword: str, site: str, products: list[dict], ts_file: str) -> Path:
        raw_dir = Path("output/raw")
        raw_dir.mkdir(parents=True, exist_ok=True)
        out = raw_dir / f"{site}_{keyword}_raw_{ts_file}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(products, f, ensure_ascii=False, indent=2)
        print(f"[RAW] {len(products)}개 -> {out}")
        return out
