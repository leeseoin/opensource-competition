"""판매처별 전역 rate limit(MerchantRateLimiter)과 Redis token bucket 해석을 검증한다."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock

from app.services.rate_limiter import (
    MerchantRateLimiter,
    RateLimitTimeoutError,
    RedisTokenBucketBackend,
    create_redis_backend,
)


class FakeTokenBucketBackend:
    """호출 순서대로 미리 정한 허용 여부를 반환하는 backend 대역이다."""

    def __init__(self, allow_sequence: list[bool]):
        self._allow_sequence = allow_sequence
        self.calls: list[tuple[str, float, float, float]] = []

    async def try_acquire(self, key: str, capacity: float, refill_per_second: float, now: float) -> bool:
        self.calls.append((key, capacity, refill_per_second, now))
        index = len(self.calls) - 1
        if index >= len(self._allow_sequence):
            return self._allow_sequence[-1]
        return self._allow_sequence[index]


def _make_clock(step: float):
    """호출할 때마다 고정폭으로 증가하는 결정적 fake 단조 시계를 만든다."""

    state = {"value": 0.0}

    def clock() -> float:
        state["value"] += step
        return state["value"]

    return clock


class MerchantRateLimiterTests(unittest.IsolatedAsyncioTestCase):
    async def test_acquire_returns_immediately_when_backend_allows(self) -> None:
        backend = FakeTokenBucketBackend([True])
        limiter = MerchantRateLimiter(backend, requests_per_second=2.0)

        await limiter.acquire("abcmart")

        self.assertEqual(len(backend.calls), 1)
        key, capacity, refill_per_second, _now = backend.calls[0]
        self.assertEqual(key, "purchase-research:rate-limit:abcmart")
        self.assertEqual(capacity, 2.0)
        self.assertEqual(refill_per_second, 2.0)

    async def test_acquire_retries_until_backend_allows(self) -> None:
        backend = FakeTokenBucketBackend([False, False, True])
        limiter = MerchantRateLimiter(backend, requests_per_second=1.0, poll_interval_seconds=0.01)

        await limiter.acquire("29cm")

        self.assertEqual(len(backend.calls), 3)

    async def test_acquire_raises_timeout_when_backend_never_allows(self) -> None:
        backend = FakeTokenBucketBackend([False])
        clock = _make_clock(step=1000.0)
        limiter = MerchantRateLimiter(
            backend,
            requests_per_second=1.0,
            poll_interval_seconds=0.01,
            max_wait_seconds=5.0,
            clock=clock,
        )

        with self.assertRaises(RateLimitTimeoutError):
            await limiter.acquire("abcmart")

    async def test_different_merchants_use_independent_keys(self) -> None:
        backend = FakeTokenBucketBackend([True, True])
        limiter = MerchantRateLimiter(backend, requests_per_second=1.0)

        await limiter.acquire("abcmart")
        await limiter.acquire("29cm")

        keys = [call[0] for call in backend.calls]
        self.assertEqual(keys, [
            "purchase-research:rate-limit:abcmart",
            "purchase-research:rate-limit:29cm",
        ])

    async def test_burst_defaults_to_requests_per_second_when_not_set(self) -> None:
        backend = FakeTokenBucketBackend([True])
        limiter = MerchantRateLimiter(backend, requests_per_second=3.0)

        await limiter.acquire("abcmart")

        _key, capacity, _refill, _now = backend.calls[0]
        self.assertEqual(capacity, 3.0)

    async def test_explicit_burst_overrides_default(self) -> None:
        backend = FakeTokenBucketBackend([True])
        limiter = MerchantRateLimiter(backend, requests_per_second=1.0, burst=5.0)

        await limiter.acquire("abcmart")

        _key, capacity, _refill, _now = backend.calls[0]
        self.assertEqual(capacity, 5.0)


class MerchantRateLimiterConfigTests(unittest.TestCase):
    def test_rejects_non_positive_requests_per_second(self) -> None:
        with self.assertRaises(ValueError):
            MerchantRateLimiter(FakeTokenBucketBackend([]), requests_per_second=0)

    def test_rejects_non_positive_poll_interval(self) -> None:
        with self.assertRaises(ValueError):
            MerchantRateLimiter(FakeTokenBucketBackend([]), requests_per_second=1.0, poll_interval_seconds=0)

    def test_rejects_non_positive_max_wait(self) -> None:
        with self.assertRaises(ValueError):
            MerchantRateLimiter(FakeTokenBucketBackend([]), requests_per_second=1.0, max_wait_seconds=0)


class RedisTokenBucketBackendTests(unittest.IsolatedAsyncioTestCase):
    """실제 Redis 없이, script 반환값을 backend가 올바르게 해석하는지만 검증한다."""

    async def test_try_acquire_true_when_script_allows(self) -> None:
        script = AsyncMock(return_value=[1, "3.5"])
        client = MagicMock()
        client.register_script = MagicMock(return_value=script)

        backend = RedisTokenBucketBackend(client)
        allowed = await backend.try_acquire("purchase-research:rate-limit:abcmart", 5.0, 1.0, 100.0)

        self.assertTrue(allowed)
        script.assert_awaited_once_with(
            keys=["purchase-research:rate-limit:abcmart"], args=[5.0, 1.0, 100.0, 1]
        )

    async def test_try_acquire_false_when_script_denies(self) -> None:
        script = AsyncMock(return_value=[0, "0.2"])
        client = MagicMock()
        client.register_script = MagicMock(return_value=script)

        backend = RedisTokenBucketBackend(client)
        allowed = await backend.try_acquire("purchase-research:rate-limit:abcmart", 5.0, 1.0, 100.0)

        self.assertFalse(allowed)


class CreateRedisBackendTests(unittest.TestCase):
    def test_rejects_empty_url(self) -> None:
        with self.assertRaises(ValueError):
            create_redis_backend("")


if __name__ == "__main__":
    unittest.main()
