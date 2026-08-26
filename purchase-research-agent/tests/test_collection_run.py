"""요청 예산/wall time 상한과 checkpoint/resume 대량 수집 실행기를 검증한다."""

from __future__ import annotations

import json
import tempfile
import time
import unittest
from pathlib import Path
from typing import Any

from app.services.collection_run import CollectionRunConfig, CollectionRunner
from app.services.rate_limiter import RateLimitTimeoutError


class FakeRateLimiter:
    """실제 Redis 없이 acquire 호출 순서와 대상 판매처만 기록하는 대역이다."""

    def __init__(self, *, fail_after: int | None = None):
        self.calls: list[str] = []
        self._fail_after = fail_after

    async def acquire(self, merchant: str) -> None:
        self.calls.append(merchant)
        if self._fail_after is not None and len(self.calls) > self._fail_after:
            raise RateLimitTimeoutError(f"{merchant} 전역 rate limit 대기 시간을 초과했습니다")


class FakePageCrawler:
    """호출 순서대로 미리 정해둔 (상품, 오류) 페이지를 반환하는 SiteCrawler 대역이다."""

    def __init__(self, pages: list[tuple[list[dict[str, Any]], list[str]]]):
        self._pages = pages
        self.calls: list[tuple[str, int, int, int | None]] = []

    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict[str, Any]], list[str]]:
        self.calls.append((keyword, max_items, start_page, max_pages))
        index = len(self.calls) - 1
        if index >= len(self._pages):
            return [], []
        return self._pages[index]


def _product(product_id: str) -> dict[str, Any]:
    return {"source_product_id": product_id, "title": product_id}


class SyntheticPageCrawler:
    """실제 판매처 대신 요청한 페이지 번호로 결정적인 상품을 생성하는 대량 수집용 대역이다.

    호출 순서가 아니라 SiteCrawler.crawl에 전달되는 실제 start_page 인자로 상품을 만들어서,
    체크포인트에서 재개했을 때 새 crawler 인스턴스가 이전 실행과 겹치지 않는 뒷 페이지부터
    이어받는지(그리고 이어받아도 내용이 어긋나지 않는지) 검증할 수 있다.
    """

    def __init__(self, *, available_pages: int, fail_on_pages: set[int] | None = None):
        self.available_pages = available_pages
        self._fail_on_pages = fail_on_pages or set()
        self.calls: list[tuple[int, int]] = []

    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict[str, Any]], list[str]]:
        self.calls.append((start_page, max_items))
        if start_page in self._fail_on_pages:
            return [], [f"page {start_page} 예외(재시도 후에도 실패): MERCHANT_TEMPORARY_FAILURE: 원격 서버 오류"]
        if start_page > self.available_pages:
            return [], []
        products = [
            _product(f"item-{start_page:05d}-{i:03d}")
            for i in range(max_items)
        ]
        return products, []


def _make_clock(step: float):
    """호출할 때마다 고정폭으로 증가하는 결정적 fake 단조 시계를 만든다."""

    state = {"value": 0.0}

    def clock() -> float:
        state["value"] += step
        return state["value"]

    return clock


class CollectionRunnerTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.checkpoint_dir = Path(self._tmp.name)

    async def test_target_reached_stops_and_dedups_across_pages(self) -> None:
        crawler = FakePageCrawler([
            ([_product("p1"), _product("p2")], []),
            ([_product("p2"), _product("p3")], []),
        ])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=3,
            request_budget=5,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "target_reached")
        self.assertEqual([p["source_product_id"] for p in products], ["p1", "p2", "p3"])
        self.assertEqual(stats.unique_count, 3)
        self.assertEqual(stats.duplicate_count, 1)
        self.assertEqual(stats.page_fetch_count, 2)

    async def test_rate_limiter_is_acquired_before_every_page_fetch(self) -> None:
        crawler = FakePageCrawler([
            ([_product("p1")], []),
            ([_product("p2")], []),
        ])
        rate_limiter = FakeRateLimiter()
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=2,
            request_budget=5,
            min_interval_seconds=0,
        )

        await CollectionRunner(crawler, rate_limiter=rate_limiter).run(config)

        self.assertEqual(rate_limiter.calls, ["abcmart", "abcmart"])

    async def test_rate_limit_timeout_stops_run_without_counting_page_fetch(self) -> None:
        crawler = FakePageCrawler([([_product("p1")], [])])
        rate_limiter = FakeRateLimiter(fail_after=0)
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=10,
            request_budget=5,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler, rate_limiter=rate_limiter).run(config)

        self.assertEqual(stats.stop_reason, "rate_limit_timeout")
        self.assertEqual(stats.page_fetch_count, 0)
        self.assertEqual(crawler.calls, [])
        self.assertEqual(products, [])

    async def test_request_budget_exhausted_stops_before_target(self) -> None:
        crawler = FakePageCrawler([
            ([_product("p1")], []),
            ([_product("p2")], []),
            ([_product("p3")], []),
            ([_product("p4")], []),
        ])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=3,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "request_budget_exhausted")
        self.assertEqual(stats.page_fetch_count, 3)
        self.assertEqual(len(products), 3)

    async def test_wall_time_exhausted_stops_without_fetching(self) -> None:
        crawler = FakePageCrawler([([_product("p1")], [])])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=100,
            max_wall_seconds=5,
            min_interval_seconds=0,
        )
        clock = _make_clock(step=10.0)

        products, stats = await CollectionRunner(crawler, clock=clock).run(config)

        self.assertEqual(stats.stop_reason, "wall_time_exhausted")
        self.assertEqual(stats.page_fetch_count, 0)
        self.assertEqual(products, [])

    async def test_rate_limit_marker_stops_immediately_without_retry_budget(self) -> None:
        crawler = FakePageCrawler([
            ([], ["page 1 예외(재시도 후에도 실패): MERCHANT_RATE_LIMITED: abcmart 요청 제한 응답을 받았습니다(HTTP 429)"]),
            ([_product("p1")], []),
        ])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=100,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "http_429")
        self.assertEqual(stats.http_429_count, 1)
        self.assertEqual(stats.page_fetch_count, 1)
        self.assertEqual(products, [])

    async def test_access_blocked_marker_stops_immediately(self) -> None:
        crawler = FakePageCrawler([
            ([], ["page 1 요청 실패: MERCHANT_ACCESS_BLOCKED: abcmart 접근이 거부됐습니다(HTTP 403)"]),
        ])
        config = CollectionRunConfig(
            merchant="29cm",
            keyword="운동화",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=100,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "http_401_403")
        self.assertEqual(stats.page_fetch_count, 1)

    async def test_empty_page_without_error_stops_as_no_more_results(self) -> None:
        crawler = FakePageCrawler([([], [])])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=100,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "no_more_results")
        self.assertEqual(stats.error_count, 0)

    async def test_generic_failure_stops_after_one_page(self) -> None:
        crawler = FakePageCrawler([([], ["page 1 예외(재시도 후에도 실패): MERCHANT_TIMEOUT: abcmart 검색 timeout"])])
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=100,
            request_budget=100,
            min_interval_seconds=0,
        )

        products, stats = await CollectionRunner(crawler).run(config)

        self.assertEqual(stats.stop_reason, "request_failed")
        self.assertEqual(stats.page_fetch_count, 1)
        self.assertEqual(stats.error_count, 1)

    async def test_resume_continues_from_checkpoint_without_reprocessing_page_one(self) -> None:
        first_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=2,
            request_budget=5,
            min_interval_seconds=0,
        )
        first_crawler = FakePageCrawler([([_product("p1"), _product("p2")], [])])
        first_products, first_stats = await CollectionRunner(first_crawler).run(first_config)
        self.assertEqual(first_stats.stop_reason, "target_reached")
        self.assertFalse(first_stats.checkpoint_resumed)

        second_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=4,
            request_budget=5,
            min_interval_seconds=0,
            resume=True,
        )
        second_crawler = FakePageCrawler([([_product("p3"), _product("p4")], [])])

        products, stats = await CollectionRunner(second_crawler).run(second_config)

        self.assertTrue(stats.checkpoint_resumed)
        self.assertEqual(second_crawler.calls[0][2], 2, "checkpoint의 next_page(2)부터 재개해야 한다")
        self.assertEqual(
            [p["source_product_id"] for p in products],
            ["p1", "p2", "p3", "p4"],
            "이전 실행 결과와 재개 후 결과가 함께 저장돼야 한다",
        )
        self.assertEqual(stats.stop_reason, "target_reached")

    async def test_resume_rejects_checkpoint_when_sanitized_filename_collides(self) -> None:
        """서로 다른 검색어라도 파일명 안전화 후 같은 checkpoint 경로로 겹치면 거부해야 한다.

        예: "구두!"와 "구두?"는 둘 다 특수문자가 '_'로 치환돼 같은 파일명이 된다.
        파일명만으로는 이 충돌을 막을 수 없으므로 checkpoint 내용의 keyword를 실제
        설정과 대조해 다른 실행의 진행 상태를 이어받지 않도록 방어한다.
        """

        first_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두!",
            checkpoint_dir=self.checkpoint_dir,
            max_items=1,
            request_budget=5,
            min_interval_seconds=0,
        )
        await CollectionRunner(FakePageCrawler([([_product("p1")], [])])).run(first_config)

        colliding_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두?",
            checkpoint_dir=self.checkpoint_dir,
            max_items=1,
            request_budget=5,
            min_interval_seconds=0,
            resume=True,
        )

        with self.assertRaises(ValueError):
            await CollectionRunner(FakePageCrawler([])).run(colliding_config)

    async def test_checkpoint_file_written_atomically_and_is_valid_json(self) -> None:
        config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=1,
            request_budget=5,
            min_interval_seconds=0,
        )
        await CollectionRunner(FakePageCrawler([([_product("p1")], [])])).run(config)

        checkpoint_path = self.checkpoint_dir / "abcmart_구두.checkpoint.json"
        self.assertTrue(checkpoint_path.exists())
        payload = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        self.assertEqual(payload["seen_ids"], ["p1"])
        self.assertEqual(payload["next_page"], 2)
        self.assertFalse(list(self.checkpoint_dir.glob("*.tmp")), "임시 파일이 남아 있으면 안 된다")


class LargeScaleCheckpointResumeTests(unittest.IsolatedAsyncioTestCase):
    """실제 판매처 없이 10,000개 규모에서 request budget 중단과 checkpoint 재개가
    정확히 이어지는지 검증한다. 실사이트에 부하를 주지 않도록 SyntheticPageCrawler로
    페이지 내용을 결정적으로 생성한다(app/services/rate_limiter.py의 MerchantRateLimiter
    같은 실제 네트워크 호출은 전혀 발생하지 않는다)."""

    PAGE_SIZE = 50
    TARGET_ITEMS = 10_000

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.checkpoint_dir = Path(self._tmp.name)

    async def test_reaches_10000_items_across_interrupted_and_resumed_runs(self) -> None:
        total_pages_needed = self.TARGET_ITEMS // self.PAGE_SIZE  # 200
        first_phase_pages = 60  # 3,000개 수집 후 request budget 소진으로 중단됐다고 가정

        first_crawler = SyntheticPageCrawler(available_pages=total_pages_needed)
        first_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=self.TARGET_ITEMS,
            page_size=self.PAGE_SIZE,
            request_budget=first_phase_pages,
            min_interval_seconds=0,
        )
        first_products, first_stats = await CollectionRunner(first_crawler).run(first_config)

        self.assertEqual(first_stats.stop_reason, "request_budget_exhausted")
        self.assertEqual(first_stats.page_fetch_count, first_phase_pages)
        self.assertEqual(len(first_products), first_phase_pages * self.PAGE_SIZE)
        self.assertFalse(first_stats.checkpoint_resumed)

        # 새 process가 checkpoint에서 이어받는 상황을 흉내 내도록 crawler 인스턴스를 새로 만든다.
        second_crawler = SyntheticPageCrawler(available_pages=total_pages_needed)
        second_config = CollectionRunConfig(
            merchant="abcmart",
            keyword="구두",
            checkpoint_dir=self.checkpoint_dir,
            max_items=self.TARGET_ITEMS,
            page_size=self.PAGE_SIZE,
            request_budget=total_pages_needed,  # 남은 140페이지를 넉넉히 감당
            min_interval_seconds=0,
            resume=True,
        )

        started = time.perf_counter()
        products, stats = await CollectionRunner(second_crawler).run(second_config)
        elapsed = time.perf_counter() - started

        self.assertTrue(stats.checkpoint_resumed)
        self.assertEqual(stats.stop_reason, "target_reached")
        self.assertEqual(second_crawler.calls[0][0], first_phase_pages + 1, "checkpoint 다음 페이지부터 재개해야 한다")
        self.assertEqual(len(products), self.TARGET_ITEMS)
        unique_ids = {p["source_product_id"] for p in products}
        self.assertEqual(len(unique_ids), self.TARGET_ITEMS, "10,000개가 중복 없이 모두 고유해야 한다")
        self.assertEqual(stats.duplicate_count, 0)
        self.assertEqual(stats.page_fetch_count, total_pages_needed - first_phase_pages)
        self.assertLess(elapsed, 10.0, "외부 요청 없는 순수 mock 실행은 몇 초 안에 끝나야 한다")

        checkpoint_path = self.checkpoint_dir / "abcmart_구두.checkpoint.json"
        payload = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        self.assertEqual(payload["next_page"], total_pages_needed + 1)
        self.assertEqual(len(payload["seen_ids"]), self.TARGET_ITEMS)

    async def test_request_budget_below_target_leaves_room_for_a_later_resume(self) -> None:
        """중단 시점의 request budget이 남은 목표를 다 채우지 못해도 두 번째 실행에서
        같은 checkpoint로 이어받을 수 있는 상태로 정확히 멈추는지 검증한다."""

        crawler = SyntheticPageCrawler(available_pages=500)
        config = CollectionRunConfig(
            merchant="29cm",
            keyword="운동화",
            checkpoint_dir=self.checkpoint_dir,
            max_items=self.TARGET_ITEMS,
            page_size=self.PAGE_SIZE,
            request_budget=1,  # 극단적으로 낮은 예산으로 매 실행마다 강제 중단시킨다
            min_interval_seconds=0,
        )

        collected: list[dict[str, Any]] = []
        run_count = 0
        while len(collected) < self.TARGET_ITEMS:
            run_count += 1
            config.resume = run_count > 1
            products, stats = await CollectionRunner(SyntheticPageCrawler(available_pages=500)).run(config)
            self.assertEqual(stats.stop_reason, "request_budget_exhausted" if len(products) < self.TARGET_ITEMS else "target_reached")
            collected = products

        self.assertEqual(run_count, self.TARGET_ITEMS // self.PAGE_SIZE, "예산 1페이지짜리 실행을 페이지 수만큼 반복해야 한다")
        self.assertEqual(len({p["source_product_id"] for p in collected}), self.TARGET_ITEMS)


class CollectionRunConfigValidationTests(unittest.TestCase):
    def _base_kwargs(self, **overrides: Any) -> dict[str, Any]:
        kwargs: dict[str, Any] = {
            "merchant": "abcmart",
            "keyword": "구두",
            "checkpoint_dir": Path("."),
        }
        kwargs.update(overrides)
        return kwargs

    def test_rejects_empty_keyword(self) -> None:
        with self.assertRaises(ValueError):
            CollectionRunConfig(**self._base_kwargs(keyword="  ")).validate()

    def test_rejects_max_items_over_limit(self) -> None:
        with self.assertRaises(ValueError):
            CollectionRunConfig(**self._base_kwargs(max_items=10_001)).validate()

    def test_rejects_zero_request_budget(self) -> None:
        with self.assertRaises(ValueError):
            CollectionRunConfig(**self._base_kwargs(request_budget=0)).validate()

    def test_rejects_non_positive_wall_seconds(self) -> None:
        with self.assertRaises(ValueError):
            CollectionRunConfig(**self._base_kwargs(max_wall_seconds=0)).validate()


if __name__ == "__main__":
    unittest.main()
