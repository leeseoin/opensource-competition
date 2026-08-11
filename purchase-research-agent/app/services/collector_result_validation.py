"""Python 변환 결과가 저장소의 공통 CollectorResult JSON Schema를 지키는지 검증한다."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import jsonschema

_SCHEMA_PATH = (
    Path(__file__).resolve().parents[3]
    / "contracts" / "collector" / "v1" / "collector-result.schema.json"
)
_validator: jsonschema.Draft202012Validator | None = None


def validate_collector_result(result: dict[str, Any]) -> None:
    """공통 JSON Schema 위반 내용을 모아 ``ValueError``로 차단한다.

    Args:
        result: Product Backend로 전송하기 직전의 CollectorResult 객체다.

    Raises:
        ValueError: 계약 파일을 읽을 수 없거나 결과가 계약을 위반한 경우다.
    """

    issues = sorted(
        _get_validator().iter_errors(result),
        key=lambda issue: tuple(str(part) for part in issue.path),
    )
    if not issues:
        return
    messages = [
        f"{'/'.join(str(part) for part in issue.path) or '<root>'}: {issue.message}"
        for issue in issues
    ]
    raise ValueError("CollectorResult 계약 위반: " + "; ".join(messages))


def _get_validator() -> jsonschema.Draft202012Validator:
    """공통 CollectorResult validator를 한 번 생성한 뒤 재사용한다.

    Returns:
        Draft 2020-12 JSON Schema validator다.

    Raises:
        ValueError: 공통 계약 파일을 찾을 수 없는 경우다.
    """

    global _validator
    if _validator is not None:
        return _validator
    if not _SCHEMA_PATH.exists():
        raise ValueError(f"CollectorResult 계약 파일을 찾을 수 없습니다: {_SCHEMA_PATH}")
    schema = json.loads(_SCHEMA_PATH.read_text(encoding="utf-8"))
    _validator = jsonschema.Draft202012Validator(schema)
    return _validator
