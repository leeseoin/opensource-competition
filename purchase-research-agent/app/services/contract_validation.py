"""사이트별 contract schema로 크롤러 output을 런타임에 검증한다.

크롤러가 만들어낸 상품 dict가 스키마에 안 맞으면(필수 필드 누락, VARCHAR 길이 초과 등)
그 상품은 결과에서 제외하고 errors에 이유를 남긴다 — 스키마 위반 데이터가 백엔드
POST까지 조용히 넘어가지 않게 막는 첫 번째 관문이다.
"""

from __future__ import annotations

import json
from pathlib import Path

import jsonschema

_REPO_ROOT = Path(__file__).resolve().parents[3]

_SCHEMA_PATHS: dict[str, Path] = {
    "abcmart": _REPO_ROOT / "contracts" / "collector" / "v1-abcmart" / "abcmart-crawl-item.schema.json",
    "29cm":    _REPO_ROOT / "contracts" / "collector" / "v1-29cm"    / "29cm-crawl-item.schema.json",
}

_validators: dict[str, jsonschema.Draft202012Validator] = {}


def _get_validator(site: str) -> jsonschema.Draft202012Validator | None:
    if site not in _SCHEMA_PATHS:
        return None
    if site not in _validators:
        path = _SCHEMA_PATHS[site]
        if not path.exists():
            raise FileNotFoundError(
                f"contract schema not found: {path} "
                "(purchase-research-agent와 contracts/가 같은 레포 루트 아래 있어야 한다)"
            )
        schema = json.loads(path.read_text(encoding="utf-8"))
        _validators[site] = jsonschema.Draft202012Validator(schema)
    return _validators[site]


def validate_items(items: list[dict], site: str | None = None) -> tuple[list[dict], list[str]]:
    """스키마를 통과한 상품만 남기고, 위반 상품은 제거하며 이유를 errors로 반환한다.

    site를 명시하지 않으면 각 상품의 'site' 필드로 자동 판별한다.
    해당 사이트의 schema가 없으면 검증 없이 통과시킨다.
    """
    valid: list[dict] = []
    errors: list[str] = []

    for i, item in enumerate(items):
        s = site or item.get("site", "")
        validator = _get_validator(s)

        if validator is None:
            valid.append(item)
            continue

        issues = list(validator.iter_errors(item))
        if issues:
            label = item.get("title") or item.get("source_product_id") or f"index={i}"
            reasons = "; ".join(e.message for e in issues)
            errors.append(f"contract 위반으로 제외됨 [{label}]: {reasons}")
        else:
            valid.append(item)

    return valid, errors
