"""대량 수집 실행기의 중복 제거/checkpoint/안전 중단을 검증한다."""

from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

import httpx

from purchase_collector.contract import count_missing_fields, repository_root
from purchase_collector.merchants.base import MerchantAdapter
from purchase_collector.models import CollectionConfig, MerchantRequestError, PageResult
from purchase_collector.runner import CollectionRunner


def example_products() -> list[dict]:
    """공통 계약을 통과하는 작은 상품 fixture 목록을 반환한다."""

    path = (
        repository_root()
        / "contracts"
        / "collector"
        / "unified"
        / "examples"
        / "unified_구두_top20_20260803_002024.json"
    )
    return json.loads(path.read_text(encoding="utf-8"))[:3]


class FakeAdapter(MerchantAdapter):
    """테스트가 지정한 페이지 결과 또는 오류를 순서대로 반환한다."""

    merchant = "abcmart"

    def __init__(self, results: dict[int, PageResult | Exception]):
        """페이지 번호별 결과를 저장하고 실제 호출 페이지를 기록한다."""

        self.results = results
        self.pages: list[int] = []

    async def fetch_page(
        self,
        client: httpx.AsyncClient,
        query: str,
        page: int,
        page_size: int,
    ) -> PageResult:
        """요청한 페이지의 fixture 결과를 반환하거나 지정 오류를 발생시킨다."""

        del client, query, page_size
        self.pages.append(page)
        result = self.results[page]
        if isinstance(result, Exception):
            raise result
        return result


class CollectionRunnerTests(unittest.IsolatedAsyncioTestCase):
    """외부 요청 없이 실행기의 원자 동작을 검증한다."""

    async def test_deduplicates_and_reaches_target(self) -> None:
        """페이지 사이 중복을 제거하고 고유 상품 목표에서 중단하는지 검증한다."""

        products = example_products()
        adapter = FakeAdapter(
            {
                1: PageResult([products[0], products[1]], True, 10),
                2: PageResult([products[1], products[2]], False, 10),
            }
        )
        with tempfile.TemporaryDirectory() as directory:
            config = CollectionConfig(
                merchant="abcmart",
                queries=["구두"],
                output_dir=Path(directory),
                max_items=3,
                request_budget=3,
                min_interval_seconds=0,
            )
            async with httpx.AsyncClient() as client:
                stats = await CollectionRunner(adapter).run(config, client)
            with gzip.open(Path(directory) / "products.ndjson.gz", "rt", encoding="utf-8") as source:
                saved = [json.loads(line) for line in source]

        self.assertEqual(3, stats.unique_count)
        self.assertEqual(1, stats.duplicate_count)
        self.assertEqual(3, stats.contract_pass_count)
        self.assertEqual(0, stats.skipped_after_target_count)
        self.assertEqual("target_reached", stats.stop_reason)
        self.assertEqual(3, len(saved))

    async def test_counts_products_skipped_after_target(self) -> None:
        """마지막 응답에서 목표 뒤에 남은 상품 수를 별도 지표로 기록하는지 검증한다."""

        products = example_products()
        adapter = FakeAdapter({1: PageResult(products, False, 3)})
        with tempfile.TemporaryDirectory() as directory:
            config = CollectionConfig(
                merchant="abcmart",
                queries=["구두"],
                output_dir=Path(directory),
                max_items=2,
                request_budget=1,
                min_interval_seconds=0,
            )
            async with httpx.AsyncClient() as client:
                stats = await CollectionRunner(adapter).run(config, client)

        self.assertEqual(3, stats.received_count)
        self.assertEqual(2, stats.unique_count)
        self.assertEqual(1, stats.skipped_after_target_count)

    async def test_resume_continues_from_checkpoint(self) -> None:
        """요청 예산으로 중단한 작업이 다음 페이지부터 재개되는지 검증한다."""

        products = example_products()
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            first_adapter = FakeAdapter({1: PageResult(products[:2], True, 10)})
            first = CollectionConfig(
                merchant="abcmart",
                queries=["구두"],
                output_dir=output_dir,
                max_items=3,
                request_budget=1,
                min_interval_seconds=0,
            )
            async with httpx.AsyncClient() as client:
                first_stats = await CollectionRunner(first_adapter).run(first, client)

            second_adapter = FakeAdapter({2: PageResult([products[2]], False, 10)})
            second = CollectionConfig(
                merchant="abcmart",
                queries=["구두"],
                output_dir=output_dir,
                max_items=3,
                request_budget=1,
                min_interval_seconds=0,
                resume=True,
            )
            async with httpx.AsyncClient() as client:
                second_stats = await CollectionRunner(second_adapter).run(second, client)
            with gzip.open(output_dir / "products.ndjson.gz", "rt", encoding="utf-8") as source:
                saved = [json.loads(line) for line in source]

        self.assertEqual("request_budget_exhausted", first_stats.stop_reason)
        self.assertEqual([2], second_adapter.pages)
        self.assertTrue(second_stats.checkpoint_resumed)
        self.assertEqual(3, second_stats.unique_count)
        self.assertEqual(3, second_stats.contract_pass_count)
        self.assertEqual(
            sum(count_missing_fields(product) for product in products),
            second_stats.missing_field_count,
        )
        self.assertEqual(3, len(saved))

    async def test_http_429_stops_without_retry(self) -> None:
        """429 응답을 재시도하거나 우회하지 않고 즉시 중단하는지 검증한다."""

        adapter = FakeAdapter(
            {1: MerchantRequestError("요청 제한", status_code=429, retryable=True)}
        )
        with tempfile.TemporaryDirectory() as directory:
            config = CollectionConfig(
                merchant="abcmart",
                queries=["구두"],
                output_dir=Path(directory),
                max_items=100,
                request_budget=10,
                min_interval_seconds=0,
            )
            async with httpx.AsyncClient() as client:
                stats = await CollectionRunner(adapter).run(config, client)

        self.assertEqual("http_429", stats.stop_reason)
        self.assertEqual(1, stats.request_count)
        self.assertEqual(1, stats.http_429_count)


if __name__ == "__main__":
    unittest.main()
