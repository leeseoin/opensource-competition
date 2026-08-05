import asyncio
import json
from pathlib import Path

from app.crawlers import SITE_CRAWLERS
from app.crawlers.abcmart import BRANDS, CATEGORIES
from app.services.contract_validation import validate_items

SUPPORTED_SITES = list(SITE_CRAWLERS.keys())
SUPPORTED_CATEGORIES = list(CATEGORIES.keys())
SUPPORTED_BRANDS = list(BRANDS.keys())


class CrawlerService:

    async def search_items(
        self,
        keyword: str,
        site: str,
        max_items: int = 500,
        detail_limit: int = 0,
    ) -> tuple[list[dict], list[str]]:
        if site not in SITE_CRAWLERS:
            raise ValueError(f"지원하지 않는 사이트: {site}. 지원 목록: {SUPPORTED_SITES}")

        crawler = SITE_CRAWLERS[site]()
        products, errors = await crawler.crawl(keyword, max_items)

        if detail_limit > 0 and products:
            products, detail_errors = await crawler.attach_details(products, limit=detail_limit)
            errors.extend(detail_errors)

        if site == "abcmart":
            products, contract_errors = validate_items(products)
            errors.extend(contract_errors)

        return products, errors

    async def search_multi_site(
        self,
        keyword: str,
        sites: list[str] | None = None,
        max_items: int = 500,
    ) -> dict[str, tuple[list[dict], list[str]]]:
        """여러 사이트를 동시에 검색한다.

        asyncio.gather로 사이트별 search_items()를 같이 실행하므로, 전체 소요 시간은
        사이트별 소요 시간의 합이 아니라 가장 느린 사이트 하나의 소요 시간에 가깝다.
        한 사이트가 예외를 던져도 다른 사이트 결과에는 영향을 주지 않는다
        (return_exceptions=True로 개별 실패를 격리).
        """
        targets = sites or SUPPORTED_SITES
        unknown = [s for s in targets if s not in SITE_CRAWLERS]
        if unknown:
            raise ValueError(f"지원하지 않는 사이트: {unknown}. 지원 목록: {SUPPORTED_SITES}")

        results = await asyncio.gather(
            *(self.search_items(keyword, site, max_items) for site in targets),
            return_exceptions=True,
        )

        merged: dict[str, tuple[list[dict], list[str]]] = {}
        for site, result in zip(targets, results):
            if isinstance(result, BaseException):
                merged[site] = ([], [f"{site} 크롤링 중 예외: {result}"])
            else:
                merged[site] = result
        return merged

    async def search_by_category(
        self,
        category: str,
        max_items: int = 500,
        detail_limit: int = 10,
    ) -> tuple[list[dict], list[str]]:
        """카테고리 기반 수집 + 상위 detail_limit개 상품 리뷰/옵션 수집.

        카테고리 수집은 현재 ABC마트만 지원한다(CATEGORIES가 ABC마트 카테고리 체계라서).
        다른 사이트도 카테고리 수집을 지원하게 되면 site 파라미터를 받아 SITE_CRAWLERS에서
        선택하도록 확장한다.
        """
        crawler = SITE_CRAWLERS["abcmart"]()
        products, errors = await crawler.crawl_category(category, max_items)

        if detail_limit > 0 and products:
            products, detail_errors = await crawler.attach_details(products, limit=detail_limit)
            errors.extend(detail_errors)

        products, contract_errors = validate_items(products)
        errors.extend(contract_errors)

        return products, errors

    async def search_by_brand(
        self,
        brand: str,
        max_items: int = 500,
        detail_limit: int = 10,
        gender: str = "10000",
    ) -> tuple[list[dict], list[str]]:
        """브랜드 기반 수집 + 상위 detail_limit개 상품 리뷰/옵션 수집.

        브랜드 수집은 현재 ABC마트만 지원한다(BRANDS가 ABC마트 brandNo/tChnnlNo 체계라서).
        """
        crawler = SITE_CRAWLERS["abcmart"]()
        products, errors = await crawler.crawl_by_brand(brand, max_items, gender=gender)

        if detail_limit > 0 and products:
            products, detail_errors = await crawler.attach_details(products, limit=detail_limit)
            errors.extend(detail_errors)

        products, contract_errors = validate_items(products)
        errors.extend(contract_errors)

        return products, errors

    def save_raw(self, keyword: str, site: str, products: list[dict], ts_file: str) -> Path:
        raw_dir = Path("output/raw")
        raw_dir.mkdir(parents=True, exist_ok=True)
        out = raw_dir / f"{site}_{keyword}_raw_{ts_file}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(products, f, ensure_ascii=False, indent=2)
        print(f"[RAW] {len(products)}개 -> {out}")
        return out
