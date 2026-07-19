from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import agent
from app.clients.anthropic_client import AnthropicClient
from app.clients.guard_api_client import GuardAPIClient
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    app.state.ai_client = AnthropicClient()
    app.state.guard_client = GuardAPIClient()
    print(f"AgentPay Guard Sample Agent 시작 - Guard: {settings.guard_api_base_url}")
    yield
    print("AgentPay Guard Sample Agent 종료")


app = FastAPI(
    title="AgentPay Guard - Sample Agent API",
    description="AgentPay Guard 게이트웨이와 연동하여 외부 유료 리소스를 사용하는 샘플 AI 에이전트",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

register_exception_handlers(app)

app.include_router(agent.router, prefix="/api/v1/agent", tags=["AI Agent"])


@app.get("/", tags=["Root"])
async def root():
    settings = get_settings()
    return {
        "status": "ok",
        "service": "AgentPay Guard Sample Agent",
        "guard_api": settings.guard_api_base_url,
        "docs": "/docs",
    }


@app.get("/health", tags=["Root"])
async def health():
    return {"status": "ok"}
