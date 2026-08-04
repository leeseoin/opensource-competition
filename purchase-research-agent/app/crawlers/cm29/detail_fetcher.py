import asyncio
import json
import re

import httpx

_DETAIL_URL = "https://product.29cm.co.kr/catalog/{item_id}"
_REVIEW_URL = "https://review-api.29cm.co.kr/api/v4/reviews"
_IMG_BASE = "https://img.29cm.co.kr"

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://www.29cm.co.kr/",
    "Accept": "text/html",
}
_REVIEW_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
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


class Cm29DetailFetcher:

    async def attach(
        self,
        products: list[dict],
        limit: int = 10,
        review_limit: int = 0,
        delay: float = 0.5,
    ) -> tuple[list[dict], list[str]]:
        """상위 limit개 상품에 상세 페이지 데이터(평점·카테고리·옵션)와 리뷰를 추가한다.
        review_limit이 0이면 상품당 리뷰를 페이지네이션으로 전부 가져온다."""
        errors: list[str] = []

        detail_client = httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=True)
        review_client = httpx.AsyncClient(headers=_REVIEW_HEADERS, timeout=10, follow_redirects=True)

        async with detail_client, review_client:
            for i, product in enumerate(products[:limit]):
                item_id = product.get("source_product_id", "")
                if not item_id:
                    continue

                # ── 상세 페이지 (JSON-LD, 옵션) ──
                try:
                    r = await detail_client.get(_DETAIL_URL.format(item_id=item_id))
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

                except Exception as e:
                    errors.append(f"detail 오류 itemId={item_id}: {e}")

                # ── 리뷰 API (페이지네이션으로 전체 수집, review_limit>0이면 그 개수에서 멈춤) ──
                try:
                    reviews: list[dict] = []
                    rv_count = None
                    rv_page = 1
                    while True:
                        rr = await review_client.get(
                            _REVIEW_URL,
                            params={"itemId": item_id, "page": rv_page, "size": _REVIEW_PAGE_SIZE},
                        )
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
                except Exception as e:
                    errors.append(f"review 오류 itemId={item_id}: {e}")
                    product.setdefault("reviews", [])

                print(
                    f"[CM29:detail] {i+1}/{min(limit, len(products))}"
                    f" id={item_id}"
                    f" rating={product.get('rating')}"
                    f" reviews={product.get('review_count')} (fetched={len(product.get('reviews', []))})"
                    f" images={len(product.get('images', []))}"
                    f" opts={len(product.get('options', []))}"
                )

                if delay > 0 and i < limit - 1:
                    await asyncio.sleep(delay)

        return products, errors
