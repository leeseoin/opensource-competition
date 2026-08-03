"""Python Collector의 단계별 실수집 명령을 제공한다."""

from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path

from .models import CollectionConfig
from .runner import CollectionRunner


def build_parser() -> argparse.ArgumentParser:
    """안전 상한을 명시적으로 입력받는 CLI parser를 만든다."""

    parser = argparse.ArgumentParser(description="Python/Go 비교용 공개 상품 수집기")
    parser.add_argument("--merchant", required=True, choices=("abcmart", "29cm"))
    parser.add_argument("--query", action="append", required=True, dest="queries")
    parser.add_argument("--max-items", type=int, default=100)
    parser.add_argument("--page-size", type=int, default=50)
    parser.add_argument("--request-budget", type=int, default=10)
    parser.add_argument("--min-interval", type=float, default=1.0)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--max-retries", type=int, default=1)
    parser.add_argument("--max-wall-seconds", type=float, default=3600.0)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--resume", action="store_true")
    return parser


async def run_from_args(args: argparse.Namespace) -> int:
    """CLI 인자를 수집 설정으로 바꾸고 결과 요약을 출력한다."""

    config = CollectionConfig(
        merchant=args.merchant,
        queries=args.queries,
        output_dir=args.output_dir,
        max_items=args.max_items,
        page_size=args.page_size,
        request_budget=args.request_budget,
        min_interval_seconds=args.min_interval,
        timeout_seconds=args.timeout,
        max_retries=args.max_retries,
        max_wall_seconds=args.max_wall_seconds,
        resume=args.resume,
    )
    stats = await CollectionRunner().run(config)
    print(json.dumps(stats.to_dict(), ensure_ascii=False, indent=2))
    return 0 if stats.contract_fail_count == 0 and stats.stop_reason not in {"http_401", "http_403", "http_429"} else 2


def main() -> None:
    """명령행 실행 결과를 process exit code로 반환한다."""

    raise SystemExit(asyncio.run(run_from_args(build_parser().parse_args())))


if __name__ == "__main__":
    main()
