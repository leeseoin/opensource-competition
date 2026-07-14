# AgentPay Guard Sample Agent Architecture

현재 프로젝트는 엔터프라이즈급 백엔드에서 사용하는 관심사 분리(Separation of Concerns) 패턴을 따르고 있습니다.

## Directory Structure

```text
app/
├── api/endpoints/
│   ├── agent.py          # API 진입점 (프론트/백엔드의 요청을 받음)
├── clients/
│   ├── anthropic_client.py # Claude API 통신만 담당
│   ├── guard_api_client.py # 결제/검증 통신만 담당
│   └── scraper_client.py   # HTTP 요청(requests, httpx, aiohttp) 통신만 담당
├── services/
│   ├── crawler_service.py  # 쇼핑몰 HTML 파싱 및 비즈니스 로직 담당
│   └── agent_service.py    # 크롤러와 AI 클라이언트를 조합하여 최종 결과 도출
└── schemas/
    └── product.py        # Pydantic 데이터 모델
```

## Core Principles
1. **비동기 I/O**: 크롤링과 AI 분석 등 대기 시간이 긴 작업은 FastAPI의 `async`/`await`와 `BackgroundTasks`를 활용하여 서버 블로킹을 방지합니다.
2. **모듈화**: 크롤링 로직이 변경되더라도 API 라우터나 AI 분석 로직에 영향을 주지 않도록 Client와 Service 계층을 철저히 분리합니다.
