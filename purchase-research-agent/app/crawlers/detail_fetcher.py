import asyncio
import re

import httpx

_BASE = "https://abcmart.a-rt.com"
_REVIEW_URL = f"{_BASE}/product/review/get-review-list"
_OPTION_URL = f"{_BASE}/product/review/get-prd-option"
_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://abcmart.a-rt.com/",
}


def _prdtno(link: str) -> str | None:
    m = re.search(r"prdtNo=(\d+)", link)
    return m.group(1) if m else None


class DetailFetcher:

    async def attach(
        self,
        products: list[dict],
        limit: int = 10,
        reviews_per_item: int = 3,
        delay: float = 5.0,
    ) -> tuple[list[dict], list[str]]:
        """
        상위 limit개 상품에 리뷰 + 옵션(사이즈/색상)을 추가한다.
        나머지 상품은 그대로 반환.
        """
        errors: list[str] = []
        targets = products[:limit]

        async with httpx.AsyncClient(headers=_HEADERS, verify=False, timeout=10, follow_redirects=True) as client:
            for i, product in enumerate(targets):
                pno = _prdtno(product.get("link", ""))
                if not pno:
                    product["reviews"] = []
                    product["review_count"] = 0
                    product["options"] = {}
                    continue

                # 리뷰 fetch
                try:
                    r = await client.post(
                        _REVIEW_URL,
                        data={"prdtNo": pno, "pageNum": 1, "rowsPerPage": reviews_per_item},
                    )
                    data = r.json()
                    product["review_count"] = data.get("totalCount", 0)
                    product["reviews"] = [
                        {
                            "content": rv.get("rvwContText", "")[:200],
                            "score": rv.get("evltScore") or rv.get("starScore") or rv.get("totalScore"),
                            "date": rv.get("writeDtm", "")[:10],
                            "size": rv.get("prdtOptnNm") or rv.get("optnName", ""),
                        }
                        for rv in data.get("content", [])
                    ]
                except Exception as e:
                    errors.append(f"리뷰 오류 prdtNo={pno}: {e}")
                    product["reviews"] = []
                    product["review_count"] = 0

                await asyncio.sleep(delay)

                # 옵션 fetch
                try:
                    r = await client.post(_OPTION_URL, data={"prdtNo": pno})
                    data = r.json()
                    product["options"] = {
                        "colors": [c.get("codeDtlName", "") for c in data.get("resultColorList", [])],
                        "sizes": [s.get("optnName", "") for s in data.get("resultList", [])],
                    }
                except Exception as e:
                    errors.append(f"옵션 오류 prdtNo={pno}: {e}")
                    product["options"] = {}

                print(f"[DETAIL] {i+1}/{limit} prdtNo={pno} 리뷰={product['review_count']}개 옵션={product.get('options')}")

                if i < limit - 1:
                    await asyncio.sleep(delay)

        return products, errors
