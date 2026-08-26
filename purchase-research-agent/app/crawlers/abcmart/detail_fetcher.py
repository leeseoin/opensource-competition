import asyncio
import re
import statistics

import httpx
from crawl4ai import AsyncWebCrawler, BrowserConfig, CrawlerRunConfig, MemoryAdaptiveDispatcher

from app.crawlers.access_safety import ensure_success, safe_exception_message

_BASE = "https://abcmart.a-rt.com"
_REVIEW_URL = f"{_BASE}/product/review/get-review-list"
_OPTION_URL  = f"{_BASE}/product/review/get-prd-option"
_HEADERS = {
    "User-Agent": "PurchaseResearchAgent/0.1 (+public product research; low rate)",
    "Referer": "https://abcmart.a-rt.com/",
}
_BROWSER_CFG = BrowserConfig(ignore_https_errors=False, headless=True)
# networkidle 대신 load: JS 실행 완료 후 즉시 반환 → 더 빠르고 결과 동일
# max_retries=1: 동시 연결 폭주로 인한 일시적 타임아웃을 한 번 더 시도해 흡수
_DETAIL_RUN_CFG = CrawlerRunConfig(wait_until="load", page_timeout=20000, max_retries=1)

# 상품 상세 브라우저 탭 동시 개수. crawl4ai arun_many 기본값(20)은 브라우저 탭 기준으로는
# 너무 높아, 프로세스 내부에서 combo가 여러 개 겹치거나 프로세스를 여러 개 띄우면
# 로컬 리소스/네트워크 연결이 폭주해 타임아웃이 급증한다(2026-08-06 실측 확인).
_DEFAULT_BROWSER_CONCURRENCY = 5
# 리뷰/옵션 API(httpx)는 브라우저 탭보다 가벼워 좀 더 높게 허용.
_DEFAULT_API_CONCURRENCY = 10

_EVAL_LABELS: dict[int, str] = {
    10000: "종합",
    10006: "착화감",
    10012: "가격",
    10072: "디자인",
    10078: "내구성",
    10090: "경량성",
}


def _prdtno(link: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", link)
    return m.group(1) if m else None


def _parse_scores(evlts: list[dict]) -> tuple[float | None, dict[str, int]]:
    overall = None
    details: dict[str, int] = {}
    for e in evlts:
        code = e.get("prdtRvwCode")
        score = e.get("evltScore")
        if code is None or score is None:
            continue
        code = int(code)
        label = _EVAL_LABELS.get(code)
        if label == "종합":
            overall = score
        elif label:
            details[label] = score
    return overall, details


def _parse_review(rv: dict) -> dict:
    evlts = rv.get("productReviewEvlts", [])
    overall_score, detail_scores = _parse_scores(evlts)
    images = [
        img.get("imageUrl") or img.get("prdtImgUrl", "")
        for img in rv.get("productReviewImages", [])
        if img.get("imageUrl") or img.get("prdtImgUrl")
    ]
    return {
        "review_source_id": str(rv.get("prdtRvwSeq")) if rv.get("prdtRvwSeq") is not None else None,
        "content": rv.get("rvwContText", "")[:200],
        "score": overall_score,
        "detail_scores": detail_scores,
        "date": rv.get("writeDtm", "")[:10],
        "size": rv.get("prdtOptnNm") or rv.get("optnName", ""),
        "color": rv.get("prdtColorCodeName", ""),
        "helpful_count": rv.get("helpfulCnt", 0),
        "is_best": rv.get("bestYn") == "Y",
        "images": images,
    }


def _extract_detail(html: str) -> dict:
    """렌더링된 HTML에서 images / category / in_stock 추출."""
    images = list(dict.fromkeys(
        re.sub(r"\?.*$", "", img)
        for img in re.findall(
            r"https://image\.a-rt\.com/art/product/\d{4}/\d{2}/[^\s\"'<>?]+\.jpg\?shrink=580:580",
            html,
        )
    ))
    cat_m = re.search(r'"category"\s*:\s*"([^"]+)"', html)
    category_path = cat_m.group(1) if cat_m else ""
    category = category_path.split(" > ")[-1].strip() if category_path else ""

    all_size = re.findall(r'class="btn-prod-size[^"]*"', html)
    sold_out = re.findall(r'class="btn-prod-size[^"]*\bsold-out\b[^"]*"', html)
    in_stock = bool(all_size) and (len(sold_out) < len(all_size))

    return {"images": images, "category": category, "category_path": category_path, "in_stock": in_stock}


_REVIEW_PAGE_SIZE = 50


async def _fetch_review(client: httpx.AsyncClient, pno: str, review_limit: int = 0) -> dict:
    """리뷰를 제한된 페이지 범위에서 가져와 식별정보 없는 원본으로 변환한다.

    Args:
        client: TLS 검증과 redirect 차단이 설정된 HTTP client다.
        pno: ABC마트 공개 상품 번호다.
        review_limit: 반환할 리뷰 상한이며 0이면 판매처 결과 끝까지 진행한다.

    Returns:
        리뷰 전체 개수, 수집 리뷰와 선택적인 안전 오류 설명이다.
    """
    reviews: list[dict] = []
    total_count = 0
    page = 1
    try:
        while True:
            r = await client.post(
                _REVIEW_URL,
                data={"prdtNo": pno, "pageNum": page, "rowsPerPage": _REVIEW_PAGE_SIZE},
            )
            ensure_success(r, "abcmart")
            data = r.json()
            total_count = data.get("totalCount", 0)
            content = data.get("content", [])
            reviews.extend(_parse_review(rv) for rv in content)

            if review_limit and len(reviews) >= review_limit:
                reviews = reviews[:review_limit]
                break
            if len(content) < _REVIEW_PAGE_SIZE or len(reviews) >= total_count:
                break

            page += 1
            await asyncio.sleep(0.3)
    except Exception as exc:
        return {
            "review_count": total_count,
            "reviews": reviews,
            "_err": safe_exception_message(exc, "abcmart", "리뷰"),
        }

    return {"review_count": total_count, "reviews": reviews}


async def _fetch_option(client: httpx.AsyncClient, pno: str) -> dict:
    """ABC마트 옵션 API에서 색상과 판매처 표시 사이즈를 수집한다.

    Args:
        client: TLS 검증과 redirect 차단이 설정된 HTTP client다.
        pno: ABC마트 공개 상품 번호다.

    Returns:
        색상/사이즈 목록 또는 URL과 응답 body를 제외한 오류다.
    """

    try:
        r = await client.post(_OPTION_URL, data={"prdtNo": pno})
        ensure_success(r, "abcmart")
        data = r.json()
        return {
            "colors": [c.get("codeDtlName", "") for c in data.get("resultColorList", [])],
            "sizes":  [s.get("optnName", "") for s in data.get("resultList", [])],
        }
    except Exception as exc:
        return {"_err": safe_exception_message(exc, "abcmart", "옵션")}


async def _noop() -> dict:
    return {}


_EMPTY_DETAIL = {"images": [], "category": "", "category_path": "", "in_stock": None}


class DetailFetcher:
    """ABC마트 공개 상세/리뷰/옵션을 제한된 동시성으로 결합한다."""

    async def attach_options(
        self,
        products: list[dict],
        limit: int = 10,
        concurrency: int = _DEFAULT_API_CONCURRENCY,
    ) -> tuple[list[dict], list[str]]:
        """리뷰와 browser 없이 공개 옵션 API만 호출해 검색 결과를 보강한다.

        Args:
            products: ABC마트 검색 결과다.
            limit: 옵션을 확인할 상위 상품 수다.
            concurrency: 동시에 실행할 옵션 API 요청 상한이다.

        Returns:
            기존 재고 근거를 보존한 상품 목록과 안전한 부분 실패 경고다.

        Raises:
            ValueError: limit 또는 concurrency가 올바르지 않은 경우다.
        """
        if limit < 0 or concurrency < 1:
            raise ValueError("limit은 0 이상이고 concurrency는 1 이상이어야 합니다")
        targets = products[:limit]
        semaphore = asyncio.Semaphore(concurrency)

        async def _fetch_bounded(client: httpx.AsyncClient, product: dict) -> str | None:
            pno = _prdtno(product.get("link", ""))
            if not pno:
                return "옵션 오류: ABC마트 상품 번호가 없습니다"
            async with semaphore:
                result = await _fetch_option(client, pno)
            if "_err" in result:
                return f"옵션 오류 prdtNo={pno}: {result['_err']}"
            existing = product.get("options") if isinstance(product.get("options"), dict) else {}
            product["options"] = {
                "colors": result.get("colors") or existing.get("colors") or [],
                "sizes": result.get("sizes") or existing.get("sizes") or [],
                "available_sizes": existing.get("available_sizes") or [],
            }
            return None

        async with httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=False) as client:
            results = await asyncio.gather(*(_fetch_bounded(client, product) for product in targets))
        return products, [error for error in results if error is not None]

    async def attach(
        self,
        products: list[dict],
        limit: int = 10,
        reviews_per_item: int = 0,
        delay: float = 0.0,
        browser_concurrency: int = _DEFAULT_BROWSER_CONCURRENCY,
        api_concurrency: int = _DEFAULT_API_CONCURRENCY,
    ) -> tuple[list[dict], list[str]]:
        """상위 상품에 상세, 리뷰, 옵션과 rating을 제한된 동시성으로 추가한다.

        Args:
            products: ABC마트 검색 원본 상품이다.
            limit: 상세를 추가할 상위 상품 수다.
            reviews_per_item: 상품별 리뷰 상한이며 0이면 결과 끝까지 진행한다.
            delay: 호환성을 위해 유지한 추가 대기 초다.
            browser_concurrency: 동시에 열 browser 탭 상한이다.
            api_concurrency: 동시에 실행할 리뷰/옵션 API 상한이다.

        Returns:
            상세가 추가된 상품과 안전하게 축약된 부분 실패 경고다.
        """
        errors: list[str] = []
        targets = products[:limit]
        pnos = [_prdtno(p.get("link", "")) for p in targets]

        # ── 1) 상세 페이지 병렬 크롤링 (동시 탭 수를 dispatcher로 제한) ──
        urls = [p["link"] for p in targets]
        dispatcher = MemoryAdaptiveDispatcher(max_session_permit=browser_concurrency)

        async with AsyncWebCrawler(config=_BROWSER_CFG) as crawler:
            raw_results = await crawler.arun_many(urls, config=_DETAIL_RUN_CFG, dispatcher=dispatcher)
        # arun_many는 입력 순서대로 결과를 반환한다
        detail_list = [_extract_detail(res.html or "") for res in raw_results]

        # ── 2) 리뷰 + 옵션 병렬 API 호출 (세마포어로 동시 요청 수 제한) ──
        api_semaphore = asyncio.Semaphore(api_concurrency)

        async def _fetch_bounded(client: httpx.AsyncClient, pno: str | None):
            async with api_semaphore:
                return await asyncio.gather(
                    _fetch_review(client, pno, reviews_per_item) if pno else _noop(),
                    _fetch_option(client, pno) if pno else _noop(),
                )

        async with httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=False) as client:
            tasks = [_fetch_bounded(client, pno) for pno in pnos]
            api_results = await asyncio.gather(*tasks)

        # ── 3) 결과 병합 ──
        for i, product in enumerate(targets):
            pno = pnos[i]

            # 상세 페이지 데이터
            detail = detail_list[i] if i < len(detail_list) else _EMPTY_DETAIL
            product.update(detail)

            # 리뷰 + 옵션
            rv_data, opt_data = api_results[i]
            if "_err" in rv_data:
                errors.append(f"리뷰 오류 prdtNo={pno}: {rv_data['_err']}")
            product["review_count"] = rv_data.get("review_count", 0)
            product["reviews"] = rv_data.get("reviews", [])
            if "_err" in opt_data:
                errors.append(f"옵션 오류 prdtNo={pno}: {opt_data['_err']}")
            product["options"] = {
                key: value for key, value in opt_data.items() if key != "_err"
            }

            # rating: 리뷰 score 평균
            scores = [rv["score"] for rv in product["reviews"] if rv.get("score") is not None]
            product["rating"] = round(statistics.mean(scores), 1) if scores else None

            print(
                f"[DETAIL] {i+1}/{len(targets)} prdtNo={pno}"
                f" rating={product.get('rating')}"
                f" images={len(product.get('images', []))}"
                f" category={product.get('category')!r}"
                f" in_stock={product.get('in_stock')}"
                f" 리뷰={product.get('review_count')}개"
                f" 옵션={product.get('options')}"
            )

        return products, errors
