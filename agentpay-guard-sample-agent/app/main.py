from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

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

# CORS 설정 (React 대시보드 등 다른 도메인에서의 API 호출 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 실제 운영(PoC) 단계에서는 프론트엔드 주소(예: http://localhost:3000)로 제한하는 것이 좋습니다.
    allow_credentials=True,
    allow_methods=["*"],  # GET, POST, OPTIONS 등 모든 HTTP 메서드 허용
    allow_headers=["*"],
)



# ==========================================
# 라우터(엔드포인트) 연결 구역
# ==========================================
# 앱이 커지면 모든 API를 여기에 쓰지 않고,
# 아래처럼 endpoints 폴더에 있는 라우터들을 조립만 합니다.

# app.include_router(health_router.router, prefix="/health", tags=["Health Check"])
# app.include_router(agent_router.router, prefix="/api/v1/agent", tags=["AI Agent"])

# 서버가 잘 떴는지 확인하기 위한 기본 테스트 API
@app.get("/", tags=["Root"])
async def root():
    return {
        "status": "success",
        "message": "AgentPay Guard Sample Agent API가 정상적으로 실행 중입니다.",
        "docs_url": "/docs"  # Swagger UI 주소 안내
    }

