from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse


class GuardUnavailableError(Exception):
    """AgentPay Guard(Java) API에 연결하거나 응답을 받는 데 실패했을 때 발생한다."""


class AIProviderError(Exception):
    """외부 AI 제공자(Anthropic) 호출이 실패했을 때 발생한다."""


async def _guard_unavailable_handler(request: Request, exc: GuardUnavailableError) -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={"status": "error", "message": f"AgentPay Guard 서버에 연결할 수 없습니다: {exc}"},
    )


async def _ai_provider_error_handler(request: Request, exc: AIProviderError) -> JSONResponse:
    return JSONResponse(
        status_code=502,
        content={"status": "error", "message": f"AI 제공자 호출에 실패했습니다: {exc}"},
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(GuardUnavailableError, _guard_unavailable_handler)
    app.add_exception_handler(AIProviderError, _ai_provider_error_handler)
