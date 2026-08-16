"""수동 대량 수집 스크립트용 상세 조회 캐시가 최근 조회한 상품을 정확히 걸러내는지 검증한다."""

from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from app.services.detail_freshness_cache import DetailFreshnessCache


def _product(product_id: str) -> dict:
    return {"source_product_id": product_id, "title": product_id}


class DetailFreshnessCacheTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.cache_path = Path(self._tmp.name) / "cache.json"

    def test_rejects_non_positive_ttl(self) -> None:
        with self.assertRaises(ValueError):
            DetailFreshnessCache(self.cache_path, ttl_hours=0)

    def test_all_products_are_stale_when_cache_is_empty(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)

        stale, fresh = cache.split_stale("abcmart", [_product("p1"), _product("p2")])

        self.assertEqual([p["source_product_id"] for p in stale], ["p1", "p2"])
        self.assertEqual(fresh, [])

    def test_recently_fetched_product_is_treated_as_fresh(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)
        cache.mark_fetched("abcmart", [_product("p1")])

        stale, fresh = cache.split_stale("abcmart", [_product("p1"), _product("p2")])

        self.assertEqual([p["source_product_id"] for p in stale], ["p2"])
        self.assertEqual([p["source_product_id"] for p in fresh], ["p1"])

    def test_expired_entry_is_treated_as_stale_again(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=1)
        stale_timestamp = (datetime.now(timezone.utc) - timedelta(hours=2)).isoformat()
        self.cache_path.write_text(
            json.dumps({"abcmart": {"p1": stale_timestamp}}), encoding="utf-8"
        )
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=1)

        stale, fresh = cache.split_stale("abcmart", [_product("p1")])

        self.assertEqual([p["source_product_id"] for p in stale], ["p1"])
        self.assertEqual(fresh, [])

    def test_cache_is_scoped_per_merchant(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)
        cache.mark_fetched("abcmart", [_product("p1")])

        stale, fresh = cache.split_stale("29cm", [_product("p1")])

        self.assertEqual([p["source_product_id"] for p in stale], ["p1"], "다른 판매처의 캐시는 적용되면 안 된다")
        self.assertEqual(fresh, [])

    def test_product_without_id_is_always_stale(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)

        stale, fresh = cache.split_stale("abcmart", [{"title": "id 없는 상품"}])

        self.assertEqual(len(stale), 1)
        self.assertEqual(fresh, [])

    def test_mark_fetched_persists_across_cache_instances(self) -> None:
        first = DetailFreshnessCache(self.cache_path, ttl_hours=24)
        first.mark_fetched("abcmart", [_product("p1")])

        second = DetailFreshnessCache(self.cache_path, ttl_hours=24)
        stale, fresh = second.split_stale("abcmart", [_product("p1")])

        self.assertEqual(stale, [])
        self.assertEqual([p["source_product_id"] for p in fresh], ["p1"])

    def test_save_is_atomic_and_leaves_no_tmp_file(self) -> None:
        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)

        cache.mark_fetched("abcmart", [_product("p1")])

        self.assertTrue(self.cache_path.exists())
        self.assertFalse(list(self.cache_path.parent.glob("*.tmp")))

    def test_corrupted_cache_file_is_treated_as_empty(self) -> None:
        self.cache_path.write_text("not valid json", encoding="utf-8")

        cache = DetailFreshnessCache(self.cache_path, ttl_hours=24)
        stale, fresh = cache.split_stale("abcmart", [_product("p1")])

        self.assertEqual([p["source_product_id"] for p in stale], ["p1"])
        self.assertEqual(fresh, [])


if __name__ == "__main__":
    unittest.main()
