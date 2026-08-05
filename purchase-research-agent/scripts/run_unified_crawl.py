"""ABC마트와 29CM을 같은 키워드(들)로 동시 조회해 v1-unified 계약과 같은 구조의
단일 JSON 배열로 저장한다.

각 판매처 크롤러는 사이트별 원본 구조(v1-abcmart / v1-29cm)를 그대로 반환한다.
이 스크립트가 app.normalizer로 두 결과를 평탄화하고 site별 contract로 검증한 뒤
하나의 output/unified_{keyword}_{n}_{timestamp}.json 파일로 합친다.

상세(옵션/리뷰/평점) 수집은 판매처별 내부 API를 상품마다 추가로 호출한다. detail_limit을
안 넘기거나 -1을 넘기면 이번 실행에서 수집한 상품 전체(전 상품 옵션 + 전체 리뷰
페이지네이션)에 적용한다. 0이면 상세 수집 자체를 건너뛴다.

키워드는 쉼표로 여러 개를 넘길 수 있다. 이 경우 (키워드 x 사이트) 조합을 한 프로세스
안에서 asyncio.gather로 동시에 처리한다 — 스크립트를 여러 번 따로 실행하는 것과 달리
사이트별 세마포어/커넥션 풀을 공유해 사이트 기준 실질 요청 속도가 예상 밖으로 배가되지
않는다. 동시에 진행할 (키워드 x 사이트) 조합 수는 keyword_concurrency로 제한한다.
한 조합이 예외로 실패해도 나머지 조합 결과에는 영향을 주지 않는다.

사용법:
    python scripts/run_unified_crawl.py [keyword[,keyword2,...]] [max_items_per_site] [detail_limit] [keyword_concurrency]
"""

import asyncio
import json
import sys
from datetime import datetime
from pathlib import Path

from app.crawlers import SITE_CRAWLERS
from app.crawlers.base import jittered_sleep
from app.normalizer import normalize
from app.services.contract_validation import validate_items

_DEFAULT_KEYWORD_CONCURRENCY = 3


async def _crawl_site(site: str, keyword: str, max_items: int, detail_limit: int) -> tuple[list[dict], list[str]]:
    crawler = SITE_CRAWLERS[site]()
    products, errors = await crawler.crawl(keyword, max_items)

    effective_limit = len(products) if detail_limit < 0 else detail_limit
    if effective_limit > 0 and products:
        products, detail_errors = await crawler.attach_details(products, limit=effective_limit)
        errors.extend(detail_errors)

    return products, errors


async def _crawl_site_bounded(
    semaphore: asyncio.Semaphore,
    site: str,
    keyword: str,
    max_items: int,
    detail_limit: int,
) -> tuple[list[dict], list[str]]:
    """동시에 진행하는 (키워드 x 사이트) 조합 수를 세마포어로 제한하고, 시작 전 약간의
    무작위 지연을 둬 여러 조합이 정확히 같은 순간에 몰리지 않게 한다."""

    async with semaphore:
        await jittered_sleep(0.3, spread=0.3)
        return await _crawl_site(site, keyword, max_items, detail_limit)


async def main() -> None:
    raw_keyword = sys.argv[1] if len(sys.argv) > 1 else "구두"
    keywords = [k.strip() for k in raw_keyword.split(",") if k.strip()] or ["구두"]
    max_items = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    detail_limit = int(sys.argv[3]) if len(sys.argv) > 3 else -1
    keyword_concurrency = int(sys.argv[4]) if len(sys.argv) > 4 else _DEFAULT_KEYWORD_CONCURRENCY

    semaphore = asyncio.Semaphore(keyword_concurrency)
    sites = list(SITE_CRAWLERS)
    combos = [(keyword, site) for keyword in keywords for site in sites]

    results = await asyncio.gather(
        *(
            _crawl_site_bounded(semaphore, site, keyword, max_items, detail_limit)
            for keyword, site in combos
        ),
        return_exceptions=True,
    )

    unified: list[dict] = []
    all_errors: list[str] = []
    for (keyword, site), result in zip(combos, results):
        if isinstance(result, BaseException):
            all_errors.append(f"{keyword}/{site} 크롤링 중 예외: {result}")
            print(f"{keyword}/{site}: 예외로 실패 - {result}")
            continue

        products, errors = result
        all_errors.extend(f"{keyword}/{site}: {error}" for error in errors)
        valid, contract_errors = validate_items(products, site=site)
        all_errors.extend(f"{keyword}/{site}: {error}" for error in contract_errors)
        unified.extend(normalize(p) for p in valid)
        print(f"{keyword}/{site}: {len(valid)}개 (원본 {len(products)}개, contract 위반 {len(contract_errors)}건)")

    out_dir = Path("output")
    out_dir.mkdir(exist_ok=True)
    ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")
    keyword_label = "_".join(keywords) if len(keywords) <= 3 else f"{keywords[0]}외{len(keywords) - 1}건"
    out_file = out_dir / f"unified_{keyword_label}_{len(unified)}_{ts_file}.json"
    out_file.write_text(json.dumps(unified, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n총 {len(unified)}개 통합 수집, 오류 {len(all_errors)}건")
    print(f"저장: {out_file}")
    if all_errors:
        print("에러:")
        for e in all_errors:
            print(f"  - {e}")


if __name__ == "__main__":
    asyncio.run(main())
