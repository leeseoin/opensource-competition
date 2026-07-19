import re
import traceback

from bs4 import BeautifulSoup
from crawl4ai import AsyncWebCrawler, BrowserConfig

_URL = "https://www.abcmart.co.kr/abcmart/search/totalSearch.do?q={keyword}&pageIdx={page}"


def _extract_prdtno(url: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", url)
    return m.group(1) if m else None


class AbcMartCrawler:

    async def crawl(
        self, keyword: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=True)
        page = 1

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _URL.format(keyword=keyword, page=page)
                print(f"[ABCMART] page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"page {page} 로드 실패")
                        break

                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)

                    new = []
                    for prod in page_items:
                        pno = _extract_prdtno(prod["link"])
                        if pno:
                            if pno not in seen_prdt_nos:
                                seen_prdt_nos.add(pno)
                                new.append(prod)
                        else:
                            new.append(prod)

                    all_products.extend(new)
                    print(f"[ABCMART] page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception:
                    tb = traceback.format_exc()
                    errors.append(f"page {page} 예외:\n{tb}")
                    break

                page += 1

        print(f"[ABCMART] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _parse(self, soup: BeautifulSoup) -> list[dict]:
        products: list[dict] = []
        items = [
            li for li in soup.select("li.col-list-item.prod-item")
            if li.select_one("a[href^='/product']")
        ]
        for item in items:
            title_el = item.select_one(".prod-name") or item.select_one("[class*='name']")
            link_el = item.select_one("a[href^='/product']")
            cost_el = item.select_one(".price-cost") or item.select_one(".price-normal-cost")
            unit_el = item.select_one(".price-unit")

            if not title_el or not cost_el:
                continue
            title = title_el.get_text(strip=True)
            if not title:
                continue
            cost = cost_el.get_text(strip=True)
            unit = unit_el.get_text(strip=True) if unit_el else "원"
            href = f"https://www.abcmart.co.kr{link_el.get('href', '')}" if link_el else ""
            products.append({"title": title, "price": f"{cost}{unit}", "link": href, "site": "abcmart"})
        return products
