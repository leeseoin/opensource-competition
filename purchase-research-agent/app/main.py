from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import search

app = FastAPI(
    title="Purchase Research Agent",
    description="쇼핑몰 상품 크롤링 및 가격 비교 에이전트 (무신사, ABC마트)",
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


@app.get("/", tags=["Root"])
async def root():
    return {
        "status": "ok",
        "service": "Purchase Research Agent",
        "docs": "/docs",
        "supported_sites": ["musinsa", "abcmart"],
    }


@app.get("/health", tags=["Root"])
async def health():
    return {"status": "ok"}
