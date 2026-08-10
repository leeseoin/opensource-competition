"""요청 예산/wall time 상한과 checkpoint/resume이 적용된 대량 수집 CLI다.

scripts/run_queue_crawl.py, run_abcmart_top50.py 같은 기존 스크립트는 (키워드, 사이트)
조합 하나를 안전 상한 없이 끝까지 실행한다. 이 스크립트는 그 대신
app/services/collection_run.py의 CollectionRunner를 사용해 요청 예산과 wall time을
넘기지 않고, 중단되면 checkpoint에서 이어서 실행할 수 있다.

사용법:
    python scripts/run_checkpointed_crawl.py --merchant abcmart --keyword 구두 \
        --max-items 200 --request-budget 20 --max-wall-seconds 600

    # 중단된 실행을 이어서:
    python scripts/run_checkpointed_crawl.py --merchant abcmart --keyword 구두 \
        --max-items 200 --request-budget 20 --resume
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
from pathlib import Path

from app.crawlers import SITE_CRAWLERS
from app.services.collection_run import CollectionRunConfig, CollectionRunner
from app.services.rate_limiter import MerchantRateLimiter, create_redis_backend


def build_parser() -> argparse.ArgumentParser:
    """안전 상한을 명시적으로 입력받는 CLI parser를 만든다."""

    parser = argparse.ArgumentParser(description="요청 예산/checkpoint 대량 수집 실행기")
    parser.add_argument("--merchant", required=True, choices=sorted(SITE_CRAWLERS))
    parser.add_argument("--keyword", required=True)
    parser.add_argument("--max-items", type=int, default=200)
    parser.add_argument("--page-size", type=int, default=50)
    parser.add_argument("--request-budget", type=int, default=20)
    parser.add_argument("--min-interval", type=float, default=1.0)
    parser.add_argument("--max-wall-seconds", type=float, default=1_800.0)
    parser.add_argument("--output-dir", type=Path, default=Path("output/checkpointed"))
    parser.add_argument("--resume", action="store_true")
    parser.add_argument(
        "--rate-limit-per-second",
        type=float,
        default=None,
        help=(
            "판매처 전역 초당 요청 상한. 지정하면 PURCHASE_RESEARCH_REDIS_URL로 연결해 "
            "같은 판매처를 처리하는 다른 process/instance와 상한을 나눠 쓴다."
        ),
    )
    return parser


async def run_from_args(args: argparse.Namespace) -> int:
    """CLI 인자를 실행 설정으로 바꾸고 결과 요약을 출력한다."""

    config = CollectionRunConfig(
        merchant=args.merchant,
        keyword=args.keyword,
        checkpoint_dir=args.output_dir,
        max_items=args.max_items,
        page_size=args.page_size,
        request_budget=args.request_budget,
        min_interval_seconds=args.min_interval,
        max_wall_seconds=args.max_wall_seconds,
        resume=args.resume,
    )
    rate_limiter = None
    if args.rate_limit_per_second is not None:
        backend = create_redis_backend(os.getenv("PURCHASE_RESEARCH_REDIS_URL", ""))
        rate_limiter = MerchantRateLimiter(backend, requests_per_second=args.rate_limit_per_second)

    crawler = SITE_CRAWLERS[args.merchant]()
    products, stats = await CollectionRunner(crawler, rate_limiter=rate_limiter).run(config)

    print(json.dumps(stats.to_dict(), ensure_ascii=False, indent=2))
    print(f"수집 {len(products)}개, 중단 사유: {stats.stop_reason}")
    return 0 if stats.stop_reason not in {"http_401_403", "http_429", "request_failed"} else 2


def main() -> None:
    """명령행 실행 결과를 process exit code로 반환한다."""

    raise SystemExit(asyncio.run(run_from_args(build_parser().parse_args())))


if __name__ == "__main__":
    main()
