import os
from datetime import datetime

import httpx
from dotenv import load_dotenv

load_dotenv()

_BATCH_SIZE = 100
_BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8081")
_BACKEND_API_KEY = os.getenv("BACKEND_API_KEY", "")
_ENDPOINT = "/api/v1/products/batch"


def _parse_price(price_str) -> int | None:
    if not price_str:
        return None
    digits = "".join(c for c in str(price_str) if c.isdigit())
    return int(digits) if digits else None


def _to_review_payload(review: dict) -> dict | None:
    source_id = review.get("review_source_id")
    if not source_id:
        # 원본 ID가 없으면 재수집 시 중복 판별이 불가능하므로 아예 보내지 않는다.
        return None
    return {
        "reviewSourceId": source_id,
        "content": review.get("content") or "",
        "score": review.get("score"),
        "reviewDate": review.get("date") or None,
        "size": review.get("size") or None,
    }


def _to_product_payload(product: dict) -> dict:
    payload: dict = {
        "sourceProductId": product.get("source_product_id") or "",
        "title": product.get("title", ""),
        "brand": product.get("brand") or None,
        "price": _parse_price(product.get("price")),
        "priceOriginal": _parse_price(product.get("price_original")),
        "discountPercent": product.get("discount_percent"),
        "imageUrl": product.get("image_url") or None,
        "styleCode": product.get("style_code") or None,
        "link": product.get("link", ""),
        "reviewCount": product.get("review_count", 0),
    }

    options = product.get("options") or {}
    if options.get("colors") or options.get("sizes"):
        payload["options"] = {
            "colors": options.get("colors", []),
            "sizes": options.get("sizes", []),
        }

    reviews = [r for r in (_to_review_payload(rv) for rv in product.get("reviews", [])) if r]
    if reviews:
        payload["reviews"] = reviews

    return payload


class BackendClient:
    """purchase-research-backend(Spring Boot)의 상품 배치 적재 API(POST /api/v1/products/batch)를 호출한다.

    크롤링 결과를 100건 단위로 쪼개서 순차 전송하고(§6.1),
    청크별 성공/실패를 합산해서 돌려준다. 배치 안 일부 상품이 실패해도
    나머지는 계속 진행한다(부분 성공 허용, §6.4).
    """

    def __init__(self, base_url: str | None = None, api_key: str | None = None):
        self.base_url = base_url or _BACKEND_BASE_URL
        self.api_key = api_key or _BACKEND_API_KEY

    async def push_products(
        self,
        site: str,
        keyword: str,
        products: list[dict],
    ) -> tuple[dict, list[str]]:
        errors: list[str] = []
        summary = {"totalCount": 0, "successCount": 0, "failCount": 0, "failures": []}

        if not products:
            return summary, errors

        chunks = [products[i:i + _BATCH_SIZE] for i in range(0, len(products), _BATCH_SIZE)]
        headers = {"X-API-Key": self.api_key} if self.api_key else {}

        async with httpx.AsyncClient(base_url=self.base_url, headers=headers, timeout=30) as client:
            for idx, chunk in enumerate(chunks, start=1):
                payload = {
                    "site": site,
                    "keyword": keyword,
                    "collectedAt": datetime.now().astimezone().isoformat(),
                    "products": [_to_product_payload(p) for p in chunk],
                }
                try:
                    r = await client.post(_ENDPOINT, json=payload)
                    r.raise_for_status()
                    data = r.json()
                    summary["totalCount"] += data.get("totalCount", len(chunk))
                    summary["successCount"] += data.get("successCount", 0)
                    summary["failCount"] += data.get("failCount", 0)
                    summary["failures"].extend(data.get("failures", []))
                    print(
                        f"[BACKEND] 청크 {idx}/{len(chunks)} 전송 완료: "
                        f"{data.get('successCount', 0)}/{len(chunk)}건 성공"
                    )
                except Exception as e:
                    errors.append(f"청크 {idx}/{len(chunks)} 전송 실패: {e}")
                    summary["totalCount"] += len(chunk)
                    summary["failCount"] += len(chunk)

        return summary, errors
