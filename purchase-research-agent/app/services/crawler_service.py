import json
from pathlib import Path

from app.crawlers import AbcMartCrawler, MusinSaCrawler, ReviewFetcher

SITE_NAMES = {
    "abcmart": "ABC마트",
    "musinsa": "무신사",
}
SUPPORTED_SITES = list(SITE_NAMES.keys())


class CrawlerService:

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
        with_reviews: bool = False,
        reviews_per_item: int = 5,
    ) -> tuple[list[dict], list[str]]:
        if site not in SUPPORTED_SITES:
            raise ValueError(f"지원하지 않는 사이트: {site}")

        if site == "musinsa":
            products, errors = await MusinSaCrawler().crawl(keyword, max_items)
        elif site == "abcmart":
            products, errors = await AbcMartCrawler().crawl(keyword, max_items)
        else:
            products, errors = [], []

        if with_reviews and site == "musinsa":
            products, rev_errors = await ReviewFetcher().attach(products, reviews_per_item)
            errors.extend(rev_errors)

        return products, errors

    def save_raw(self, keyword: str, site: str, products: list[dict], ts_file: str) -> Path:
        raw_dir = Path("output/raw")
        raw_dir.mkdir(parents=True, exist_ok=True)
        out = raw_dir / f"{site}_{keyword}_raw_{ts_file}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(products, f, ensure_ascii=False, indent=2)
        print(f"[RAW] {len(products)}개 -> {out}")
        return out
