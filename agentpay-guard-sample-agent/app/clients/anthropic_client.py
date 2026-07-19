import anthropic

from app.core.config import get_settings


class AnthropicClient:
    def __init__(self):
        settings = get_settings()
        self._client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
        self._default_model = settings.anthropic_model_default
        self._premium_model = settings.anthropic_model_premium

    def complete(self, prompt: str, max_tokens: int = 1024, use_premium: bool = False) -> str:
        model = self._premium_model if use_premium else self._default_model
        response = self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.content[0].text

    def estimate_cost(self, prompt: str, use_premium: bool = False) -> float:
        # 토큰 수 기반 간단 추정 (1000 토큰 ≈ 0.001 USD, PoC 기준)
        token_estimate = len(prompt) / 4
        rate = 0.003 if use_premium else 0.001
        return round(token_estimate * rate / 1000, 6)
