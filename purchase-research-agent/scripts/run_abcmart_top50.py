"""구두 + 스니커즈(운동화) 카테고리에서 ABC마트 상위 50개를 크롤링해 output/에 저장한다.

사용법:
    python scripts/run_abcmart_top50.py

기본 배분: 구두_남성 25개 + 스니커즈_남성 25개 = 50개.
카테고리/개수를 바꾸려면 TARGETS를 수정한다 (가능한 카테고리 키는
app/crawlers/abcmart/crawler.py의 CATEGORIES 참조).
"""

import asyncio
import json
from datetime import datetime
from pathlib import Path

from app.crawlers.abcmart import AbcMartCrawler

TARGETS: list[tuple[str, int]] = [
    ("구두_남성", 25),
    ("스니커즈_남성", 25),
]
DETAIL_LIMIT = 5  # 카테고리당 상위 N개에만 리뷰/옵션을 붙인다 (ABC마트 내부 API 호출이라 느림)


async def main() -> None:
    crawler = AbcMartCrawler()
    ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")
    all_items: list[dict] = []
    all_errors: list[str] = []

    for category, max_items in TARGETS:
        print(f"\n=== {category} (최대 {max_items}개) ===")
        products, errors = await crawler.crawl_category(category, max_items)
        all_errors.extend(errors)

        if DETAIL_LIMIT > 0 and products:
            products, detail_errors = await crawler.attach_details(products, limit=DETAIL_LIMIT)
            all_errors.extend(detail_errors)

        all_items.extend(products)
        print(f"{category}: {len(products)}개 수집")

    out_dir = Path("output")
    out_dir.mkdir(exist_ok=True)
    out_file = out_dir / f"abcmart_구두_운동화_top50_{ts_file}.json"
    out_file.write_text(json.dumps(all_items, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n총 {len(all_items)}개 수집, 오류 {len(all_errors)}건")
    print(f"저장: {out_file}")
    if all_errors:
        print("에러:")
        for e in all_errors:
            print(f"  - {e}")

    print(f"\n검증하려면: python scripts/validate_abcmart_crawl.py {out_file}")


if __name__ == "__main__":
    asyncio.run(main())
