import asyncio
import json
import re

import httpx

from app.crawlers.access_safety import ensure_success, safe_exception_message

_DETAIL_URL = "https://product.29cm.co.kr/catalog/{item_id}"
_REVIEW_URL = "https://review-api.29cm.co.kr/api/v4/reviews"
_IMG_BASE = "https://img.29cm.co.kr"

_HEADERS = {
    "User-Agent": "PurchaseResearchAgent/0.1 (+public product research; low rate)",
    "Referer": "https://www.29cm.co.kr/",
    "Accept": "text/html",
}
_REVIEW_HEADERS = {
    "User-Agent": "PurchaseResearchAgent/0.1 (+public product research; low rate)",
    "Accept": "application/json",
    "Referer": "https://product.29cm.co.kr/",
    "Origin": "https://product.29cm.co.kr",
}
_SIZE_SURVEY = {1: "작음", 2: "맞음", 3: "큼"}
_REVIEW_PAGE_SIZE = 50


def _parse_ld(html: str) -> dict:
    """JSON-LD에서 Product 스키마와 BreadcrumbList 추출."""
    product, breadcrumb = {}, {}
    for m in re.finditer(
        r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', html, re.S
    ):
        try:
            d = json.loads(m.group(1))
            if d.get("@type") == "Product":
                product = d
            elif d.get("@type") == "BreadcrumbList":
                breadcrumb = d
        except Exception:
            pass
    return product, breadcrumb


def _parse_options(html: str) -> list[dict]:
    """self.__next_f 스트리밍 데이터에서 옵션(SIZE/COLOR) 파싱.
    스트리밍 JSON 안에서 키가 \" 로 이스케이프되어 있는 경우와 일반 " 모두 처리."""
    options = []
    seen = set()
    # 이스케이프된 버전 (\\"optionItemName\\":\\"SIZE\\") 과 일반 버전 모두 포함
    pattern = re.compile(
        r'(?:\\"|")optionItemName(?:\\"|")\s*:\s*(?:\\"|")([^"\\]+)(?:\\"|")'
        r'.{0,30}?'
        r'(?:\\"|")optionItemValue(?:\\"|")\s*:\s*(?:\\"|")([^"\\]+)(?:\\"|")',
        re.S,
    )
    for m in pattern.finditer(html):
        name, value = m.group(1), m.group(2)
        key = (name, value)
        if key not in seen:
            seen.add(key)
            options.append({"name": name, "value": value})
    return options


def _parse_reviews(raw_results: list[dict]) -> list[dict]:
    reviews = []
    for rv in raw_results:
        # 이미지 URL (상대경로 → 절대경로)
        images = [
            (_IMG_BASE + f["url"]) if f["url"].startswith("/") else f["url"]
            for f in rv.get("uploadFiles", [])
            if f.get("isDeleted") != "T"
        ]

        # 사이즈 설문 (1=작음/2=맞음/3=큰)
        size_survey = None
        for s in rv.get("surveyList", []):
            if s.get("surveyType") == "SIZE":
                size_survey = _SIZE_SURVEY.get(s.get("optionValue"))

        # 구매 옵션 문자열 ([SIZE] 등 타입 태그 제거)
        opt_parts = [re.sub(r"^\[[A-Z]+\]", "", o).strip() for o in rv.get("optionValue", [])]

        reviews.append({
            "review_source_id": str(rv.get("itemReviewNo", "")),
            "content": (rv.get("contents") or "")[:500],
            "score": rv.get("point"),
            "date": (rv.get("insertTimestamp") or "")[:10],
            "size": ", ".join(opt_parts),
            "user_size": rv.get("userSize", []),
            "helpful_count": rv.get("helpfulCount", 0),
            "images": images,
            "partner_comment": rv.get("partnerComment"),
            "size_survey": size_survey,
            "is_blind": rv.get("isBlind", False),
        })
    return reviews


_DEFAULT_CONCURRENCY = 10


class Cm29DetailFetcher:
    """29CM 공개 상세와 리뷰를 제한된 동시성으로 상품에 추가한다."""

    async def attach(
        self,
        products: list[dict],
        limit: int = 10,
        review_limit: int = 0,
        concurrency: int = _DEFAULT_CONCURRENCY,
    ) -> tuple[list[dict], list[str]]:
        """상위 상품에 상세, 옵션과 리뷰를 제한된 동시성으로 추가한다.

        Args:
            products: 29CM 검색 원본 상품이다.
            limit: 상세를 추가할 상위 상품 수다.
            review_limit: 상품별 리뷰 상한이며 0이면 결과 끝까지 진행한다.
            concurrency: 동시에 처리할 상품 상한이다.

        Returns:
            상세가 추가된 상품과 URL/응답 body를 제외한 부분 실패 경고다.
        """
        targets = products[:limit]
        errors_by_index: list[list[str]] = [[] for _ in targets]
        semaphore = asyncio.Semaphore(concurrency)

        detail_client = httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=False)
        review_client = httpx.AsyncClient(headers=_REVIEW_HEADERS, timeout=10, follow_redirects=False)

        async def _fetch_detail(item_id: str, product: dict) -> str | None:
            """상세 JSON-LD와 옵션을 상품에 반영하고 안전한 오류를 반환한다.

            Args:
                item_id: 29CM 공개 상품 식별자다.
                product: 상세 필드를 추가할 원본 상품이다.

            Returns:
                성공하면 ``None``, 실패하면 URL과 body가 없는 오류 설명이다.
            """
            try:
                r = await detail_client.get(_DETAIL_URL.format(item_id=item_id))
                ensure_success(r, "29cm")
                html = r.text

                ld_product, ld_breadcrumb = _parse_ld(html)

                agg = ld_product.get("aggregateRating", {})
                product["rating"] = agg.get("ratingValue")
                product["review_count"] = agg.get("reviewCount")

                raw_images = ld_product.get("image", [])
                product["images"] = [
                    img["contentUrl"] if isinstance(img, dict) else img
                    for img in raw_images
                ]

                product["category"] = ld_product.get("category", "")
                crumbs = ld_breadcrumb.get("itemListElement", [])
                product["category_path"] = " > ".join(
                    c["name"] for c in crumbs if c.get("name") and c["name"] != "홈"
                )
                product["category_codes"] = {
                    k: v
                    for crumb in crumbs
                    for k, v in re.findall(r"(category\w+Code)=(\d+)", crumb.get("item", ""))
                }

                avail = ld_product.get("offers", {}).get("availability", "")
                product["in_stock"] = "InStock" in avail
                product["options"] = _parse_options(html)

                # 색상: options[].value에서 / 또는 : 구분자 앞 부분 추출
                # "BLACK (3CM) / KR 230 / IT36" → "BLACK"
                # "베이지-인조가죽-1cm:225mm" → "베이지-인조가죽-1cm"
                seen_c: set[str] = set()
                colors: list[str] = []
                for opt in product["options"]:
                    raw_val = opt.get("value", "")
                    c = re.split(r"\s*/\s*|:", raw_val)[0]
                    c = re.sub(r"\s*\(.*?\)", "", c).strip()
                    c = re.sub(r"^\[[A-Z]+\]", "", c).strip()
                    if c and c not in seen_c:
                        seen_c.add(c)
                        colors.append(c)
                product["color"] = ", ".join(colors)
                return None
            except Exception as exc:
                return f"detail 오류 itemId={item_id}: {safe_exception_message(exc, '29cm', '상세')}"

        async def _fetch_review(item_id: str, product: dict) -> str | None:
            """리뷰를 상한까지 수집해 상품에 반영하고 안전한 오류를 반환한다.

            Args:
                item_id: 29CM 공개 상품 식별자다.
                product: 리뷰를 추가할 원본 상품이다.

            Returns:
                성공하면 ``None``, 실패하면 URL과 body가 없는 오류 설명이다.
            """
            try:
                reviews: list[dict] = []
                rv_count = None
                rv_page = 1
                while True:
                    rr = await review_client.get(
                        _REVIEW_URL,
                        params={"itemId": item_id, "page": rv_page, "size": _REVIEW_PAGE_SIZE},
                    )
                    ensure_success(rr, "29cm")
                    rv_data = rr.json().get("data", {})
                    if rv_count is None and rv_data.get("count") is not None:
                        rv_count = rv_data["count"]
                    if rv_data.get("averagePoint") is not None:
                        product["rating"] = rv_data["averagePoint"]

                    results = rv_data.get("results", [])
                    reviews.extend(_parse_reviews(results))

                    if review_limit and len(reviews) >= review_limit:
                        reviews = reviews[:review_limit]
                        break
                    if len(results) < _REVIEW_PAGE_SIZE or (rv_count is not None and len(reviews) >= rv_count):
                        break

                    rv_page += 1
                    await asyncio.sleep(0.3)

                product["reviews"] = reviews
                if rv_count is not None:
                    product["review_count"] = rv_count
                return None
            except Exception as exc:
                product.setdefault("reviews", [])
                return f"review 오류 itemId={item_id}: {safe_exception_message(exc, '29cm', '리뷰')}"

        async def _fetch_one(index: int, product: dict) -> None:
            item_id = product.get("source_product_id", "")
            if not item_id:
                return
            item_errors = errors_by_index[index]

            async with semaphore:
                # 상세 페이지와 리뷰는 서로 결과를 참조하지 않으므로 한 상품 안에서도 동시에 호출한다.
                detail_error, review_error = await asyncio.gather(
                    _fetch_detail(item_id, product),
                    _fetch_review(item_id, product),
                )
                if detail_error:
                    item_errors.append(detail_error)
                if review_error:
                    item_errors.append(review_error)

            print(
                f"[CM29:detail] {index+1}/{len(targets)}"
                f" id={item_id}"
                f" rating={product.get('rating')}"
                f" reviews={product.get('review_count')} (fetched={len(product.get('reviews', []))})"
                f" images={len(product.get('images', []))}"
                f" opts={len(product.get('options', []))}"
            )

        async with detail_client, review_client:
            await asyncio.gather(*(_fetch_one(i, p) for i, p in enumerate(targets)))

        errors = [error for bucket in errors_by_index for error in bucket]
        return products, errors
