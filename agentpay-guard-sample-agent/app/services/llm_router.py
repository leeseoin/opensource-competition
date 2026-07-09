from dataclasses import dataclass
from typing import Optional

from app.core.config import Settings

PREMIUM_THRESHOLD_CHARS = 400
PREMIUM_KEYWORDS = ("분석", "요약", "전략", "코드 리뷰", "설계")

_PRICE_PER_1K_CHARS = {
    "default": 0.002,
    "premium": 0.01,
}

# 실제 Anthropic 요금표 (USD / 1M 토큰). 요금이 바뀌면 여기만 갱신하면 된다.
_TOKEN_PRICE_USD_PER_1M = {
    "claude-haiku-4-5-20251001": {"input": 1.00, "output": 5.00},
    "claude-haiku-4-5": {"input": 1.00, "output": 5.00},
    "claude-sonnet-5": {"input": 3.00, "output": 15.00},
}


def calculate_actual_cost(model: str, input_tokens: int, output_tokens: int) -> Optional[float]:
    """
    Anthropic 응답의 실제 토큰 사용량을 요금표에 대입해 실제 청구 비용을 계산한다.
    요금표에 없는 모델이면 계산할 수 없으므로 None을 반환한다.
    """
    price = _TOKEN_PRICE_USD_PER_1M.get(model)
    if price is None:
        return None
    cost = (input_tokens * price["input"] + output_tokens * price["output"]) / 1_000_000
    return round(cost, 8)


@dataclass
class RoutingDecision:
    model: str
    tier: str
    estimated_cost: float
    intent: str


def route(prompt: str, settings: Settings) -> RoutingDecision:
    """
    프롬프트 길이/키워드로 어떤 모델을 쓸지, 예상 비용이 얼마인지 결정한다.
    실제 토큰 계산기가 아닌 PoC용 대략치이다.
    """
    is_premium = len(prompt) > PREMIUM_THRESHOLD_CHARS or any(keyword in prompt for keyword in PREMIUM_KEYWORDS)
    tier = "premium" if is_premium else "default"
    model = settings.anthropic_model_premium if is_premium else settings.anthropic_model_default
    estimated_cost = round((len(prompt) / 1000) * _PRICE_PER_1K_CHARS[tier], 6)

    return RoutingDecision(
        model=model,
        tier=tier,
        estimated_cost=estimated_cost,
        intent=prompt[:120],
    )
