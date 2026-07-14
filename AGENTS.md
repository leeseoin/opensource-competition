# AGENTS.md

## 프로젝트 개요

- 프로젝트명: Purchase Research Agent(가칭)
- 목적: 자연어 구매 조건을 구체화하고 실제 판매처의 공개 상품·리뷰 정보를 근거 기반으로 비교·재검증한다.
- 현재 상태: Go/Python 역할 분리와 디렉토리 구조 설계 완료, 실제 기능은 planned
- 핵심 기술: Go, Python, MCP, FastAPI, React, PostgreSQL

## 구성요소 책임

- `services/collector`: Go. 외부 판매처 접근, 검색·상세·옵션·리뷰 parsing, rate limit, timeout, retry, 차단 감지
- `services/research-backend`: Python. MCP, FastAPI, 작업 orchestration, 데이터 검증·정규화, PostgreSQL 적재, 리뷰 신호 추출, 비교·재검증
- `apps/purchase-web`: React. 구매 대화, 진행 상태, 비교, 근거, 검증 결과 표시
- `plugins/purchase-research-agent`: Codex plugin. 사용자 질문, MCP tool 선택, 근거 기반 최종 설명

## 핵심 경계

- 외부 판매처에는 Go Collector만 접근한다.
- PostgreSQL의 최종 쓰기는 Python Backend만 수행한다.
- Codex Plugin과 React는 크롤러나 DB를 직접 호출하지 않는다.
- Go는 판매처별 `CollectorResult`를 반환하고 최종 추천을 판단하지 않는다.
- Python은 Collector가 제공하지 않은 판매처 사실을 생성하지 않는다.
- Codex는 구조화된 근거를 설명하며 상품 사실을 추측하지 않는다.

## 작업 원칙

- 기존 사용자 변경을 되돌리지 않는다.
- 구현된 기능과 planned 기능을 문서에서 구분한다.
- 로그인, CAPTCHA, robots 제한, 접근 통제를 우회하지 않는다.
- 공개적으로 접근 가능한 상품 정보만 수집한다.
- 판매처별 동시성·요청 빈도·timeout·재시도 상한을 둔다.
- 가격·재고·옵션에는 `sourceUrl`, `collectedAt`, `collectorVersion`을 포함한다.
- 추천 snapshot과 구매 전 verification snapshot을 분리한다.
- 리뷰 작성자 이름·프로필 등 식별정보는 저장하지 않는다.
- 리뷰 이미지는 내려받지 않고 사진 존재 여부와 공개 출처 참조만 저장한다.
- LLM 추출값은 `derived`와 confidence를 기록하고 공식 정보와 구분한다.
- API key, cookie, session, 개인 정보는 커밋하지 않는다.
- Python은 `uv`와 서비스 내부 `.venv`를 사용한다.
- Go와 Python의 public type/function에는 책임, 입력·출력, 실패 계약을 주석으로 남긴다.

## 실행과 검증

아직 실제 서비스 의존성과 실행 entrypoint는 구현 전이다.

예정 검증 계층:

- Go unit/contract test: parser, rate limit, 저장된 HTML fixture
- Python unit/integration test: 정규화, DB 적재, review signal, MCP/API 계약
- E2E: 구매 질문 → 실제 수집 → 근거 비교 → 재검증
- 실제 판매처 smoke test는 기본 CI에서 제외하고 opt-in으로 실행

## 문서

- 시스템 구조: `docs/architecture/Purchase_Research_Agent_시스템_구조.md`
- 구현 계획: `docs/planning/Purchase_Research_Agent_TODO.md`
- 날짜는 `YYYY-MM-DD` 형식을 사용한다.
