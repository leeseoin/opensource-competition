import json
from datetime import datetime
from pathlib import Path

import httpx

from ..base import SiteCrawler, jittered_sleep
from ..access_safety import ensure_success, safe_exception_message
from .detail_fetcher import Cm29DetailFetcher
from .verification import verify_products

_LISTING_URL = "https://display-bff-api.29cm.co.kr/api/v1/listing/items?colorchipVariant=treatment"
_HEADERS = {
    "User-Agent": "PurchaseResearchAgent/0.1 (+public product verification; low rate)",
    "Referer": "https://www.29cm.co.kr/",
    "Content-Type": "application/json",
}
_PAGE_SIZE = 50
_JSON_DIR = Path("output/raw_json/29cm")

# 카테고리명 -> (대표 검색어, 대분류명, 중분류명). 2026-08-15에 listing API를 실측한 결과,
# 서버사이드 카테고리 브라우징 전용 API를 찾지 못했다 — pageType=CATEGORY_PLP를 포함해
# facets나 top-level categoryLargeCode를 body 어디에 넣어도 조용히 무시되고 카테고리가
# 뒤섞인 전체 카탈로그(total=1,086,031)가 그대로 돌아온다. 그래서 대표 검색어로 검색한
# 뒤 각 상품에 이미 포함된 largeCategoryName/middleCategoryName이 정확히 일치하는
# 상품만 남기는 방식으로 대신한다. crawl_category()의 대표 검색어로 실제로 찾을 수
# 있는 범위라는 한계가 있어(카테고리 전수 열람이 아님), ABC마트 crawl_category처럼
# 완전한 커버리지를 보장하지 않는다.
CATEGORIES: dict[str, tuple[str, str, str]] = {
    "로퍼_여성": ("로퍼", "여성슈즈", "로퍼"),
    "힐펌프스_여성": ("구두", "여성슈즈", "힐/펌프스"),
    "부츠_여성": ("부츠", "여성슈즈", "부츠"),
    "샌들_여성": ("샌들", "여성슈즈", "샌들"),
    "슬리퍼_여성": ("슬리퍼", "여성슈즈", "슬리퍼"),
    "스니커즈_여성": ("스니커즈", "여성슈즈", "스니커즈"),
    "플랫슈즈_여성": ("로퍼", "여성슈즈", "플랫슈즈"),
    "스니커즈_남성": ("운동화", "남성슈즈", "스니커즈"),
    "로퍼_남성": ("로퍼", "남성슈즈", "로퍼"),
    "구두_남성": ("로퍼", "남성슈즈", "구두"),
    "샌들_남성": ("슬리퍼", "남성슈즈", "샌들"),
    "슬리퍼_남성": ("슬리퍼", "남성슈즈", "슬리퍼"),
}

# 대분류명 -> categoryLargeCode. 2026-08-15에 상품 상세 페이지에 박혀있는 카테고리
# nav(React Server Components 스트리밍 데이터)에서 뽑았다. 위 CATEGORIES가 실제로
# 필터링에 쓰는 값은 아니다 — categoryLargeCode를 listing API 어디에 넣어도 무시된다는
# 걸 실측으로 확인했다(모듈 상단 CATEGORIES 주석 참고). 나중에 진짜 카테고리 브라우징
# API를 찾으면 바로 쓸 수 있도록 참고용으로만 남겨둔다.
LARGE_CATEGORY_CODES: dict[str, str] = {
    "여성의류": "268100100",
    "여성슈즈": "270100100",
    "여성가방": "269100100",
    "여성모자": "310100100",
    "여성주얼리": "305100100",
    "여성액세서리": "271100100",
    "남성의류": "272100100",
    "남성슈즈": "274100100",
    "남성가방": "273100100",
    "남성모자": "311100100",
    "남성주얼리": "306100100",
    "남성액세서리": "275100100",
    "뷰티": "266100100",
    "주방/생활": "292100100",
    "가구/인테리어": "291100100",
    "컴퓨터/디지털": "294100100",
    "가전": "293100100",
    "컬처": "265100100",
    "레저": "286100100",
    "키즈": "290100100",
    "푸드": "289100100",
    "어스": "307100100",
}

# "대분류>중분류[>소분류]" -> 코드. 상품 상세 페이지 카테고리 nav(여성슈즈 중/소분류)와
# 실제 검색 결과에 박힌 largeCategoryNo/middleCategoryNo/smallCategoryNo(여성슈즈,
# 남성슈즈 둘 다, 2026-08-15에 로퍼/힐/부츠/샌들/슬리퍼/스니커즈/운동화 키워드로 실측
# 확인)를 합쳐 정리했다. LARGE_CATEGORY_CODES와 마찬가지로 API 필터링에는 못 쓰고
# 참고/역추적용이다.
SHOE_CATEGORY_CODES: dict[str, str] = {
    "여성슈즈": "270100100",
    "여성슈즈>단독": "270116100",
    "여성슈즈>해외브랜드": "270123100",
    "여성슈즈>샌들": "270104100",
    "여성슈즈>샌들>스트랩샌들": "270104101",
    "여성슈즈>샌들>슬링백샌들": "270104107",
    "여성슈즈>스니커즈": "270106100",
    "여성슈즈>스니커즈>로우탑": "270106102",
    "여성슈즈>스니커즈>러닝화": "270106104",
    "여성슈즈>스니커즈>뮬": "270106109",
    "여성슈즈>부츠": "270103100",
    "여성슈즈>부츠>롱부츠": "270103102",
    "여성슈즈>부츠>플랫부츠": "270103103",
    "여성슈즈>부츠>미드힐부츠": "270103104",
    "여성슈즈>부츠>워커부츠": "270103109",
    "여성슈즈>슬리퍼": "270105100",
    "여성슈즈>슬리퍼>슬라이드": "270105101",
    "여성슈즈>슬리퍼>플립플랍": "270105103",
    "여성슈즈>로퍼": "270101100",
    "여성슈즈>로퍼>플레인로퍼": "270101101",
    "여성슈즈>로퍼>기타로퍼": "270101102",
    "여성슈즈>플랫슈즈": "270121100",
    "여성슈즈>플랫슈즈>메리제인": "270121101",
    "여성슈즈>플랫슈즈>플랫": "270121103",
    "여성슈즈>힐/펌프스": "270102100",
    "여성슈즈>힐/펌프스>미드힐": "270102101",
    "여성슈즈>힐/펌프스>하이힐": "270102102",
    "여성슈즈>힐/펌프스>키튼힐": "270102109",
    "여성슈즈>힐/펌프스>플랫폼": "270102110",
    "여성슈즈>신발 액세서리": "270107100",
    "남성슈즈": "274100100",
    "남성슈즈>스니커즈": "274101100",
    "남성슈즈>스니커즈>로우탑": "274101102",
    "남성슈즈>스니커즈>러닝화": "274101104",
    "남성슈즈>로퍼": "274102100",
    "남성슈즈>로퍼>페니로퍼": "274102101",
    "남성슈즈>로퍼>플레인로퍼": "274102102",
    "남성슈즈>구두": "274103100",
    "남성슈즈>구두>더비": "274103102",
    "남성슈즈>샌들": "274105100",
    "남성슈즈>샌들>워터샌들": "274105109",
    "남성슈즈>슬리퍼": "274110100",
    "남성슈즈>슬리퍼>슬라이드": "274110101",
}


def _format_won(amount: int | float | None) -> str:
    """선택 숫자 금액을 쉼표가 포함된 원화 문자열로 변환한다."""

    if amount is None:
        return ""
    return f"{int(amount):,}원"


class Cm29Crawler(SiteCrawler):
    """29CM 상품 크롤러.

    검색 목록은 JSON을 기본값으로 사용하고, 수집 대상으로 선택한 모든 상품의 공개
    상세 HTML에 포함된 Product JSON-LD를 비교해 상품별 검증 결과를 만든다.
    """

    site_id = "29cm"

    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """29CM 검색 JSON을 수집하고 선택 상품 전체의 상세 HTML을 검증한다.

        Args:
            keyword: 실제 29CM 검색어다.
            max_items: 저장하고 검증할 최대 고유 상품 수다.
            start_page: 수집을 시작할 1 기반 페이지다.
            max_pages: 처리할 페이지 상한이며 없으면 상품 상한까지 진행한다.

        Returns:
            검증 결과가 포함된 상품 목록과 수집 또는 비교 경고다.
        """

        errors: list[str] = []
        all_products: list[dict] = []
        seen_ids: set[str] = set()
        page = start_page
        processed_pages = 0
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        async with httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=False) as client:
            while len(all_products) < max_items:
                body = {
                    "keyword": keyword,
                    "pageType": "SRP",
                    "sortType": "RECOMMENDED",
                    "facets": {},
                    "pageRequest": {"page": page, "size": _PAGE_SIZE},
                }
                print(f"[29CM:search] page={page} keyword={keyword}")

                try:
                    r = await client.post(_LISTING_URL, json=body)
                    ensure_success(r, "29cm")
                    payload = r.json()
                    data = payload.get("data", {})
                    self._save_json(payload, keyword, page, ts_file)
                except Exception as exc:
                    errors.append(f"page {page} 요청 실패: {safe_exception_message(exc, '29cm', '검색')}")
                    break

                items = data.get("list", [])
                new = self._dedup(self._parse(items), seen_ids)
                remaining = max_items - len(all_products)
                selected = new[:remaining]
                selected, verification_errors = await verify_products(
                    client,
                    selected,
                    json_source_url=str(r.url),
                    ts_file=ts_file,
                )
                errors.extend(verification_errors)
                all_products.extend(selected)
                print(f"[29CM:search] page={page} +{len(selected)}개 / 누적 {len(all_products)}개")

                processed_pages += 1
                if max_pages is not None and processed_pages >= max_pages:
                    break
                if not new or not data.get("pagination", {}).get("hasNext", False):
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[29CM:search] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _save_json(self, payload: dict, keyword: str, page: int, ts_file: str) -> None:
        """29CM 검색 JSON 원본을 실행 시각과 페이지별 파일로 저장한다."""

        try:
            _JSON_DIR.mkdir(parents=True, exist_ok=True)
            safe_keyword = "".join(c if c not in r'\/:*?"<>|' else "_" for c in keyword)
            output = _JSON_DIR / f"29cm_{safe_keyword}_{ts_file}_page{page}.json"
            output.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        except OSError:
            pass

    async def crawl_category(
        self, category: str, max_items: int
    ) -> tuple[list[dict], list[str]]:
        """CATEGORIES의 대표 검색어로 검색한 뒤 대분류/중분류명이 일치하는 상품만 남긴다.

        서버사이드 카테고리 브라우징 API를 찾지 못해 검색 결과를 사후 필터링하는
        방식으로 대신한다(모듈 상단 CATEGORIES 주석 참고). 그래서 대표 검색어로
        찾을 수 있는 상품 범위 안에서만 동작하며, 카테고리 전수를 보장하지 않는다.

        Args:
            category: CATEGORIES 딕셔너리 키다.
            max_items: 반환할 고유 상품 상한이다.

        Returns:
            대분류/중분류가 일치하는 상품과 검색 경고 목록이다.
        """

        if category not in CATEGORIES:
            return [], [f"알 수 없는 카테고리: {category}. 가능한 값: {list(CATEGORIES.keys())}"]

        seed_keyword, large_name, middle_name = CATEGORIES[category]
        errors: list[str] = []
        matched: list[dict] = []
        seen_ids: set[str] = set()
        page = 1
        max_pages = 40  # 대표 검색어 결과 안에서만 찾으므로 무한히 넘기지 않도록 상한을 둔다.

        while len(matched) < max_items and page <= max_pages:
            page_products, page_errors = await self.crawl(
                seed_keyword, _PAGE_SIZE, start_page=page, max_pages=1,
            )
            errors.extend(page_errors)
            if not page_products:
                break

            for product in page_products:
                product_id = str(product.get("source_product_id") or "")
                if not product_id or product_id in seen_ids:
                    continue
                seen_ids.add(product_id)
                if _matches_category(product, large_name, middle_name):
                    matched.append(product)

            page += 1
            await jittered_sleep(1.0)

        print(f"[29CM:category] {category} 검색어='{seed_keyword}' {len(matched)}개 (요청 {max_items}개)")
        return matched[:max_items], errors

    async def attach_details(
        self, products: list[dict], limit: int, review_limit: int = 0
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 상세 페이지 데이터(평점·리뷰수·다중이미지·카테고리·옵션·리뷰)를 추가.
        review_limit이 0이면 상품당 리뷰를 페이지네이션으로 전부 가져온다."""
        return await Cm29DetailFetcher().attach(products, limit=limit, review_limit=review_limit)

    def _dedup(self, items: list[dict], seen: set[str]) -> list[dict]:
        """이미 수집한 29CM 상품 ID를 제외하고 새 상품만 반환한다."""

        new = []
        for prod in items:
            pid = prod["source_product_id"]
            if pid and pid not in seen:
                seen.add(pid)
                new.append(prod)
        return new

    def _parse(self, items: list[dict]) -> list[dict]:
        """29CM 검색 JSON 상품을 Python 크롤러의 공통 원본 구조로 변환한다.

        Args:
            items: 공개 listing 응답의 상품 객체 목록이다.

        Returns:
            가격, category, 재고와 공개 출처를 포함한 원본 상품 목록이다.
        """

        products: list[dict] = []
        for item in items:
            info = item.get("itemInfo", {})
            url = item.get("itemUrl", {})
            event = item.get("itemEvent", {}).get("eventProperties", {})

            sell_price = info.get("sellPrice", info.get("displayPrice"))
            original_price = info.get("originalPrice")
            discount_percent = _discount_percent(sell_price, original_price)
            categories = [
                str(event.get(key) or "").strip()
                for key in ("largeCategoryName", "middleCategoryName", "smallCategoryName")
                if str(event.get(key) or "").strip()
            ]
            sold_out = info.get("isSoldOut")

            products.append({
                "source_product_id": str(item.get("itemId", "")),
                "title": info.get("productName", ""),
                "brand": info.get("brandName") or "",
                "price": _format_won(sell_price),
                "price_original": _format_won(original_price),
                "discount_percent": discount_percent,
                "image_url": info.get("thumbnailUrl") or "",
                "color": "",  # 29CM 목록 API는 카드 단위 색상 정보를 안 준다
                "style_code": "",  # 29CM 목록 API는 style code를 안 준다
                "link": url.get("webLink", ""),
                "site": "29cm",
                "review_count": info.get("reviewCount"),
                "category": categories[-1] if categories else "",
                "category_path": " > ".join(categories),
                "in_stock": not sold_out if isinstance(sold_out, bool) else None,
            })
        return products


def _discount_percent(
    sell_price: int | float | None,
    original_price: int | float | None,
) -> int | None:
    """저장하는 일반 판매가와 정상가를 기준으로 할인율을 계산한다.

    Args:
        sell_price: 쿠폰 적용 전 일반 판매가다.
        original_price: 할인 전 정상가다.

    Returns:
        반올림한 일반 판매 할인율이며 계산할 수 없으면 ``None``이다.
    """

    if sell_price is None or original_price in (None, 0):
        return None
    return round((float(original_price) - float(sell_price)) * 100 / float(original_price))


def _matches_category(product: dict, large_name: str, middle_name: str) -> bool:
    """상품의 category_path가 대분류/중분류명을 정확한 단계로 포함하는지 확인한다.

    Args:
        product: Cm29Crawler._parse가 만든 원본 상품이다.
        large_name: 대상 대분류명이다(예: "여성슈즈").
        middle_name: 대상 중분류명이다(예: "샌들").

    Returns:
        category_path를 " > "로 나눈 단계 중 두 이름이 각각 정확히 있으면 참이다.
        부분 문자열이 아니라 단계 단위로 비교해 우연한 일치를 막는다.
    """

    parts = [part.strip() for part in str(product.get("category_path") or "").split(">")]
    parts = [part for part in parts if part]
    return large_name in parts and middle_name in parts
