# §3.3 Recommendation Agent의 임시 구현.
# 원래 설계는 LLM이 후보를 분석해서 추천하는 것이지만, ANTHROPIC_API_KEY가 없어서
# 규칙 기반(할인율 높은 순 -> 가격 낮은 순)으로 먼저 파이프라인을 연결한다.
# (§12 Open Question #16)


def recommend(products: list[dict], top_n: int = 5) -> list[dict]:
    def sort_key(p: dict):
        discount = p.get("discountPercent") or 0
        price = p.get("price") if p.get("price") is not None else float("inf")
        return (-discount, price)

    ranked = sorted(products, key=sort_key)
    return ranked[:top_n]
