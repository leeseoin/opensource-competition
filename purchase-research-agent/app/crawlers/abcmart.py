import asyncio
import re
import traceback

from bs4 import BeautifulSoup
from crawl4ai import AsyncWebCrawler, BrowserConfig

_SEARCH_URL = "https://www.abcmart.co.kr/abcmart/search/totalSearch.do?q={keyword}&pageIdx={page}"
_CATEGORY_URL = "https://abcmart.a-rt.com/display/category/main?genderGbnCode={gender}&ctgrNo={ctgrNo}&page={page}"
_PRODUCT_BASE = "https://www.abcmart.co.kr/product?prdtNo="

# 카테고리명 -> (ctgrNo, genderGbnCode)
CATEGORIES: dict[str, tuple[str, str]] = {
    # 신발 전체
    "신발_남성":     ("1000000441", "10000"),
    "신발_여성":     ("1000000441", "10001"),
    "신발_아동":     ("1000000441", "10002"),
    # 스니커즈
    "스니커즈_남성": ("1000000245", "10000"),
    "스니커즈_여성": ("1000000245", "10001"),
    # 스포츠
    "스포츠_남성":   ("1000000249", "10000"),
    "스포츠_여성":   ("1000000249", "10001"),
    # 러닝화
    "러닝화_남성":   ("1000000250", "10000"),
    "러닝화_여성":   ("1000000250", "10001"),
    # 샌들
    "샌들_남성":     ("1000000260", "10000"),
    "샌들_여성":     ("1000000260", "10001"),
    # 부츠
    "부츠_남성":     ("1000000266", "10000"),
    "부츠_여성":     ("1000000266", "10001"),
    # 구두
    "구두_남성":     ("1000000254", "10000"),
    "구두_여성":     ("1000000254", "10001"),
    # 의류
    "의류_남성":     ("1000000442", "10000"),
    "의류_여성":     ("1000000442", "10001"),
    # 잡화
    "잡화_남성":     ("1000000443", "10000"),
    "잡화_여성":     ("1000000443", "10001"),
}


def _extract_prdtno(url: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", url)
    return m.group(1) if m else None


class AbcMartCrawler:

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """키워드 검색 기반 크롤링"""
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _SEARCH_URL.format(keyword=keyword, page=page)
                print(f"[ABCMART:search] page={page} keyword={keyword}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"page {page} 로드 실패")
                        break

                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:search] page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception:
                    tb = traceback.format_exc()
                    errors.append(f"page {page} 예외:\n{tb}")
                    break

                page += 1
                await asyncio.sleep(1)

        print(f"[ABCMART:search] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def crawl_category(
        self, category: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """카테고리 기반 크롤링 (CATEGORIES 딕셔너리 키 사용)"""
        if category not in CATEGORIES:
            return [], [f"알 수 없는 카테고리: {category}. 가능한 값: {list(CATEGORIES.keys())}"]

        ctgrNo, gender = CATEGORIES[category]
        return await self.crawl_category_by_no(ctgrNo, gender, max_items, label=category)

    async def crawl_category_by_no(
        self, ctgrNo: str, gender: str, max_items: int, label: str = ""
    ) -> tuple[list[dict], list[str]]:
        """ctgrNo + genderGbnCode 직접 지정 크롤링"""
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1
        tag = label or f"ctgrNo={ctgrNo}/gender={gender}"

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _CATEGORY_URL.format(ctgrNo=ctgrNo, gender=gender, page=page)
                print(f"[ABCMART:category] {tag} page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"[{tag}] page {page} 로드 실패")
                        break

                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:category] {tag} page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception:
                    tb = traceback.format_exc()
                    errors.append(f"[{tag}] page {page} 예외:\n{tb}")
                    break

                page += 1
                await asyncio.sleep(1)

        print(f"[ABCMART:category] {tag} 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _dedup(self, items: list[dict], seen: set[str]) -> list[dict]:
        new = []
        for prod in items:
            pno = _extract_prdtno(prod["link"])
            if pno:
                if pno not in seen:
                    seen.add(pno)
                    new.append(prod)
            else:
                new.append(prod)
        return new

    def _parse(self, soup: BeautifulSoup) -> list[dict]:
        products: list[dict] = []
        items = soup.select("li.col-list-item.prod-item")
        for item in items:
            prdtno = item.get("data-product-no", "")

            title_el = item.select_one(".prod-name")
            brand_el = item.select_one(".prod-brand")
            cost_el = item.select_one(".price-cost") or item.select_one(".price-normal-cost")
            unit_el = item.select_one(".price-unit")

            if not title_el or not cost_el:
                continue

            # badge-gender 텍스트(남성/여성) 제거 후 제목 추출
            for badge in title_el.select(".badge-gender"):
                badge.decompose()
            title = title_el.get_text(strip=True)
            if not title:
                continue

            cost = cost_el.get_text(strip=True)
            unit = unit_el.get_text(strip=True) if unit_el else "원"
            brand = brand_el.get_text(strip=True) if brand_el else ""

            if prdtno:
                link = f"{_PRODUCT_BASE}{prdtno}"
            else:
                link_el = item.select_one("a[href*='prdtNo']") or item.select_one("a[href^='/product']")
                href = link_el.get("href", "") if link_el else ""
                pno = _extract_prdtno(href)
                link = f"{_PRODUCT_BASE}{pno}" if pno else f"https://www.abcmart.co.kr{href}"

            products.append({
                "title": title,
                "brand": brand,
                "price": f"{cost}{unit}",
                "link": link,
                "site": "abcmart",
            })
        return products
