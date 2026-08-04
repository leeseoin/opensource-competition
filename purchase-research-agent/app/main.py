from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import search, store
from app.services.crawler_service import SUPPORTED_SITES

app = FastAPI(
    title="Purchase Research Agent",
    description="쇼핑몰 상품 크롤링 및 가격 비교 에이전트 (ABC마트, 29CM)",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(search.router, prefix="/api/v1", tags=["Search"])
app.include_router(store.router, prefix="/api/v1", tags=["Search and Store"])


@app.get("/", tags=["Root"])
async def root():
    """서비스 상태와 Swagger 경로 및 지원 판매처를 반환한다."""

    return {
        "status": "ok",
        "service": "Purchase Research Agent",
        "docs": "/docs",
        "supported_sites": SUPPORTED_SITES,
    }


@app.get("/health", tags=["Root"])
async def health():
    """프로세스가 HTTP 요청을 처리할 수 있는지 확인한다."""

    return {"status": "ok"}
