"""요청 예산/시간 상한과 checkpoint/resume으로 대량 수집을 안전하게 실행한다.

Queue로 들어오는 CollectionTask 한 건은 이미 페이지 하나·상품 최대 50개로 작아서
``CollectionTaskProcessor``의 30초 timeout과 Queue retry/DLQ만으로 충분하다. 반면
스크립트나 수동 대량 수집처럼 한 (판매처, 검색어)를 여러 페이지에 걸쳐 계속
수집하는 경로는 페이지 수를 미리 알 수 없어 별도의 안전 상한이 필요하다. 이
모듈은 그 대량 수집 경로 전용이며 Queue Worker 경로는 건드리지 않는다.
"""

from __future__ import annotations

import json
import os
import re
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Protocol

from app.crawlers.base import jittered_sleep
from app.services.rate_limiter import MerchantRateLimiter, RateLimitTimeoutError

# app/crawlers/access_safety.py의 MerchantAccessError가 만드는 오류 코드다. SiteCrawler는
# 판매처 요청 중 발생한 예외를 절대 밖으로 던지지 않고 안전한 문자열로만 돌려주므로
# (예: abcmart/crawler.py의 재시도 루프), 즉시 중단 여부는 이 문자열을 검사해 판단한다.
_RATE_LIMIT_MARKER = "MERCHANT_RATE_LIMITED"
_ACCESS_BLOCKED_MARKER = "MERCHANT_ACCESS_BLOCKED"

_UNSAFE_FILENAME_CHARS = re.compile(r"[^A-Za-z0-9가-힣_-]+")


class PageCrawler(Protocol):
    """한 페이지 분량을 수집하는 SiteCrawler의 최소 계약이다."""

    async def crawl(
        self,
        keyword: str,
        max_items: int,
        *,
        start_page: int = 1,
        max_pages: int | None = None,
    ) -> tuple[list[dict[str, Any]], list[str]]:
        """지정한 페이지 하나 분량의 상품과 수집 경고를 반환한다."""


@dataclass(slots=True)
class CollectionRunConfig:
    """한 판매처/검색어 대량 수집의 안전 상한과 checkpoint 저장 위치를 정의한다.

    Args:
        merchant: 대상 판매처 식별자다.
        keyword: 판매처에 전달할 검색어다.
        checkpoint_dir: checkpoint와 누적 상품 파일을 저장할 디렉터리다.
        page_size: 페이지 하나에 요청할 최대 상품 수다.
        max_items: 이번 실행에서 모을 고유 상품 상한이다.
        request_budget: 허용할 최대 페이지 조회 시도 횟수다. 판매처별로 페이지
            하나가 내부적으로 여러 HTTP 요청(JSON 조회 + HTML 교차 검증 등)을
            포함할 수 있어 실제 HTTP 요청 수가 아니라 SiteCrawler가 노출하는
            페이지 단위로 센다.
        min_interval_seconds: 페이지 사이 최소 대기 초다.
        max_wall_seconds: 이번 실행이 사용할 수 있는 총 시간 상한(초)이다.
        resume: 참이면 기존 checkpoint에서 이어서 실행한다.
    """

    merchant: str
    keyword: str
    checkpoint_dir: Path
    page_size: int = 50
    max_items: int = 500
    request_budget: int = 20
    min_interval_seconds: float = 1.0
    max_wall_seconds: float = 3_600.0
    resume: bool = False

    def validate(self) -> None:
        """설정이 대량 수집에 필요한 안전 상한 안에 있는지 검사한다.

        Raises:
            ValueError: 판매처/검색어/수량/시간 설정이 허용 범위를 벗어난 경우다.
        """

        if not self.merchant.strip():
            raise ValueError("merchant가 비어 있습니다")
        if not self.keyword.strip():
            raise ValueError("keyword가 비어 있습니다")
        if not 1 <= self.max_items <= 10_000:
            raise ValueError("max_items는 1 이상 10000 이하여야 합니다")
        if not 1 <= self.page_size <= 100:
            raise ValueError("page_size는 1 이상 100 이하여야 합니다")
        if self.request_budget < 1:
            raise ValueError("request_budget은 1 이상이어야 합니다")
        if self.min_interval_seconds < 0:
            raise ValueError("min_interval_seconds는 0 이상이어야 합니다")
        if self.max_wall_seconds <= 0:
            raise ValueError("max_wall_seconds는 0보다 커야 합니다")


@dataclass(slots=True)
class CollectionRunStats:
    """재현 가능한 대량 수집 실행 결과와 중단 사유를 기록한다."""

    merchant: str
    keyword: str
    target_count: int
    unique_count: int = 0
    duplicate_count: int = 0
    page_fetch_count: int = 0
    error_count: int = 0
    http_429_count: int = 0
    wall_seconds: float = 0.0
    stop_reason: str = ""
    checkpoint_resumed: bool = False
    errors: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        """요약 파일에 저장할 사전으로 변환한다."""

        return asdict(self)

    def _remember_error(self, message: str) -> None:
        """요약이 과도하게 커지지 않도록 최근 오류를 최대 20개만 보관한다."""

        self.errors.append(message)
        if len(self.errors) > 20:
            del self.errors[0]


@dataclass(slots=True)
class _Checkpoint:
    """중단 후 이어서 실행할 다음 페이지와 중복 제거 상태를 보관한다."""

    merchant: str
    keyword: str
    next_page: int
    seen_ids: list[str]


class CollectionRunner:
    """한 판매처 검색어를 안전 상한 안에서 순차 수집하고 재개 가능하게 저장한다."""

    def __init__(
        self,
        crawler: PageCrawler,
        *,
        clock: Any = time.monotonic,
        rate_limiter: MerchantRateLimiter | None = None,
    ):
        """페이지 단위 크롤러, 테스트용 시계와 선택적 전역 rate limiter를 보관한다.

        Args:
            crawler: 페이지 하나씩 조회할 수 있는 SiteCrawler 계약 구현체다.
            clock: wall time 계산에 사용할 단조 시계이며 기본값은 ``time.monotonic``이다.
            rate_limiter: process/instance 경계를 넘어 판매처 전역 요청 속도를 제한할
                MerchantRateLimiter다. 없으면 이 실행의 min_interval_seconds/request_budget
                로만 속도를 제어한다(기존 동작과 동일).
        """

        self._crawler = crawler
        self._clock = clock
        self._rate_limiter = rate_limiter

    async def run(self, config: CollectionRunConfig) -> tuple[list[dict[str, Any]], CollectionRunStats]:
        """목표 수량 또는 안전 상한에 도달할 때까지 상품을 순차 수집한다.

        Args:
            config: 판매처/검색어/안전 상한과 checkpoint 저장 위치다.

        Returns:
            수집한 상품(최대 max_items개)과 실행 지표다.
        """

        config.validate()
        config.checkpoint_dir.mkdir(parents=True, exist_ok=True)
        checkpoint_path = self._checkpoint_path(config)
        result_path = self._result_path(config)

        checkpoint = self._load_checkpoint(config, checkpoint_path)
        seen: set[str] = set(checkpoint.seen_ids)
        products = self._load_products(result_path) if config.resume else []

        stats = CollectionRunStats(
            merchant=config.merchant,
            keyword=config.keyword,
            target_count=config.max_items,
            unique_count=len(seen),
            checkpoint_resumed=bool(config.resume and seen),
        )

        started = self._clock()
        page = checkpoint.next_page
        while True:
            if len(seen) >= config.max_items:
                stats.stop_reason = "target_reached"
                break
            if stats.page_fetch_count >= config.request_budget:
                stats.stop_reason = "request_budget_exhausted"
                break
            if self._clock() - started >= config.max_wall_seconds:
                stats.stop_reason = "wall_time_exhausted"
                break

            if self._rate_limiter is not None:
                try:
                    await self._rate_limiter.acquire(config.merchant)
                except RateLimitTimeoutError as exc:
                    stats._remember_error(str(exc))
                    stats.stop_reason = "rate_limit_timeout"
                    break

            stats.page_fetch_count += 1
            remaining = config.max_items - len(seen)
            page_products, errors = await self._fetch_page(config, page, remaining)

            if errors:
                stats.error_count += len(errors)
                for message in errors:
                    stats._remember_error(message)
            if _matches(errors, _RATE_LIMIT_MARKER):
                stats.http_429_count += 1
                stats.stop_reason = "http_429"
                break
            if _matches(errors, _ACCESS_BLOCKED_MARKER):
                stats.stop_reason = "http_401_403"
                break

            new_products = [
                product for product in page_products
                if str(product.get("source_product_id") or "") not in seen
            ]
            for product in new_products:
                seen.add(str(product.get("source_product_id") or ""))
            stats.duplicate_count += len(page_products) - len(new_products)
            products.extend(new_products)
            stats.unique_count = len(seen)

            if not new_products and not page_products:
                stats.stop_reason = "request_failed" if errors else "no_more_results"
                break

            next_page = page + 1
            self._save_checkpoint(
                checkpoint_path,
                _Checkpoint(config.merchant, config.keyword, next_page, sorted(seen)),
            )
            self._save_products(result_path, products)

            if len(seen) >= config.max_items:
                stats.stop_reason = "target_reached"
                break
            page = next_page
            await jittered_sleep(config.min_interval_seconds)

        stats.wall_seconds = round(self._clock() - started, 3)
        return products[: config.max_items], stats

    async def _fetch_page(
        self,
        config: CollectionRunConfig,
        page: int,
        remaining: int,
    ) -> tuple[list[dict[str, Any]], list[str]]:
        """페이지 하나를 조회하고, SiteCrawler가 예외를 던지는 예상 밖의 경우도 안전하게 처리한다."""

        try:
            return await self._crawler.crawl(
                config.keyword,
                min(config.page_size, remaining),
                start_page=page,
                max_pages=1,
            )
        except Exception as exc:  # noqa: BLE001 - 판매처/파싱 예외를 모두 페이지 실패로 취급
            return [], [f"page {page} 처리 중 예상하지 못한 예외: {type(exc).__name__}"]

    def _checkpoint_path(self, config: CollectionRunConfig) -> Path:
        """판매처와 검색어별로 구분된 checkpoint 파일 경로를 반환한다."""

        return config.checkpoint_dir / f"{_safe_name(config.merchant)}_{_safe_name(config.keyword)}.checkpoint.json"

    def _result_path(self, config: CollectionRunConfig) -> Path:
        """판매처와 검색어별로 구분된 누적 상품 파일 경로를 반환한다."""

        return config.checkpoint_dir / f"{_safe_name(config.merchant)}_{_safe_name(config.keyword)}.products.json"

    def _load_checkpoint(self, config: CollectionRunConfig, path: Path) -> _Checkpoint:
        """resume 설정이 있으면 동일 실행의 checkpoint를 검증해 읽는다.

        Raises:
            ValueError: checkpoint의 판매처 또는 검색어가 현재 설정과 다른 경우다.
        """

        empty = _Checkpoint(config.merchant, config.keyword, 1, [])
        if not config.resume or not path.exists():
            return empty
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            checkpoint = _Checkpoint(**raw)
        except (OSError, ValueError, TypeError) as exc:
            raise ValueError(f"checkpoint를 읽을 수 없습니다: {path}") from exc
        if checkpoint.merchant != config.merchant or checkpoint.keyword != config.keyword:
            raise ValueError("checkpoint의 판매처 또는 검색어가 현재 설정과 다릅니다")
        return checkpoint

    def _load_products(self, path: Path) -> list[dict[str, Any]]:
        """resume 설정에서 기존에 저장한 상품 목록을 읽는다."""

        if not path.exists():
            return []
        return json.loads(path.read_text(encoding="utf-8"))

    def _save_checkpoint(self, path: Path, checkpoint: _Checkpoint) -> None:
        """페이지 완료 상태를 임시 파일 교체 방식으로 안전하게 저장한다."""

        self._write_json(path, asdict(checkpoint))

    def _save_products(self, path: Path, products: list[dict[str, Any]]) -> None:
        """지금까지 수집한 고유 상품 전체를 임시 파일 교체 방식으로 저장한다."""

        self._write_json(path, products)

    def _write_json(self, path: Path, payload: Any) -> None:
        """부분 쓰기 파일을 남기지 않도록 JSON을 원자적으로 교체한다."""

        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(temporary, path)


def _matches(errors: list[str], marker: str) -> bool:
    """오류 목록 중 하나라도 지정한 접근 안전성 오류 코드를 포함하는지 반환한다."""

    return any(marker in message for message in errors)


def _safe_name(value: str) -> str:
    """검색어/판매처를 파일 이름에 안전한 문자열로 변환한다."""

    cleaned = _UNSAFE_FILENAME_CHARS.sub("_", value.strip()) or "unnamed"
    return cleaned[:80]


__all__ = ["CollectionRunConfig", "CollectionRunner", "CollectionRunStats", "PageCrawler"]
