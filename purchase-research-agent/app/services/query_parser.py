import re

# §3.3 Query Analysis Agent의 임시 구현.
# 원래 설계는 LLM 호출이지만, 이 환경에 ANTHROPIC_API_KEY가 없어서
# 규칙 기반 파서로 먼저 전체 파이프라인을 연결하고, 나중에 LLM 호출로 교체한다.
# (§12 Open Question #16)

_PRICE_PATTERN = re.compile(r"(\d+(?:,\d{3})*)\s*(만)?\s*원?\s*(이하|미만|이내|안)")

_STOPWORDS = [
    "추천해줘", "추천해주세요", "추천",
    "찾아줘", "찾아주세요", "찾아",
    "보여줘", "보여주세요",
    "알려줘", "알려주세요",
    "구매하고 싶어", "사고 싶어",
    "좀",
]


def parse_query(text: str) -> dict:
    """자연어 요청에서 조회 조건을 추출한다.

    반환: {"keyword": str | None, "max_price": int | None}
    """
    max_price = None
    remaining = text

    match = _PRICE_PATTERN.search(text)
    if match:
        number = int(match.group(1).replace(",", ""))
        unit = match.group(2)
        max_price = number * 10000 if unit == "만" else number
        remaining = remaining[:match.start()] + remaining[match.end():]

    for word in _STOPWORDS:
        remaining = remaining.replace(word, "")

    keyword = remaining.strip() or None

    return {"keyword": keyword, "max_price": max_price}
