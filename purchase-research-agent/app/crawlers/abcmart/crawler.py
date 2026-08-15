import re
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode

import httpx
from bs4 import BeautifulSoup
from crawl4ai import AsyncWebCrawler, BrowserConfig

from ..access_safety import safe_exception_message
from ..base import SiteCrawler, jittered_sleep
from .detail_fetcher import DetailFetcher
from .json_fetcher import AbcJsonFetcher
from .verification import mark_failed, reconcile_page

_SEARCH_PAGE = "https://abcmart.a-rt.com/display/search-word/result"
_PAGE_SIZE = 30
_CATEGORY_URL = "https://abcmart.a-rt.com/display/category/main?genderGbnCode={gender}&ctgrNo={ctgrNo}&page={page}"
_BRAND_URL = (
    "https://abcmart.a-rt.com/product/brand/page"
    "?brandNo={brandNo}&tChnnlNo={tChnnlNo}&genderGbnCode={gender}&page={page}"
)
_PRODUCT_BASE = "https://abcmart.a-rt.com/product?prdtNo="
_HTML_DIR = Path("output/raw_html/abcmart")


def _save_html(html: str, label: str, page: int, ts_file: str) -> None:
    _HTML_DIR.mkdir(parents=True, exist_ok=True)
    out = _HTML_DIR / f"abcmart_{label}_{ts_file}_page{page}.html"
    out.write_text(html, encoding="utf-8")

# 카테고리명 -> (ctgrNo, genderGbnCode). 2026-08-15에 검색 결과 페이지 HTML에 박혀있는
# 전역 카테고리 nav(depth2/depth3 제목)를 파싱해 채운 목록이다(9종 -> 대분류/중분류
# 약 40종 x 성별). nav가 JS로 지연 로드하는 일부 하위 메뉴는 안 잡혔을 수 있어 완전한
# 목록이라고 보장하지 못한다 — "러닝화"처럼 이 nav 스냅샷엔 없었지만 실제로 존재하는
# 카테고리는 직접 확인해 추가해야 한다. 갱신하려면 검색 결과 페이지 HTML을 다시 저장한
# 뒤 depth2-title/depth3-title 클래스의 <a href="...ctgrNo=...&genderGbnCode=...">를
# 다시 뽑으면 된다.
CATEGORIES: dict[str, tuple[str, str]] = {
    "가방_남성": ("1000000484", "10000"),
    "가방_아동": ("1000000484", "10002"),
    "가방_여성": ("1000000484", "10001"),
    "관리용품_남성": ("1000000524", "10000"),
    "관리용품_여성": ("1000000524", "10001"),
    "구두_남성": ("1000000254", "10000"),
    "구두_아동": ("1000000254", "10002"),
    "구두_여성": ("1000000254", "10001"),
    "기타_남성": ("1000000480", "10000"),
    "기타_아동": ("1000000480", "10002"),
    "기타_여성": ("1000000480", "10001"),
    "디지털_남성": ("1000000713", "10000"),
    "디지털_여성": ("1000000713", "10001"),
    "레저_남성": ("1000000720", "10000"),
    "레저_아동": ("1000000720", "10002"),
    "레저_여성": ("1000000720", "10001"),
    # nav 스냅샷엔 없었지만 기존에 확인된 카테고리라 유지한다.
    "러닝화_남성": ("1000000250", "10000"),
    "러닝화_여성": ("1000000250", "10001"),
    "모자_남성": ("1000000493", "10000"),
    "모자_아동": ("1000000493", "10002"),
    "모자_여성": ("1000000493", "10001"),
    "부츠_남성": ("1000000266", "10000"),
    "부츠_아동": ("1000000266", "10002"),
    "부츠_여성": ("1000000266", "10001"),
    "상의_남성": ("1000000456", "10000"),
    "상의_아동": ("1000000456", "10002"),
    "상의_여성": ("1000000456", "10001"),
    "샌들_남성": ("1000000260", "10000"),
    "샌들_아동": ("1000000260", "10002"),
    "샌들_여성": ("1000000260", "10001"),
    "수납용품_남성": ("1000000718", "10000"),
    "수납용품_여성": ("1000000718", "10001"),
    "스니커즈_남성": ("1000000245", "10000"),
    "스니커즈_아동": ("1000000245", "10002"),
    "스니커즈_여성": ("1000000245", "10001"),
    "스포츠_남성": ("1000000249", "10000"),
    "스포츠_남성_1": ("1000000678", "10000"),
    "스포츠_남성_2": ("1000000681", "10000"),
    "스포츠_아동": ("1000000249", "10002"),
    "스포츠_아동_1": ("1000000678", "10002"),
    "스포츠_아동_2": ("1000000681", "10002"),
    "스포츠_여성": ("1000000249", "10001"),
    "스포츠_여성_1": ("1000000678", "10001"),
    "스포츠_여성_2": ("1000000681", "10001"),
    "신발_남성": ("1000000441", "10000"),
    "신발_아동": ("1000000441", "10002"),
    "신발_여성": ("1000000441", "10001"),
    "신발용품_남성": ("1000000520", "10000"),
    "신발용품_아동": ("1000000520", "10002"),
    "신발용품_여성": ("1000000520", "10001"),
    "아우터_남성": ("1000000466", "10000"),
    "아우터_아동": ("1000000466", "10002"),
    "아우터_여성": ("1000000466", "10001"),
    "악세서리_남성": ("1000000502", "10000"),
    "악세서리_아동": ("1000000502", "10002"),
    "악세서리_여성": ("1000000502", "10001"),
    "양말_남성": ("1000000498", "10000"),
    "양말_아동": ("1000000498", "10002"),
    "양말_여성": ("1000000498", "10001"),
    "용품_남성": ("1000000444", "10000"),
    "용품_아동": ("1000000444", "10002"),
    "용품_여성": ("1000000444", "10001"),
    "원피스_남성": ("1000000477", "10000"),
    "원피스_아동": ("1000000477", "10002"),
    "원피스_여성": ("1000000477", "10001"),
    "의류_남성": ("1000000442", "10000"),
    "의류_아동": ("1000000442", "10002"),
    "의류_여성": ("1000000442", "10001"),
    "잡화_남성": ("1000000443", "10000"),
    "잡화_아동": ("1000000443", "10002"),
    "잡화_여성": ("1000000443", "10001"),
    "주얼리_아동": ("1000000513", "10002"),
    "캐주얼_남성": ("1000000410", "10000"),
    "캐주얼_아동": ("1000000410", "10002"),
    "캐주얼_여성": ("1000000410", "10001"),
    "하의_남성": ("1000000472", "10000"),
    "하의_아동": ("1000000472", "10002"),
    "하의_여성": ("1000000472", "10001"),
    "화장품_아동": ("1000000731", "10002"),
}

# 브랜드명 -> (brandNo, tChnnlNo). tChnnlNo: 10001=ABC-MART 일반, 10002=GRAND STAGE(프리미엄).
# 2026-08-15에 검색 결과 페이지 HTML에 박혀있는 전역 브랜드 nav(#brandSearch 옆
# 브랜드 목록)를 파싱해 채운 목록이다(4개 -> 131개). nav가 노출한 건 전부 일반
# 채널(10001)이라 GRAND STAGE 전용 브랜드는 이 스냅샷에 없다 — 조던처럼 GRAND STAGE
# 채널로만 확인된 브랜드는 별도 키로 유지한다. 갱신하려면 검색 결과 페이지 HTML을
# 다시 저장한 뒤 href에 brandNo=가 있는 <a> 태그의 .brand-name.kor 텍스트를 다시
# 뽑으면 된다.
BRANDS: dict[str, tuple[str, str]] = {
    "가민": ("900144", "10001"),
    "고두겟": ("900120", "10001"),
    "골스튜디오": ("000511", "10001"),
    "꼬까참새": ("000534", "10001"),
    "꼬무신": ("000478", "10001"),
    "나이키": ("000050", "10001"),
    "누오보": ("000081", "10001"),
    "뉴발란스": ("000048", "10001"),
    "다이노솔즈": ("000519", "10001"),
    "다이노킹즈": ("900162", "10001"),
    "닥터마틴": ("000018", "10001"),
    "대너": ("000216", "10001"),
    "데시레드": ("900017", "10001"),
    "드라반": ("900042", "10001"),
    "또떼또떼": ("000117", "10001"),
    "라라고": ("900007", "10001"),
    "라코스테": ("000131", "10001"),
    "락피쉬웨더웨어": ("000561", "10001"),
    "로아드로아": ("900117", "10001"),
    "롤리팝": ("000574", "10001"),
    "롬크": ("900081", "10001"),
    "루디프로젝트": ("000589", "10001"),
    "루미": ("900112", "10001"),
    "루셰트": ("900170", "10001"),
    "루카디스": ("900050", "10001"),
    "리게타": ("000567", "10001"),
    "리복": ("000058", "10001"),
    "리블렛츠더블엠": ("900043", "10001"),
    "마리엘라": ("000157", "10001"),
    "마이애미프로젝트": ("900033", "10001"),
    "마크모크": ("000558", "10001"),
    "멜리니": ("900161", "10001"),
    "멜리사": ("000514", "10001"),
    "미르": ("900146", "10001"),
    "미즈노": ("000576", "10001"),
    "바켄": ("900130", "10001"),
    "박스앤콕스": ("900025", "10001"),
    "반스": ("000072", "10001"),
    "밸롭": ("900088", "10001"),
    "밸롭 키즈": ("900122", "10001"),
    "베러댄88": ("900039", "10001"),
    "베어파우": ("000172", "10001"),
    "베이비잼": ("900074", "10001"),
    "벤시몽": ("000240", "10001"),
    "보그스": ("900160", "10001"),
    "봉봉프렌즈": ("000492", "10001"),
    "브룩스": ("000581", "10001"),
    "비에스큐티바이클래시": ("900028", "10001"),
    "사토리산": ("900096", "10001"),
    "슈보이": ("000577", "10001"),
    "슈브릭": ("900097", "10001"),
    "슈브제": ("900119", "10001"),
    "스케쳐스": ("000065", "10001"),
    "스탠스": ("000578", "10001"),
    "스테파노로시": ("000066", "10001"),
    "스텝스": ("900110", "10001"),
    "스트레이": ("900001", "10001"),
    "써코니": ("000062", "10001"),
    "아날로그무드": ("900116", "10001"),
    "아디다스": ("000003", "10001"),
    "아소부": ("900145", "10001"),
    "아식스": ("000430", "10001"),
    "아이더 세이프티": ("900156", "10001"),
    "아이리스": ("900086", "10001"),
    "아키클래식": ("000569", "10001"),
    "앤커버": ("900036", "10001"),
    "앨빈클로": ("900067", "10001"),
    "앨빈클로 주니어": ("900118", "10001"),
    "어프어프": ("900024", "10001"),
    "에이비씨 셀렉트": ("000528", "10001"),
    "에이비씨마트": ("000002", "10001"),
    "엘두": ("900069", "10001"),
    "오더이터": ("900098", "10001"),
    "오디너리원": ("900080", "10001"),
    "오션스카이": ("900154", "10001"),
    "요가피기": ("900129", "10001"),
    "요세미티": ("900168", "10001"),
    "윙 하우스": ("000500", "10001"),
    "유연 키즈": ("000549", "10001"),
    "이머전시": ("900040", "10001"),
    "이파네마": ("000445", "10001"),
    "일세야콥센": ("900157", "10001"),
    "작시": ("000541", "10001"),
    "잔스포츠": ("000152", "10001"),
    "재클라": ("000568", "10001"),
    "제이다울": ("900029", "10001"),
    "조던": ("090050", "10001"),
    # GRAND STAGE 채널 한정으로 확인된 별도 시드다. 위 조던(10001) 목록은 검색결과
    # nav에서 뽑은 일반 채널 항목이라 이 특별판은 남겨둔다.
    "조던_그랜드스테이지": ("090050", "10002"),
    "좀비킥스": ("900121", "10001"),
    "챔피온": ("000456", "10001"),
    "체이스컬트": ("900153", "10001"),
    "츄바스코": ("900140", "10001"),
    "카고브로스": ("900113", "10001"),
    "카고브로스 포 우먼": ("900166", "10001"),
    "캐스키드슨": ("900158", "10001"),
    "캘빈클라인 진": ("900149", "10001"),
    "커스텀에이드": ("900070", "10001"),
    "커스텀에이드 우먼": ("900108", "10001"),
    "컨버스": ("000074", "10001"),
    "컬럼비아": ("000616", "10001"),
    "케이투 세이프티": ("900155", "10001"),
    "케즈": ("000538", "10001"),
    "코뮤엘로": ("900016", "10001"),
    "크록스": ("000093", "10001"),
    "크루클린": ("900068", "10001"),
    "클레용": ("900071", "10001"),
    "키위": ("000038", "10001"),
    "킨토": ("900147", "10001"),
    "테바": ("000293", "10001"),
    "티엘": ("000584", "10001"),
    "팀버랜드": ("000067", "10001"),
    "팁토이조이": ("900159", "10001"),
    "파스코로젠": ("900105", "10001"),
    "파인드칠린": ("900051", "10001"),
    "팔라디움": ("900150", "10001"),
    "페이유에": ("000465", "10001"),
    "펠로우": ("900148", "10001"),
    "포머티브": ("900127", "10001"),
    "포쉬프로젝트": ("900047", "10001"),
    "폴로": ("000055", "10001"),
    "푸마": ("000054", "10001"),
    "프레드 페리": ("000141", "10001"),
    "프로-스펙스": ("000207", "10001"),
    "프리키쉬빌딩": ("900048", "10001"),
    "플래니트": ("900171", "10001"),
    "플로리다 스튜디오": ("900034", "10001"),
    "피퍼": ("900169", "10001"),
    "핏플랍": ("000246", "10001"),
    "핑거프리": ("000572", "10001"),
    "호킨스": ("000032", "10001"),
    "휠라": ("000125", "10001"),
    "힐탑": ("900167", "10001"),
}


def _extract_prdtno(url: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", url)
    return m.group(1) if m else None


class AbcMartCrawler(SiteCrawler):
    """ABC마트 검색 JSON과 렌더링 HTML을 페이지별로 교차 검증한다."""

    site_id = "abcmart"

    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict], list[str]]:
        """지정한 페이지의 JSON을 기본 수집하고 같은 HTML을 전수 검증한다.

        Args:
            keyword: ABC마트 검색어다.
            max_items: 반환할 고유 상품 상한이다.
            start_page: 수집을 시작할 1 기반 페이지다.
            max_pages: 처리할 페이지 상한이며 없으면 상품 상한까지 진행한다.

        Returns:
            교차 검증 정보가 포함된 원본 상품과 부분 실패 경고 목록이다.
        """

        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=False)
        json_fetcher = AbcJsonFetcher()
        page = start_page
        processed_pages = 0
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        headers = {
            "User-Agent": "PurchaseResearchAgent/0.1 (+public product verification; low rate)",
            "Accept": "application/json",
            "Accept-Language": "ko-KR",
        }
        async with httpx.AsyncClient(headers=headers, timeout=15, follow_redirects=False) as client, \
                AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                html_url = self._search_url(keyword, page, _PAGE_SIZE)
                print(f"[ABCMART:full] page={page} keyword={keyword}")

                # 페이지 요청 1건의 일시적 네트워크 예외로 남은 페이지 전체를 포기하지
                # 않도록, 실패 시 짧은 대기 후 한 번 더 시도한다(2026-08-06 실측:
                # 동시 워커 부하 중 순간적인 연결 예외가 재시도 없이 pagination을
                # 통째로 끊어 목표치의 88%를 날린 사례 확인).
                page_succeeded = False
                last_error = ""
                for attempt in range(2):
                    try:
                        json_page = await json_fetcher.fetch_page(
                            client, keyword, page, _PAGE_SIZE, ts_file,
                        )
                        result = await crawler.arun(url=html_url, delay_before_return_html=2.0)
                        remaining = max_items - len(all_products)
                        selected_json_products = json_page.products[:remaining]
                        if result.success:
                            _save_html(result.html, keyword, page, ts_file)
                            html_items = self._parse(BeautifulSoup(result.html, "html.parser"))
                            selected_ids = {
                                str(product.get("source_product_id") or "")
                                for product in selected_json_products
                            }
                            selected_html_items = [
                                product for product in html_items
                                if str(product.get("source_product_id") or "") in selected_ids
                            ]
                            page_items, verification_errors = reconcile_page(
                                selected_json_products,
                                selected_html_items,
                                json_source_url=json_page.source_url,
                                html_source_url=html_url,
                            )
                        else:
                            page_items, verification_errors = mark_failed(
                                selected_json_products,
                                json_source_url=json_page.source_url,
                                html_source_url=html_url,
                                reason=f"page {page} browser 로드 실패",
                            )
                        errors.extend(verification_errors)
                        new = self._dedup(page_items, seen_prdt_nos)
                        all_products.extend(new)
                        print(f"[ABCMART:full] page={page} +{len(new)}개 / 누적 {len(all_products)}개")
                        page_succeeded = True
                        break

                    except Exception as exc:
                        last_error = safe_exception_message(exc, "abcmart", "검색")
                        if attempt == 0:
                            print(f"[ABCMART:full] page={page} 예외, 재시도 1회")
                            await jittered_sleep(2.0, spread=1.0)

                if not page_succeeded:
                    errors.append(f"page {page} 예외(재시도 후에도 실패): {last_error}")
                    break

                processed_pages += 1
                if max_pages is not None and processed_pages >= max_pages:
                    break
                if not new or not json_page.has_next:
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:full] 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    def _search_url(self, keyword: str, page: int, page_size: int) -> str:
        """JSON 요청과 동일한 검색어, 페이지 및 페이지 크기의 화면 URL을 만든다."""

        params = {
            "channel": "10001",
            "searchWord": keyword,
            "smartSearchCheck": "true",
            "page": page,
            "perPage": page_size,
            "pageColumn": 3,
            "sort": "point",
            "tabGubun": "total",
            "searchPageGubun": "product",
        }
        return f"{_SEARCH_PAGE}?{urlencode(params)}"

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
        """ABC마트 category 번호와 성별 코드로 공개 목록을 수집한다.

        Args:
            ctgrNo: ABC마트 category 번호다.
            gender: 성별 channel 코드다.
            max_items: 반환할 상품 상한이다.
            label: 로그와 원본 파일에 사용할 안전한 표시 이름이다.

        Returns:
            원본 상품과 URL/traceback을 제외한 경고 목록이다.
        """
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=False)
        page = 1
        tag = label or f"ctgrNo={ctgrNo}/gender={gender}"
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _CATEGORY_URL.format(ctgrNo=ctgrNo, gender=gender, page=page)
                print(f"[ABCMART:category] {tag} page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"[{tag}] page {page} 로드 실패")
                        break

                    _save_html(result.html, tag, page, ts_file)
                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:category] {tag} page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception as exc:
                    errors.append(f"[{tag}] page {page} 예외: {safe_exception_message(exc, 'abcmart', '카테고리')}")
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:category] {tag} 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def crawl_by_brand(
        self, brand: str, max_items: int, gender: str = "10000"
    ) -> tuple[list[dict], list[str]]:
        """브랜드 기반 크롤링 (BRANDS 딕셔너리 키 사용, gender는 genderGbnCode)."""
        if brand not in BRANDS:
            return [], [f"알 수 없는 브랜드: {brand}. 가능한 값: {list(BRANDS.keys())}"]

        brand_no, channel_no = BRANDS[brand]
        return await self.crawl_by_brand_no(brand_no, channel_no, gender, max_items, label=brand)

    async def crawl_by_brand_no(
        self, brand_no: str, channel_no: str, gender: str, max_items: int, label: str = ""
    ) -> tuple[list[dict], list[str]]:
        """ABC마트 brand/channel/성별 코드로 공개 목록을 수집한다.

        Args:
            brand_no: ABC마트 brand 번호다.
            channel_no: 판매 channel 번호다.
            gender: 성별 channel 코드다.
            max_items: 반환할 상품 상한이다.
            label: 로그와 원본 파일에 사용할 안전한 표시 이름이다.

        Returns:
            원본 상품과 URL/traceback을 제외한 경고 목록이다.
        """
        errors: list[str] = []
        all_products: list[dict] = []
        seen_prdt_nos: set[str] = set()
        browser_cfg = BrowserConfig(ignore_https_errors=False)
        page = 1
        tag = label or f"brandNo={brand_no}/channel={channel_no}/gender={gender}"
        ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")

        async with AsyncWebCrawler(config=browser_cfg, verbose=False) as crawler:
            while len(all_products) < max_items:
                url = _BRAND_URL.format(brandNo=brand_no, tChnnlNo=channel_no, gender=gender, page=page)
                print(f"[ABCMART:brand] {tag} page={page}")

                try:
                    result = await crawler.arun(url=url, delay_before_return_html=2.0)
                    if not result.success:
                        errors.append(f"[{tag}] page {page} 로드 실패")
                        break

                    _save_html(result.html, tag, page, ts_file)
                    soup = BeautifulSoup(result.html, "html.parser")
                    page_items = self._parse(soup)
                    new = self._dedup(page_items, seen_prdt_nos)
                    all_products.extend(new)
                    print(f"[ABCMART:brand] {tag} page={page} +{len(new)}개 / 누적 {len(all_products)}개")

                    if not new:
                        break

                except Exception as exc:
                    errors.append(f"[{tag}] page {page} 예외: {safe_exception_message(exc, 'abcmart', '브랜드')}")
                    break

                page += 1
                await jittered_sleep(1.0)

        print(f"[ABCMART:brand] {tag} 최종 {len(all_products)}개")
        return all_products[:max_items], errors

    async def attach_details(
        self, products: list[dict], limit: int
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 리뷰/옵션을 덧붙인다 (ABC마트 내부 API 사용)."""
        return await DetailFetcher().attach(products, limit=limit)

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
            normal_cost_el = item.select_one(".price-normal-cost")
            unit_el = item.select_one(".price-unit")
            discount_el = item.select_one(".price-sale-percent")
            img_el = item.select_one(".img-wrap img")
            prod_link_el = item.select_one("a.prod-link")

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
            normal_cost = normal_cost_el.get_text(strip=True) if normal_cost_el else ""

            discount_text = discount_el.get_text(strip=True) if discount_el else ""
            discount_match = re.search(r"\d+", discount_text)
            discount_percent = int(discount_match.group()) if discount_match else None

            image_url = img_el.get("src", "") if img_el else ""
            color = prod_link_el.get("data-prdt-color-info", "") if prod_link_el else ""
            style_code = prod_link_el.get("data-style-info", "") if prod_link_el else ""

            if prdtno:
                link = f"{_PRODUCT_BASE}{prdtno}"
            else:
                link_el = prod_link_el or item.select_one("a[href*='prdtNo']") or item.select_one("a[href^='/product']")
                href = link_el.get("href", "") if link_el else ""
                pno = _extract_prdtno(href)
                link = f"{_PRODUCT_BASE}{pno}" if pno else f"https://www.abcmart.co.kr{href}"

            source_product_id = prdtno or _extract_prdtno(link) or ""

            products.append({
                "source_product_id": source_product_id,
                "title": title,
                "brand": brand,
                "price": f"{cost}{unit}",
                "price_original": f"{normal_cost}{unit}" if normal_cost else "",
                "discount_percent": discount_percent,
                "image_url": image_url,
                "color": color,
                "style_code": style_code,
                "link": link,
                "site": "abcmart",
            })
        return products
