"""ABC마트와 29CM을 같은 키워드로 동시 조회해 v1-unified 계약과 같은 구조의
단일 JSON 배열로 저장한다.

각 판매처 크롤러는 사이트별 원본 구조(v1-abcmart / v1-29cm)를 그대로 반환한다.
이 스크립트가 app.normalizer로 두 결과를 평탄화하고 site별 contract로 검증한 뒤
하나의 output/unified_{keyword}_{n}_{timestamp}.json 파일로 합친다.

상세(옵션/리뷰/평점) 수집은 판매처별 내부 API를 상품마다 추가로 호출한다. detail_limit을
안 넘기거나 -1을 넘기면 이번 실행에서 수집한 상품 전체(전 상품 옵션 + 전체 리뷰
페이지네이션)에 적용한다. 0이면 상세 수집 자체를 건너뛴다.

사용법:
    python scripts/run_unified_crawl.py [keyword] [max_items_per_site] [detail_limit]
"""

import asyncio
import json
import sys
from datetime import datetime
from pathlib import Path

from app.crawlers import SITE_CRAWLERS
from app.normalizer import normalize
from app.services.contract_validation import validate_items


async def _crawl_site(site: str, keyword: str, max_items: int, detail_limit: int) -> tuple[list[dict], list[str]]:
    crawler = SITE_CRAWLERS[site]()
    products, errors = await crawler.crawl(keyword, max_items)

    effective_limit = len(products) if detail_limit < 0 else detail_limit
    if effective_limit > 0 and products:
        products, detail_errors = await crawler.attach_details(products, limit=effective_limit)
        errors.extend(detail_errors)

    return products, errors


async def main() -> None:
    keyword = sys.argv[1] if len(sys.argv) > 1 else "구두"
    max_items = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    detail_limit = int(sys.argv[3]) if len(sys.argv) > 3 else -1

    results = await asyncio.gather(
        *(_crawl_site(site, keyword, max_items, detail_limit) for site in SITE_CRAWLERS),
    )

    unified: list[dict] = []
    all_errors: list[str] = []
    for site, (products, errors) in zip(SITE_CRAWLERS, results):
        all_errors.extend(errors)
        valid, contract_errors = validate_items(products, site=site)
        all_errors.extend(contract_errors)
        unified.extend(normalize(p) for p in valid)
        print(f"{site}: {len(valid)}개 (원본 {len(products)}개, contract 위반 {len(contract_errors)}건)")

    out_dir = Path("output")
    out_dir.mkdir(exist_ok=True)
    ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_file = out_dir / f"unified_{keyword}_{len(unified)}_{ts_file}.json"
    out_file.write_text(json.dumps(unified, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n총 {len(unified)}개 통합 수집, 오류 {len(all_errors)}건")
    print(f"저장: {out_file}")
    if all_errors:
        print("에러:")
        for e in all_errors:
            print(f"  - {e}")


if __name__ == "__main__":
    asyncio.run(main())
