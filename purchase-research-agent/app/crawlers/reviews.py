import asyncio
import re

import httpx

_REVIEW_URL = "https://goods.musinsa.com/api2/review/v1/view/list"
_HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}


class ReviewFetcher:

    async def attach(
        self, products: list[dict], per_item: int = 5
    ) -> tuple[list[dict], list[str]]:
        errors: list[str] = []

        async with httpx.AsyncClient(headers=_HEADERS, timeout=10, follow_redirects=True) as client:
            for product in products:
                link = product.get("link", "")
                m = re.search(r"/products/(\d+)", link)
                if not m:
                    product["reviews"] = []
                    product["review_count"] = 0
                    continue

                goods_no = m.group(1)
                try:
                    resp = await client.get(
                        _REVIEW_URL,
                        params={
                            "page": 0,
                            "pageSize": per_item,
                            "goodsNo": goods_no,
                            "sort": "up_cnt_desc",
                            "selectedSimilarNo": goods_no,
                            "myFilter": "false",
                            "hasPhoto": "false",
                            "isExperience": "false",
                        },
                    )
                    data = resp.json()
                    section = data.get("data", {})
                    reviews = [
                        {
                            "grade": r.get("grade"),
                            "content": r.get("content", "")[:200],
                            "helpful": r.get("upCnt", 0),
                            "size_info": r.get("purchaseSizeInfo", {}).get("name", ""),
                        }
                        for r in section.get("list", [])
                    ]
                    product["reviews"] = reviews
                    product["review_count"] = (
                        section.get("totalCount")
                        or section.get("total")
                        or section.get("count")
                        or data.get("totalCount")
                        or len(reviews)
                    )
                except Exception as e:
                    errors.append(f"리뷰 오류 goodsNo={goods_no}: {e}")
                    product["reviews"] = []
                    product["review_count"] = 0

                await asyncio.sleep(0.1)

        return products, errors
