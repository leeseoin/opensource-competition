"""수동 대량 수집 스크립트가 최근에 상세를 조회한 상품을 다시 조회하지 않도록 판매처별
마지막 상세 조회 시각을 로컬 JSON 파일에 캐시한다.

Queue 기반 운영 경로(app/messaging, app/services/crawler_service.py)는 이 캐시를 쓰지 않는다.
Queue 작업 하나는 이미 상품 최대 50개·30초 timeout으로 작고, 실제 "다시 수집해야 하는가"
판정은 Product Backend(Java)의 CollectionRefreshService가 검색어 단위로 맡고 있다. 이 캐시는
scripts/run_unified_crawl.py 같은 수동 대량 실행에서 같은 (판매처, 상품)을 짧은 시간 안에
반복 실행할 때 상세/리뷰 API를 불필요하게 다시 때리지 않도록 돕는 보조 도구일 뿐이다.

DB에 저장된 실제 값은 기억하지 않는다 — "언제 마지막으로 상세를 조회했는지"만 기록하므로,
캐시로 건너뛴 상품은 이번 실행 결과에 검색 단계 필드만 남고 상세(리뷰/옵션/평점)는 비어 있다.
직전 실행 결과 파일과 병합해 쓰는 건 호출자의 책임이다.
"""

from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


class DetailFreshnessCache:
    """판매처별 상품 ID -> 마지막 상세 조회 시각을 로컬 JSON 파일로 유지한다."""

    def __init__(self, path: Path, ttl_hours: float = 24.0):
        """캐시 파일 위치와 상세를 다시 조회하기 전까지 봐줄 유효 시간을 설정한다.

        Args:
            path: 캐시를 저장할 JSON 파일 경로다.
            ttl_hours: 이 시간 안에 조회한 적 있는 상품은 다시 조회하지 않는다.

        Raises:
            ValueError: ttl_hours가 0 이하인 경우다.
        """

        if ttl_hours <= 0:
            raise ValueError("ttl_hours는 0보다 커야 합니다")
        self._path = path
        self._ttl = timedelta(hours=ttl_hours)
        self._data: dict[str, dict[str, str]] = self._load()

    def split_stale(
        self, merchant: str, products: list[dict[str, Any]]
    ) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        """이번에 상세를 다시 조회해야 할 상품과 최근에 이미 조회해 건너뛸 상품을 나눈다.

        Args:
            merchant: 판매처 식별자이며 캐시는 판매처별로 구분해 관리한다.
            products: 상세 조회 대상 후보 상품이다.

        Returns:
            (stale, fresh) 튜플이며 각각 입력 순서를 유지한다. source_product_id가
            없는 상품은 캐시로 판단할 수 없으므로 항상 stale로 취급한다.
        """

        merchant_cache = self._data.get(merchant, {})
        now = datetime.now(timezone.utc)
        stale: list[dict[str, Any]] = []
        fresh: list[dict[str, Any]] = []
        for product in products:
            product_id = str(product.get("source_product_id") or "")
            cached_at = merchant_cache.get(product_id) if product_id else None
            if product_id and cached_at is not None and now - _parse(cached_at) < self._ttl:
                fresh.append(product)
            else:
                stale.append(product)
        return stale, fresh

    def mark_fetched(self, merchant: str, products: list[dict[str, Any]]) -> None:
        """이번에 상세를 새로 조회한 상품들의 캐시 시각을 지금으로 갱신하고 즉시 저장한다.

        Args:
            merchant: 판매처 식별자다.
            products: 방금 상세 조회를 마친 상품이다.
        """

        merchant_cache = self._data.setdefault(merchant, {})
        now_iso = datetime.now(timezone.utc).isoformat()
        for product in products:
            product_id = str(product.get("source_product_id") or "")
            if product_id:
                merchant_cache[product_id] = now_iso
        self._save()

    def _load(self) -> dict[str, dict[str, str]]:
        """캐시 파일이 없거나 손상된 경우 빈 캐시로 시작한다(수동 도구이므로 실패해도 안전)."""

        if not self._path.exists():
            return {}
        try:
            return json.loads(self._path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return {}

    def _save(self) -> None:
        """부분 쓰기 파일을 남기지 않도록 JSON을 원자적으로 교체한다."""

        self._path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self._path.with_suffix(self._path.suffix + ".tmp")
        temporary.write_text(json.dumps(self._data, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(temporary, self._path)


def _parse(value: str) -> datetime:
    """캐시에 저장된 ISO 8601 시각 문자열을 timezone-aware datetime으로 되돌린다."""

    return datetime.fromisoformat(value)


__all__ = ["DetailFreshnessCache"]
