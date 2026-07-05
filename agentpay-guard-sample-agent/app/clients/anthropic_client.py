from dataclasses import dataclass

from anthropic import AsyncAnthropic


@dataclass
class GenerationResult:
    text: str
    input_tokens: int
    output_tokens: int


class AnthropicClient:
    """
    Anthropic Claude API 통신을 전담하는 클라이언트입니다.
    공식 SDK가 자체적으로 재시도/타임아웃을 처리하므로 BaseClient는 상속하지 않습니다.
    """

    def __init__(self, api_key: str):
        self._client = AsyncAnthropic(api_key=api_key)

    async def generate(self, prompt: str, model: str, max_tokens: int = 1024) -> GenerationResult:
        response = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            messages=[{"role": "user", "content": prompt}],
        )
        text = "".join(block.text for block in response.content if block.type == "text")
        return GenerationResult(
            text=text,
            input_tokens=response.usage.input_tokens,
            output_tokens=response.usage.output_tokens,
        )

    async def close(self) -> None:
        await self._client.close()
