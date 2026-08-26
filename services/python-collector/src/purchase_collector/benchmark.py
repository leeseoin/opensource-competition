"""저장된 동일 JSON fixture의 Python parser/normalizer 성능을 측정한다."""

from __future__ import annotations

import argparse
import json
import resource
import sys
import time
from pathlib import Path
from typing import Any, Callable

from .contract import repository_root, validate_product
from .merchants.abcmart import parse_page_payload as parse_abcmart_page
from .merchants.twentyninecm import parse_page_payload as parse_29cm_page
from .models import PageResult


def fixture_path(merchant: str) -> Path:
    """판매처별 공통 Go/Python 저장 fixture 경로를 반환한다.

    Args:
        merchant: `abcmart` 또는 `29cm` 판매처 식별자다.

    Returns:
        저장소 안 JSON fixture의 절대 경로다.

    Raises:
        ValueError: 지원하지 않는 판매처가 입력된 경우다.
    """

    relative = {
        "abcmart": Path("abcmart/search-products.json"),
        "29cm": Path("twentyninecm/search-items.json"),
    }.get(merchant)
    if relative is None:
        raise ValueError("merchant는 abcmart 또는 29cm여야 합니다")
    return repository_root() / "services" / "collector" / "testdata" / relative


def parser_for(merchant: str) -> Callable[[dict[str, Any]], PageResult]:
    """판매처 JSON 객체를 공통 상품으로 바꿀 순수 parser 함수를 반환한다."""

    if merchant == "abcmart":
        return lambda payload: parse_abcmart_page(payload, 1)
    if merchant == "29cm":
        return parse_29cm_page
    raise ValueError("merchant는 abcmart 또는 29cm여야 합니다")


def run_benchmark(merchant: str, iterations: int, warmup: int) -> dict[str, Any]:
    """JSON decode/판매처 변환/Contract 검증을 반복하고 성능 지표를 반환한다.

    Args:
        merchant: 측정할 판매처 식별자다.
        iterations: 실제 측정 반복 횟수다.
        warmup: 측정 전에 실행할 준비 반복 횟수다.

    Returns:
        반복 수, 상품 처리량, wall/CPU 시간과 최대 메모리다.

    Raises:
        ValueError: 반복 횟수가 올바르지 않거나 Contract 검증이 실패한 경우다.
    """

    if iterations < 1 or warmup < 0:
        raise ValueError("iterations는 1 이상이고 warmup은 0 이상이어야 합니다")
    raw = fixture_path(merchant).read_bytes()
    parse_page = parser_for(merchant)

    def execute_once() -> int:
        """fixture를 JSON decode하고 정규화한 모든 상품의 Contract를 검증한다."""

        payload = json.loads(raw)
        result = parse_page(payload)
        for product in result.products:
            issues = validate_product(product)
            if issues:
                raise ValueError(f"Contract 검증 실패: {'; '.join(issues)}")
        return len(result.products)

    for _ in range(warmup):
        execute_once()
    started_wall = time.perf_counter()
    started_cpu = time.process_time()
    products_per_iteration = 0
    for _ in range(iterations):
        products_per_iteration = execute_once()
    wall_seconds = time.perf_counter() - started_wall
    cpu_seconds = time.process_time() - started_cpu
    processed_products = products_per_iteration * iterations
    peak_memory = int(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss)
    peak_memory_kib = peak_memory // 1024 if sys.platform == "darwin" else peak_memory
    return {
        "language": "python",
        "merchant": merchant,
        "fixture_bytes": len(raw),
        "iterations": iterations,
        "warmup_iterations": warmup,
        "products_per_iteration": products_per_iteration,
        "processed_products": processed_products,
        "wall_seconds": round(wall_seconds, 6),
        "cpu_seconds": round(cpu_seconds, 6),
        "products_per_wall_second": round(processed_products / wall_seconds, 3),
        "peak_memory_kib": peak_memory_kib,
    }


def build_parser() -> argparse.ArgumentParser:
    """parser benchmark의 판매처와 반복 횟수 인자를 정의한다."""

    parser = argparse.ArgumentParser(description="Python 저장 fixture parser benchmark")
    parser.add_argument("--merchant", required=True, choices=("abcmart", "29cm"))
    parser.add_argument("--iterations", type=int, default=1_000)
    parser.add_argument("--warmup", type=int, default=100)
    return parser


def main() -> None:
    """명령행 인자로 benchmark를 실행하고 재사용 가능한 JSON을 출력한다."""

    args = build_parser().parse_args()
    print(json.dumps(run_benchmark(args.merchant, args.iterations, args.warmup), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
