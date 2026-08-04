"""contracts/collector/v1-abcmart/abcmart-crawl-item.schema.json을 런타임에 강제한다.

크롤러가 만들어낸 상품 dict가 이 스키마에 안 맞으면(필수 필드 누락, VARCHAR 길이 초과 등)
그 상품은 결과에서 제외하고 errors에 이유를 남긴다 — 스키마 위반 데이터가 백엔드
POST까지 조용히 넘어가지 않게 막는 첫 번째 관문이다.
"""

from __future__ import annotations

import json
from pathlib import Path

import jsonschema

_SCHEMA_PATH = (
    Path(__file__).resolve().parents[3]
    / "contracts" / "collector" / "v1-abcmart" / "abcmart-crawl-item.schema.json"
)

_schema: dict | None = None
_validator: jsonschema.Draft202012Validator | None = None


def _get_validator() -> jsonschema.Draft202012Validator:
    global _schema, _validator
    if _validator is None:
        if not _SCHEMA_PATH.exists():
            raise FileNotFoundError(
                f"contract schema not found: {_SCHEMA_PATH} "
                "(purchase-research-agent와 contracts/가 같은 레포 루트 아래 있어야 한다)"
            )
        _schema = json.loads(_SCHEMA_PATH.read_text(encoding="utf-8"))
        _validator = jsonschema.Draft202012Validator(_schema)
    return _validator


def validate_items(items: list[dict]) -> tuple[list[dict], list[str]]:
    """스키마를 통과한 상품만 남기고, 위반 상품은 제거하며 이유를 errors로 반환한다."""
    validator = _get_validator()
    valid: list[dict] = []
    errors: list[str] = []

    for i, item in enumerate(items):
        issues = list(validator.iter_errors(item))
        if issues:
            label = item.get("title") or item.get("source_product_id") or f"index={i}"
            reasons = "; ".join(e.message for e in issues)
            errors.append(f"contract 위반으로 제외됨 [{label}]: {reasons}")
        else:
            valid.append(item)

    return valid, errors
