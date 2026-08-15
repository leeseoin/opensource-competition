"""사이트별 크롤러 출력을 통일된 구조로 변환하는 모듈.

크롤러 raw 출력은 사이트별 스키마(v1-abcmart / v1-29cm)를 유지하고,
이 모듈이 두 사이트를 같은 구조로 평탄화한다.

통일 필드 목록
-----------
source_product_id, title, brand, price, price_original, discount_percent,
image_url, images, color, style_code, link, site,
rating, review_count, category, category_path, in_stock,
options: {colors, sizes},
reviews: [{review_source_id, content, score, date, size,
           helpful_count, images,
           # 사이트별 추가 (없으면 null)
           detail_scores, is_best,       ← ABC마트
           user_size, partner_comment, size_survey, is_blind  ← 29CM
          }]
"""

import re


def _abc_options(raw: dict | None) -> dict:
    """ABC마트 options dict를 통일 형식으로."""
    if not isinstance(raw, dict):
        return {"colors": [], "sizes": []}
    return {
        "colors": raw.get("colors") or [],
        "sizes":  raw.get("sizes")  or [],
        "available_sizes": raw.get("available_sizes") or [],
    }


def _cm29_options(raw: list | dict | None, color_field: str) -> dict:
    """29CM options 리스트 + color 필드를 통일 형식으로.

    colors: product.color (options[].value에서 이미 추출된 값)
    sizes : options[].value에서 'KR (숫자)' 패턴 추출 → 없으면 3-4자리 숫자
    """
    if isinstance(raw, dict):
        return {
            "colors": raw.get("colors") or [],
            "sizes": raw.get("sizes") or [],
        }
    colors = [c.strip() for c in color_field.split(",") if c.strip()] if color_field else []

    sizes_seen: list[str] = []
    sizes_set: set[str] = set()
    for opt in (raw or []):
        val = opt.get("value", "")
        m = re.search(r"KR\s*(\d+)", val)
        s = m.group(1) if m else re.search(r"(\d{3,4})\s*mm", val) and re.search(r"(\d{3,4})\s*mm", val).group(1)
        if not s:
            # 슬래시 구분 텍스트에서 마지막 숫자 토큰 시도
            parts = [p.strip() for p in re.split(r"\s*/\s*|:", val)]
            for p in parts:
                nm = re.fullmatch(r"\d{3,4}", p)
                if nm:
                    s = nm.group()
                    break
        if s and s not in sizes_set:
            sizes_set.add(s)
            sizes_seen.append(s)

    return {"colors": colors, "sizes": sizes_seen}


def _abc_review(rv: dict) -> dict:
    return {
        "review_source_id": rv.get("review_source_id"),
        "content":          rv.get("content", ""),
        "score":            rv.get("score"),
        "date":             rv.get("date", ""),
        "size":             rv.get("size", ""),
        "helpful_count":    rv.get("helpful_count", 0),
        "images":           rv.get("images", []),
        # ABC마트 전용
        "detail_scores":    rv.get("detail_scores"),
        "review_color":     rv.get("color"),
        "is_best":          rv.get("is_best"),
        # 29CM 전용 (없음)
        "user_size":        None,
        "partner_comment":  None,
        "size_survey":      None,
        "is_blind":         None,
    }


def _cm29_review(rv: dict) -> dict:
    return {
        "review_source_id": rv.get("review_source_id"),
        "content":          rv.get("content", ""),
        "score":            rv.get("score"),
        "date":             rv.get("date", ""),
        "size":             rv.get("size", ""),
        "helpful_count":    rv.get("helpful_count", 0),
        "images":           rv.get("images", []),
        # ABC마트 전용 (없음)
        "detail_scores":    None,
        "review_color":     None,
        "is_best":          None,
        # 29CM 전용
        "user_size":        rv.get("user_size"),
        "partner_comment":  rv.get("partner_comment"),
        "size_survey":      rv.get("size_survey"),
        "is_blind":         rv.get("is_blind"),
    }


def normalize_abcmart(product: dict) -> dict:
    return {
        "source_product_id": product.get("source_product_id", ""),
        "title":             product.get("title", ""),
        "brand":             product.get("brand", ""),
        "price":             product.get("price", ""),
        "price_original":    product.get("price_original", ""),
        "discount_percent":  product.get("discount_percent"),
        "image_url":         product.get("image_url", ""),
        "images":            product.get("images", []),
        "color":             product.get("color", ""),
        "style_code":        product.get("style_code", ""),
        "link":              product.get("link", ""),
        "site":              "abcmart",
        "rating":            product.get("rating"),
        "review_count":      product.get("review_count"),
        "category":          product.get("category", ""),
        "category_path":     product.get("category_path", ""),
        "in_stock":          product.get("in_stock"),
        "options":           _abc_options(product.get("options")),
        "reviews":           [_abc_review(r) for r in product.get("reviews", [])],
    }


def normalize_cm29(product: dict) -> dict:
    return {
        "source_product_id": product.get("source_product_id", ""),
        "title":             product.get("title", ""),
        "brand":             product.get("brand", ""),
        "price":             product.get("price", ""),
        "price_original":    product.get("price_original", ""),
        "discount_percent":  product.get("discount_percent"),
        "image_url":         product.get("image_url", ""),
        "images":            product.get("images", []),
        "color":             product.get("color", ""),
        "style_code":        product.get("style_code", ""),
        "link":              product.get("link", ""),
        "site":              "29cm",
        "rating":            product.get("rating"),
        "review_count":      product.get("review_count"),
        "category":          product.get("category", ""),
        "category_path":     product.get("category_path", ""),
        "in_stock":          product.get("in_stock"),
        "options":           _cm29_options(product.get("options"), product.get("color", "")),
        "reviews":           [_cm29_review(r) for r in product.get("reviews", [])],
    }


def normalize(product: dict) -> dict:
    """site 필드를 보고 자동으로 변환."""
    site = product.get("site", "")
    if site == "abcmart":
        return normalize_abcmart(product)
    if site == "29cm":
        return normalize_cm29(product)
    raise ValueError(f"알 수 없는 site: {site!r}")
