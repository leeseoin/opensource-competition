"""운영 사실을 만들지 않고 비교용 v1-unified 계약을 검증한다."""

from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator


def repository_root() -> Path:
    """현재 package 위치에서 저장소 루트를 찾는다.

    Returns:
        `contracts` 디렉토리가 있는 저장소 절대 경로다.

    Raises:
        FileNotFoundError: 상위 디렉토리에서 저장소 계약을 찾지 못한 경우다.
    """

    for candidate in Path(__file__).resolve().parents:
        if (candidate / "contracts" / "collector" / "unified").is_dir():
            return candidate
    raise FileNotFoundError("저장소의 contracts/collector/unified 디렉토리를 찾지 못했습니다")


@lru_cache(maxsize=1)
def unified_validator() -> Draft202012Validator:
    """공통 비교 Schema를 한 번 읽어 재사용 가능한 validator를 만든다.

    Returns:
        JSON Schema Draft 2020-12 validator다.
    """

    schema_path = repository_root() / "contracts" / "collector" / "unified" / "unified-product.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    return Draft202012Validator(schema)


def validate_product(product: dict[str, Any]) -> list[str]:
    """상품 한 건을 v1-unified Schema로 검사한다.

    Args:
        product: 판매처 Adapter가 만든 비교 상품이다.

    Returns:
        오류 위치와 원인을 담은 문자열 목록이며 정상 상품은 빈 목록이다.
    """

    errors: list[str] = []
    for issue in sorted(unified_validator().iter_errors(product), key=lambda value: list(value.path)):
        location = "/".join(str(value) for value in issue.absolute_path) or "(root)"
        errors.append(f"{location}: {issue.message}")
    return errors


def count_missing_fields(product: dict[str, Any]) -> int:
    """비교에는 필요하지만 판매처 검색 응답에서 비어 있는 필드를 센다.

    빈 문자열, null, 빈 배열을 누락으로 계산한다. `reviews`와 `options`는 검색 단계에서
    빈 값이 정상일 수 있지만 필드 완전성 비교에서는 누락 신호로 남긴다.

    Returns:
        비어 있는 최상위 필드와 옵션 하위 필드의 개수다.
    """

    fields = (
        "brand",
        "price_original",
        "image_url",
        "images",
        "color",
        "style_code",
        "rating",
        "review_count",
        "category",
        "category_path",
        "in_stock",
        "reviews",
    )
    missing = sum(product.get(name) in (None, "", []) for name in fields)
    options = product.get("options") or {}
    missing += int(not options.get("colors")) + int(not options.get("sizes"))
    return missing
