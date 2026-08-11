"""판매처별 전역 rate limit을 process/instance 경계를 넘어 Redis로 공유한다.

CollectionRunner의 min_interval_seconds와 각 SiteCrawler의 jittered_sleep은 같은
process 안에서만 요청 간격을 지킨다. RabbitMQ Worker를 여러 instance로 띄우거나
scripts/run_queue_crawl.py처럼 여러 OS process가 동시에 같은 판매처를 수집하면,
로컬 대기만으로는 판매처 입장에서 본 초당 요청 수를 통제하지 못한다
(docs/reports/2026-08-09_Python_크롤러_기능_검증과_통합_비교표.md의 지적).

이 모듈은 Redis에 저장한 token bucket 하나를 판매처별 전역 상한으로 공유해,
같은 판매처를 향한 모든 process/instance의 요청이 하나의 상한을 나눠 쓰게 한다.
"""

from __future__ import annotations

import asyncio
import os
import time
from typing import Any, Callable, Protocol

# KEYS[1] = bucket key, ARGV = [capacity, refill_per_second, now, requested]
# 토큰을 시간 경과만큼 채운 뒤 요청량만큼 원자적으로 차감한다. Redis는 단일
# 스레드로 스크립트를 실행하므로 여러 process가 동시에 호출해도 읽기-쓰기 사이에
# 다른 process가 끼어들 수 없다(레이스 없이 공유 가능한 이유).
_TOKEN_BUCKET_SCRIPT = """
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_second = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'updated_at')
local tokens = tonumber(bucket[1])
local updated_at = tonumber(bucket[2])
if tokens == nil then
  tokens = capacity
  updated_at = now
end

local elapsed = now - updated_at
if elapsed > 0 then
  tokens = math.min(capacity, tokens + elapsed * refill_per_second)
end

local allowed = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'updated_at', now)
local ttl = math.ceil(capacity / refill_per_second) + 1
redis.call('EXPIRE', key, ttl)

return {allowed, tostring(tokens)}
"""


class RateLimitTimeoutError(RuntimeError):
    """전역 rate limit 대기가 허용 시간 안에 끝나지 않은 경우다."""


class TokenBucketBackend(Protocol):
    """Redis 등 외부 저장소에서 원자적으로 토큰을 차감하는 최소 계약이다."""

    async def try_acquire(self, key: str, capacity: float, refill_per_second: float, now: float) -> bool:
        """토큰이 충분하면 즉시 차감하고 참을 반환한다."""


class RedisTokenBucketBackend:
    """redis.asyncio client와 token bucket Lua script로 원자적 차감을 구현한다."""

    def __init__(self, client: Any) -> None:
        """이미 연결 설정된 redis.asyncio.Redis client를 보관한다."""

        self._client = client
        self._script = client.register_script(_TOKEN_BUCKET_SCRIPT)

    async def try_acquire(self, key: str, capacity: float, refill_per_second: float, now: float) -> bool:
        """token bucket Lua script를 실행해 즉시 차감 가능 여부를 반환한다."""

        allowed, _tokens = await self._script(keys=[key], args=[capacity, refill_per_second, now, 1])
        return bool(int(allowed))


def create_redis_backend(url: str) -> RedisTokenBucketBackend:
    """AMQP client와 동일한 방식으로 URL 하나로 Redis 연결을 만든다.

    Args:
        url: ``redis://[:password@]host:port/db`` 형식의 연결 URL이다.

    Raises:
        ValueError: URL이 비어 있는 경우다.
    """

    if not url:
        raise ValueError("PURCHASE_RESEARCH_REDIS_URL이 비어 있습니다")
    import redis.asyncio as redis_asyncio  # 무거운 의존성을 실제 사용 시점까지 지연 import한다

    client = redis_asyncio.from_url(url, decode_responses=True)
    return RedisTokenBucketBackend(client)


def create_rate_limiter_from_env() -> "MerchantRateLimiter | None":
    """PURCHASE_RESEARCH_REDIS_URL이 설정된 경우에만 전역 rate limiter를 만든다.

    FastAPI lifespan(app/main.py)과 독립 Worker CLI(scripts/collection_worker.py)가
    같은 조건으로 rate limiter를 켜고 끌 수 있도록 구성 로직을 한 곳에 모은다.

    Returns:
        Redis 연결 URL이 없으면 ``None``이며, 이 경우 호출자는 이 실행이
        가진 로컬 속도 제어(prefetch, min_interval 등)만으로 동작해야 한다.
    """

    redis_url = os.getenv("PURCHASE_RESEARCH_REDIS_URL", "")
    if not redis_url:
        return None
    requests_per_second = float(os.getenv("PURCHASE_RESEARCH_RATE_LIMIT_PER_SECOND", "1.0"))
    backend = create_redis_backend(redis_url)
    return MerchantRateLimiter(backend, requests_per_second=requests_per_second)


class MerchantRateLimiter:
    """판매처별 전역 token bucket을 판매처가 다르면 서로 간섭하지 않게 관리한다."""

    def __init__(
        self,
        backend: TokenBucketBackend,
        *,
        requests_per_second: float = 1.0,
        burst: float | None = None,
        poll_interval_seconds: float = 0.2,
        max_wait_seconds: float = 30.0,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        """전역 상한, 대기 정책과 테스트용 시계를 설정한다.

        Args:
            backend: 판매처별 token bucket을 원자적으로 차감하는 저장소다.
            requests_per_second: 판매처 하나가 전체 process/instance를 합쳐 허용하는 초당 요청 수다.
            burst: 순간적으로 허용할 최대 토큰 수이며 없으면 requests_per_second와 같다.
            poll_interval_seconds: 토큰이 없을 때 재시도 간격이다.
            max_wait_seconds: 토큰을 기다릴 최대 시간이며 넘기면 예외를 던진다.
            clock: 대기 시간 계산에 사용할 단조 시계다.

        Raises:
            ValueError: 상한이나 대기 설정이 0 이하인 경우다.
        """

        if requests_per_second <= 0:
            raise ValueError("requests_per_second는 0보다 커야 합니다")
        if poll_interval_seconds <= 0:
            raise ValueError("poll_interval_seconds는 0보다 커야 합니다")
        if max_wait_seconds <= 0:
            raise ValueError("max_wait_seconds는 0보다 커야 합니다")
        self._backend = backend
        self._requests_per_second = requests_per_second
        self._burst = burst if burst is not None else requests_per_second
        self._poll_interval_seconds = poll_interval_seconds
        self._max_wait_seconds = max_wait_seconds
        self._clock = clock

    async def acquire(self, merchant: str) -> None:
        """판매처 전역 토큰을 하나 확보할 때까지 대기한다.

        Args:
            merchant: 요청을 보낼 대상 판매처 식별자다.

        Raises:
            RateLimitTimeoutError: max_wait_seconds 안에 토큰을 확보하지 못한 경우다.
        """

        key = f"purchase-research:rate-limit:{merchant}"
        started = self._clock()
        while True:
            allowed = await self._backend.try_acquire(key, self._burst, self._requests_per_second, time.time())
            if allowed:
                return
            if self._clock() - started >= self._max_wait_seconds:
                raise RateLimitTimeoutError(
                    f"{merchant} 전역 rate limit 대기가 {self._max_wait_seconds}초를 초과했습니다"
                )
            await asyncio.sleep(self._poll_interval_seconds)


__all__ = [
    "MerchantRateLimiter",
    "RateLimitTimeoutError",
    "RedisTokenBucketBackend",
    "TokenBucketBackend",
    "create_redis_backend",
]
