import sys
from contextlib import asynccontextmanager

# Windows 콘솔의 기본 코드페이지(cp949 등)는 이모지를 출력하지 못해 lifespan에서 죽는다.
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import agent as agent_router
from app.api.endpoints import health as health_router
from app.clients.anthropic_client import AnthropicClient
from app.clients.guard_api_client import GuardAPIClient
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("🚀 AgentPay Guard - Sample Agent 서버 시작 중...")
    settings = get_settings()
    app.state.guard_client = GuardAPIClient(base_url=settings.guard_api_base_url)
    app.state.ai_client = AnthropicClient(api_key=settings.anthropic_api_key)
    yield
    await app.state.guard_client.close()
    await app.state.ai_client.close()
    print("🚀 AgentPay Guard - Sample Agent 서버 종료 중...")



app = FastAPI(
      title="AgentPay Guard - Sample Agent API",
      description="AgentPay Guard 게이트웨이와 연동하여 외부 AI 리소스를 사용하는 샘플 에이전트",
      version="1.0.0",
      lifespan=lifespan
)

# CORS 설정 (React 대시보드 등 다른 도메인에서의 API 호출 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 실제 운영(PoC) 단계에서는 프론트엔드 주소(예: http://localhost:3000)로 제한하는 것이 좋습니다.
    allow_credentials=True,
    allow_methods=["*"],  # GET, POST, OPTIONS 등 모든 HTTP 메서드 허용
    allow_headers=["*"],
)

register_exception_handlers(app)


# ==========================================
# 라우터(엔드포인트) 연결 구역
# ==========================================
app.include_router(health_router.router, prefix="/health", tags=["Health Check"])
app.include_router(agent_router.router, prefix="/api/v1/agent", tags=["AI Agent"])

# 서버가 잘 떴는지 확인하기 위한 기본 테스트 API
@app.get("/", tags=["Root"])
async def root():
    return {
        "status": "success",
        "message": "AgentPay Guard Sample Agent API가 정상적으로 실행 중입니다.",
        "docs_url": "/docs"  # Swagger UI 주소 안내
    }
