"""판매처 페이지를 안전 상한 안에서 순차 수집하고 재개 가능한 파일로 저장한다."""

from __future__ import annotations

import asyncio
import gzip
import json
import os
import resource
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import httpx

from .contract import count_missing_fields, validate_product
from .merchants import MerchantAdapter, create_adapter
from .models import CollectionConfig, CollectionStats, MerchantRequestError


@dataclass(slots=True)
class Checkpoint:
    """중단 후 이어서 실행할 검색어/페이지와 중복 제거 상태를 보관한다."""

    merchant: str
    queries: list[str]
    query_index: int
    next_page: int
    seen_ids: list[str]


class CollectionRunner:
    """한 판매처를 순차 수집하며 계약/중복/안전 중단을 관리한다."""

    def __init__(self, adapter: MerchantAdapter | None = None):
        """테스트 Adapter를 주입하거나 설정의 판매처 Adapter를 나중에 생성한다."""

        self._adapter = adapter

    async def run(
        self,
        config: CollectionConfig,
        client: httpx.AsyncClient | None = None,
    ) -> CollectionStats:
        """설정한 목표 또는 안전 중단 조건까지 상품을 수집한다.

        Args:
            config: 판매처/검색어/요청 예산/저장 위치 설정이다.
            client: 테스트에서 사용할 HTTP client이며 없으면 제한된 client를 생성한다.

        Returns:
            실제 달성 건수와 요청/오류/성능 지표다.
        """

        config.validate()
        adapter = self._adapter or create_adapter(config.merchant)
        if adapter.merchant != config.merchant:
            raise ValueError("설정 merchant와 Adapter merchant가 다릅니다")
        config.output_dir.mkdir(parents=True, exist_ok=True)
        checkpoint_path = config.output_dir / "checkpoint.json"
        result_path = config.output_dir / "products.ndjson.gz"
        summary_path = config.output_dir / "summary.json"

        checkpoint = self._load_checkpoint(config, checkpoint_path)
        seen = set(checkpoint.seen_ids)
        stats = CollectionStats(
            merchant=config.merchant,
            target_count=config.max_items,
            unique_count=len(seen),
            checkpoint_resumed=bool(config.resume and seen),
        )
        started_wall = time.perf_counter()
        started_cpu = time.process_time()
        owns_client = client is None
        active_client = client or httpx.AsyncClient(
            timeout=config.timeout_seconds,
            follow_redirects=False,
            headers={
                "User-Agent": "PurchaseResearchAgent/0.1 (+public product research; low rate)",
                "Accept": "application/json",
                "Accept-Language": "ko-KR",
                "Origin": "https://www.29cm.co.kr",
                "Referer": "https://www.29cm.co.kr/",
            },
        )

        try:
            output_mode = "at" if config.resume else "wt"
            with gzip.open(result_path, output_mode, encoding="utf-8") as output:
                await self._collect(config, adapter, active_client, checkpoint, seen, stats, output, checkpoint_path, started_wall)
        finally:
            if owns_client:
                await active_client.aclose()
            stats.wall_seconds = round(time.perf_counter() - started_wall, 6)
            stats.cpu_seconds = round(time.process_time() - started_cpu, 6)
            peak_memory = int(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss)
            stats.peak_memory_kib = peak_memory // 1024 if sys.platform == "darwin" else peak_memory
            self._write_json(summary_path, stats.to_dict())
        return stats

    async def _collect(
        self,
        config: CollectionConfig,
        adapter: MerchantAdapter,
        client: httpx.AsyncClient,
        checkpoint: Checkpoint,
        seen: set[str],
        stats: CollectionStats,
        output: Any,
        checkpoint_path: Path,
        started_wall: float,
    ) -> None:
        """검색어와 페이지를 돌며 한 번 검증된 고유 상품만 파일에 추가한다."""

        for query_index in range(checkpoint.query_index, len(config.queries)):
            query = config.queries[query_index]
            page = checkpoint.next_page if query_index == checkpoint.query_index else 1
            while True:
                if len(seen) >= config.max_items:
                    stats.stop_reason = "target_reached"
                    return
                if stats.request_count >= config.request_budget:
                    stats.stop_reason = "request_budget_exhausted"
                    return
                if time.perf_counter() - started_wall >= config.max_wall_seconds:
                    stats.stop_reason = "wall_time_exhausted"
                    return

                page_result = await self._fetch_with_retry(config, adapter, client, query, page, stats)
                if page_result is None:
                    if not stats.stop_reason:
                        stats.stop_reason = "request_failed"
                    return
                stats.received_count += len(page_result.products)
                for product in page_result.products:
                    product_id = str(product.get("source_product_id") or "")
                    if product_id in seen:
                        stats.duplicate_count += 1
                        continue
                    issues = validate_product(product)
                    if issues:
                        stats.contract_fail_count += 1
                        stats.error_count += 1
                        self._remember_error(stats, f"contract {query}/{page}/{product_id}: {'; '.join(issues)}")
                        continue
                    output.write(json.dumps(product, ensure_ascii=False, separators=(",", ":")) + "\n")
                    output.flush()
                    seen.add(product_id)
                    stats.unique_count = len(seen)
                    stats.contract_pass_count += 1
                    stats.missing_field_count += count_missing_fields(product)
                    if len(seen) >= config.max_items:
                        break

                reached_target = len(seen) >= config.max_items
                next_page = page if reached_target else page + 1
                self._save_checkpoint(
                    checkpoint_path,
                    Checkpoint(
                        merchant=config.merchant,
                        queries=config.queries,
                        query_index=query_index,
                        next_page=next_page,
                        seen_ids=sorted(seen),
                    ),
                )
                if reached_target:
                    stats.stop_reason = "target_reached"
                    return
                if not page_result.has_next:
                    break
                page = next_page
                await asyncio.sleep(config.min_interval_seconds)

            self._save_checkpoint(
                checkpoint_path,
                Checkpoint(
                    merchant=config.merchant,
                    queries=config.queries,
                    query_index=query_index + 1,
                    next_page=1,
                    seen_ids=sorted(seen),
                ),
            )
        stats.stop_reason = "queries_exhausted"

    async def _fetch_with_retry(
        self,
        config: CollectionConfig,
        adapter: MerchantAdapter,
        client: httpx.AsyncClient,
        query: str,
        page: int,
        stats: CollectionStats,
    ) -> Any | None:
        """일시 오류만 설정 상한까지 재시도하고 접근 제한은 즉시 중단한다."""

        for attempt in range(config.max_retries + 1):
            stats.request_count += 1
            try:
                return await adapter.fetch_page(client, query, page, min(config.page_size, adapter.page_size_limit))
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                error = MerchantRequestError(str(exc), retryable=True)
            except MerchantRequestError as exc:
                error = exc

            stats.error_count += 1
            self._remember_error(stats, f"{query}/{page} attempt={attempt + 1}: {error}")
            if error.status_code == 429:
                stats.http_429_count += 1
                stats.stop_reason = "http_429"
                return None
            if error.status_code in {401, 403}:
                stats.stop_reason = f"http_{error.status_code}"
                return None
            if not error.retryable or attempt >= config.max_retries:
                stats.stop_reason = "request_failed"
                return None
            if stats.request_count >= config.request_budget:
                stats.stop_reason = "request_budget_exhausted"
                return None
            await asyncio.sleep(config.min_interval_seconds)
        return None

    def _load_checkpoint(self, config: CollectionConfig, path: Path) -> Checkpoint:
        """resume 설정이 있으면 동일 실행의 checkpoint를 검증해 읽는다."""

        empty = Checkpoint(config.merchant, config.queries, 0, 1, [])
        if not config.resume or not path.exists():
            return empty
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            checkpoint = Checkpoint(**raw)
        except (OSError, ValueError, TypeError) as exc:
            raise ValueError(f"checkpoint를 읽을 수 없습니다: {path}") from exc
        if checkpoint.merchant != config.merchant or checkpoint.queries != config.queries:
            raise ValueError("checkpoint의 판매처 또는 검색어가 현재 설정과 다릅니다")
        return checkpoint

    def _save_checkpoint(self, path: Path, checkpoint: Checkpoint) -> None:
        """페이지 완료 상태를 임시 파일 교체 방식으로 안전하게 저장한다."""

        self._write_json(path, asdict(checkpoint))

    def _write_json(self, path: Path, payload: dict[str, Any]) -> None:
        """부분 쓰기 파일을 남기지 않도록 JSON을 원자적으로 교체한다."""

        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, path)

    def _remember_error(self, stats: CollectionStats, message: str) -> None:
        """요약 파일이 과도하게 커지지 않도록 최근 오류를 최대 100개 보관한다."""

        stats.errors.append(message)
        if len(stats.errors) > 100:
            del stats.errors[0]


__all__ = ["CollectionConfig", "CollectionRunner", "CollectionStats"]
