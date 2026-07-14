from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.api.endpoints import agent

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("🚀 AgentPay Guard - Sample Agent 서버 시작 중...")
    yield
    print("🚀 AgentPay Guard - Sample Agent 서버 종료 중...")

app = FastAPI(
      title="AgentPay Guard - Sample Agent API",
      description="AgentPay Guard 게이트웨이와 연동하여 외부 AI 리소스를 사용하는 샘플 에이전트",
      version="1.0.0",
      lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(agent.router, prefix="/api/v1/agent", tags=["AI Agent"])

@app.get("/", tags=["Root"])
async def root():
    return {
        "status": "success",
        "message": "AgentPay Guard Sample Agent API가 정상적으로 실행 중입니다.",
        "docs_url": "/docs"
    }
