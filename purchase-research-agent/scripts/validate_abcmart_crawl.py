"""ABC마트 크롤링 결과 JSON을 contracts/collector/v1-abcmart 스키마로 검증한다.

사용법:
    python scripts/validate_abcmart_crawl.py output/abcmart_구두_남성_top25_....json [...more files]

입력 파일은 AbcMartCrawler.crawl_category() (+ attach_details())가 반환하는
raw dict 리스트를 그대로 json.dump한 것을 기대한다 (app/api/endpoints/search.py의
/api/v1/category가 저장하는 output/*.json과 동일한 모양).

두 단계를 검사한다:
  1) 원본 크롤러 dict가 abcmart-crawl-item.schema.json에 맞는지
  2) POST /api/v1/products/batch에 실제로 보낼 형태로 변환한 뒤
     product-batch-request.schema.json에 맞는지 (price 문자열 -> int 파싱 등 포함)
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import jsonschema

_CONTRACTS_DIR = Path(__file__).resolve().parents[2] / "contracts" / "collector" / "v1-abcmart"
_ITEM_SCHEMA_PATH = _CONTRACTS_DIR / "abcmart-crawl-item.schema.json"
_BATCH_SCHEMA_PATH = _CONTRACTS_DIR / "product-batch-request.schema.json"


def _load_schema(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_price(price_str: str | None) -> int | None:
    """CrawlTriggerService.parsePrice()와 동일한 로직."""
    if not price_str:
        return None
    digits = re.sub(r"[^0-9]", "", price_str)
    return int(digits) if digits else None


def to_review_payload(review: dict) -> dict:
    return {
        "reviewSourceId": review.get("review_source_id"),
        "content": review.get("content"),
        "score": review.get("score"),
        "reviewDate": review.get("date") or None,
        "size": review.get("size"),
    }


def to_options_payload(options: dict | None) -> dict | None:
    if not options:
        return None
    return {
        "colors": options.get("colors") or [],
        "sizes": options.get("sizes") or [],
    }


def to_product_payload(item: dict) -> dict:
    reviews = item.get("reviews")
    return {
        "sourceProductId": item.get("source_product_id"),
        "title": item.get("title"),
        "brand": item.get("brand") or None,
        "price": parse_price(item.get("price")),
        "priceOriginal": parse_price(item.get("price_original")),
        "discountPercent": item.get("discount_percent"),
        "imageUrl": item.get("image_url") or None,
        "styleCode": item.get("style_code") or None,
        "link": item.get("link"),
        "reviewCount": item.get("review_count"),
        "options": to_options_payload(item.get("options")),
        "reviews": [to_review_payload(r) for r in reviews] if reviews else None,
    }


def _format_error(err: jsonschema.exceptions.ValidationError) -> str:
    path = "/".join(str(p) for p in err.absolute_path) or "(root)"
    return f"{path}: {err.message}"


def validate_file(path: Path, item_schema: dict, batch_schema: dict, site: str, keyword: str) -> tuple[int, int, int]:
    items = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(items, dict) and "items" in items:
        items = items["items"]

    item_validator = jsonschema.Draft202012Validator(item_schema)
    ok_count = 0
    fail_count = 0

    print(f"\n=== {path.name} ({len(items)}건) ===")

    for i, item in enumerate(items):
        label = item.get("title", "")[:30] or item.get("source_product_id", f"index={i}")
        errors = list(item_validator.iter_errors(item))
        if errors:
            fail_count += 1
            print(f"[FAIL] crawl-item #{i} {label}")
            for e in errors:
                print(f"    - {_format_error(e)}")
        else:
            ok_count += 1

    batch_request = {
        "site": site,
        "keyword": keyword,
        "collectedAt": None,
        "products": [to_product_payload(item) for item in items],
    }
    batch_validator = jsonschema.Draft202012Validator(batch_schema)
    batch_errors = list(batch_validator.iter_errors(batch_request))
    batch_fail_indices: set[int] = set()
    if batch_errors:
        for e in batch_errors:
            idx_path = "/".join(str(p) for p in e.absolute_path)
            product_idx = e.absolute_path[1] if len(e.absolute_path) > 1 and e.absolute_path[0] == "products" else None
            if isinstance(product_idx, int):
                batch_fail_indices.add(product_idx)
            print(f"[FAIL] batch {idx_path}: {e.message}")
        print(f"  -> 배치 POST 시 {len(batch_fail_indices)}/{len(items)}개 상품이 검증 실패로 거부됨")
    else:
        print("[OK] product-batch-request 변환 결과 스키마 통과 (배치 POST 가능한 형태)")

    return ok_count, fail_count, len(batch_fail_indices)


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    item_schema = _load_schema(_ITEM_SCHEMA_PATH)
    batch_schema = _load_schema(_BATCH_SCHEMA_PATH)

    total_ok = 0
    total_fail = 0
    total_batch_fail = 0
    for arg in sys.argv[1:]:
        path = Path(arg)
        if not path.exists():
            print(f"[SKIP] 파일 없음: {path}")
            continue
        ok, fail, batch_fail = validate_file(path, item_schema, batch_schema, site="abcmart", keyword=path.stem)
        total_ok += ok
        total_fail += fail
        total_batch_fail += batch_fail

    print(
        f"\n=== 요약: crawl-item 통과 {total_ok}건 / 실패 {total_fail}건, "
        f"배치 POST 시 거부될 상품 {total_batch_fail}건 ==="
    )
    sys.exit(1 if (total_fail or total_batch_fail) else 0)


if __name__ == "__main__":
    main()
