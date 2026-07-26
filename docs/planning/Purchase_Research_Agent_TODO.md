# Purchase Research Agent 구현 계획

작성일: 2026-07-13
상태: in progress

## 체크박스 관리 규칙

- `[ ]`: 시작 전 또는 미완료. 작업 중이면 항목 끝에 `**(진행 중)**`을 추가한다.
- `[x]`: 구현, 관련 테스트, 문서 또는 계약 갱신이 끝나고 검증까지 통과한 상태다.
- 완료 항목의 코드 위치와 검증 결과는 [개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md)에 기록한다.
- 문제가 발생하면 원인과 해결 방법을 개발 진행 관리 문서의 문제 기록에 남긴다.

## Phase 0: 구조와 계약

- [x] 이전 프로젝트 구성요소 제거
- [x] Go Collector / Python Backend / Codex Plugin / Next.js 책임 분리
- [x] 하이브리드 아키텍처 문서 작성
- [x] 저장소 기본 디렉토리 구조 반영
- [x] 1차 운영 대상 판매처를 ABC마트와 29CM로 확정하고 무신사는 보류
- [x] 검색 요청 JSON Schema 초안과 정상 예제 작성
- [x] 수집 결과 JSON Schema 초안과 성공·부분 성공·무효 예제 작성
- [x] 재검증 결과 JSON Schema 초안과 변경 예제 작성
- [x] 판매처 공통 수집 데이터 v1 초안 문서 작성
- [x] Go Collector 판매처 원본→공통 Product 변환 동작 문서 작성
- [ ] 첫 판매처 선정과 공개 접근 범위 확인 **(진행 중: ABC마트 검색·robots 확인, 상세·리뷰 확인 필요)**
- [ ] Go `CollectorResult` JSON schema 확정 **(공통 수집 데이터 명세의 검색 조건·가격·페이지·재고 상태 반영 필요)**
- [ ] 상품 상세 수집 요청 Schema 작성
- [ ] 리뷰 수집 요청과 pagination Schema 작성
- [ ] 재검증 요청 Schema 작성
- [ ] 저장된 예제를 이용한 JSON Schema 자동 검증 명령 추가
- [ ] Go 응답이 `collector-result.schema.json`을 통과하는 contract test 추가
- [ ] Python domain model과 Go transport DTO 매핑 확정
- [ ] Python Pydantic model이 같은 정상·무효 예제를 검증하는 contract test 추가
- [ ] Schema, Go DTO, Python model 변경을 함께 검사하는 CI 추가
- [ ] v1 필수 필드, 실패 상태, 호환성 정책 최종 검토

완료 기준: 실제 Go 응답과 Python model이 같은 v1 Schema 및 예제로 검증되고, 두 서비스의 책임과 실패 상태를 설명할 수 있다.

## Phase 1: Go Collector 기반

- [x] Go module 구성
- [x] Collector configuration 구성과 단위 테스트
- [x] internal HTTP router와 health endpoint
- [x] HTTP server lifecycle과 graceful shutdown 테스트
- [x] 기존 Go type/function의 한국어 주석 규칙 정비
- [ ] 도메인 allowlist와 URL 검증
- [ ] 공통 HTTP client, timeout, retry, rate limiter **(부분 구현: ABC마트 timeout·응답 제한·요청 간격 적용)**
- [ ] collector error/status 계약 **(부분 구현: blocked·unsupported·temporarily_unavailable 상태 사용)**
- [x] 실제 판매처 HTML/JSON fixture test 기반

완료 기준: fixture 판매처를 대상으로 search/product/reviews/verify 응답을 반환한다.

## Phase 2: 실제 판매처 한 곳

- [x] ABC마트 검색 Adapter
- [x] ABC마트 검색을 `result-total/list` JSON 방식으로 전환
- [x] ABC마트·29CM `totalCount`, `hasNext` 공통 응답 매핑
- [x] ABC마트 요청 최소 1초 간격 제한
- [x] 판매처 Registry로 ABC마트 고정 분기 제거
- [x] 29CM `robots.txt`의 공개 검색·상품 경로 허용 범위 확인
- [x] 29CM 공개 검색 화면의 상품 응답 구조 확인
- [x] 29CM 상품 기본정보 Searcher와 fixture 단위 테스트
- [x] 29CM opt-in live smoke test
- [x] 무신사 현재 robots 정책과 일반 Collector 차단 범위 확인
- [x] 무신사 공개 검색 HTML의 서버 렌더링 JSON 구조 확인
- [x] 무신사 상품 기본정보 Searcher와 opt-in live smoke test
- [ ] 무신사 공식 상품 API·MCP·제휴 Feed 또는 별도 허가 확보 **(보류)**
- [ ] 무신사 장기 운영 수집 범위와 요청 빈도 확정 **(보류)**
- [ ] 상품 상세·가격·배송 Adapter
- [ ] 옵션·재고·사이즈표 Adapter
- [ ] 공개 리뷰와 사진 여부 Adapter
- [ ] 29CM·ABC마트 리뷰 상품 작업 큐와 제한된 고루틴 Worker Pool
- [ ] partial/blocked/unsupported 처리 **(부분 구현: 미등록 판매처 unsupported, 원격 오류 temporarily_unavailable)**
- [x] ABC마트 검색 opt-in live smoke test
- [x] 무신사 검색 opt-in live smoke test

완료 기준: 실제 공개 상품 후보를 찾아 출처와 수집 시각을 포함한 결과를 반환한다.

## Phase 3: Python Backend와 DB

- [ ] Python package와 설정 재정비 **(부분 구현: uv package, SQLAlchemy, Alembic, Collector client 구성 완료)**
- [x] Go Collector 검색 HTTP client
- [x] Pydantic Collector v1 검색 요청·응답 검증
- [x] 루트 Docker Compose의 PostgreSQL·영구 volume·health check 구성
- [x] SQLAlchemy 2 모델과 Alembic migration 기반 구성
- [x] 첫 상품 수집 테이블 migration 작성
- [x] Docker Compose에서 migration 실제 적용·재적용 검증
- [ ] 개발·테스트·배포 환경별 Compose 설정과 비밀값 주입 정책 **(부분 구현: 로컬 `.env.example`과 Compose 기본값·덮어쓰기 구성 완료)**
- [x] product/merchant-product/offer-snapshot/option/evidence 검색 결과 repository
- [x] 동일 판매처 상품 upsert와 수집 snapshot 추가 transaction 정책
- [x] ABC마트·29CM 실제 검색 결과 PostgreSQL 적재 검증
- [ ] 조사 세션과 작업 상태

완료 기준: Go 수집 결과가 검증·정규화되어 PostgreSQL에 재현 가능하게 저장된다.

## Phase 4: Redis·RabbitMQ 기반 수집 확장

### 4.1 실행 환경

- [x] Docker Compose에 RabbitMQ와 management UI 추가
- [x] Docker Compose에 Redis 추가
- [x] RabbitMQ·Redis health check와 영구 volume 구성
- [x] `.env.example`에 RabbitMQ·Redis 접속 설정과 로컬 기본값 추가
- [ ] 서비스가 환경 변수로 RabbitMQ·Redis 접속 정보를 주입받도록 구성 **(부분 구현: 인프라 컨테이너 설정 완료, Go·Python 접속 설정 미구현)**

### 4.2 작업 계약과 상태

- [ ] `CollectionJob` 공통 계약 정의
- [ ] 검색 페이지·상품 상세·리뷰·재검증을 구분하는 `CollectionTask` 계약 정의
- [ ] `taskId`, `merchant`, `operation`, `targetUrl`, `priority`, `attempt`, `requestedAt` 필드 확정
- [ ] 같은 판매처·작업·URL의 중복 등록을 막는 idempotency key 규칙 정의
- [ ] RabbitMQ exchange, queue, routing key 이름과 version 정책 정의
- [ ] retry 가능 오류와 즉시 실패 오류 구분
- [ ] 최대 재시도 횟수와 Dead Letter Queue 이동 규칙 정의
- [ ] `pending`, `running`, `success`, `partial`, `failed`, `cancelled` 상태 전이 정의

### 4.3 수집 작업 생성

- [ ] 검색 요청에 `page` 또는 판매처별 cursor를 전달하는 pagination 계약 추가
- [ ] `maxPages`, `maxProducts`, `requestBudget` 상한 추가
- [ ] 여러 검색어와 판매처를 입력받는 batch collection use case 구현
- [ ] 검색 결과에서 발견한 상품 URL을 상세 작업으로 등록
- [ ] 이미 성공한 URL과 현재 처리 중인 URL의 중복 작업 방지
- [ ] 정기 수집과 사용자 재검증 작업의 priority 차등 적용

### 4.4 Go Collector Worker

- [ ] RabbitMQ 작업을 소비하는 Go Worker entrypoint 추가
- [ ] `TaskQueue` port와 RabbitMQ adapter 분리
- [ ] 판매처별 최대 Worker 수 설정
- [ ] Redis 기반 판매처별 전체 요청 간격 제한
- [ ] 작업 timeout과 context 취소 처리
- [ ] 수집 성공 시 공통 `CollectorResult` 발행
- [ ] 실패 시 retry 또는 Dead Letter Queue 처리
- [ ] Worker 재시작 시 처리 중이던 미확인 작업 복구 검증

### 4.5 Python 저장 Worker와 진행 상태

- [ ] RabbitMQ의 `CollectorResult`를 소비하는 Python Worker 구현
- [ ] Pydantic 계약 검증 실패 결과를 DB에 저장하지 않는 처리
- [ ] 검증된 결과를 기존 repository transaction으로 저장
- [ ] `collection_jobs`, `collection_tasks` SQLAlchemy model 작성
- [ ] `collection_jobs`, `collection_tasks` Alembic migration 작성
- [ ] 성공·실패·중복·저장 상품 수와 소요시간 기록
- [ ] Redis에 짧은 수집 진행 상태를 저장하고 PostgreSQL에 최종 상태 보존
- [ ] Worker 또는 Backend 장애 후 작업 상태 복구 정책 구현

### 4.6 검증

- [ ] RabbitMQ 없이 실행하는 fixture 기반 Worker 단위 테스트
- [ ] Redis rate limiter와 중복 방지 단위 테스트
- [ ] RabbitMQ retry·ACK·Dead Letter Queue 통합 테스트
- [ ] ABC마트 여러 검색어·여러 페이지 batch 수집 opt-in smoke test
- [ ] 29CM 여러 검색어·여러 페이지 batch 수집 opt-in smoke test
- [ ] 동일 상품 재수집 시 상품 중복 없이 snapshot만 증가하는 DB 검증
- [ ] 판매처별 요청 상한을 넘지 않는지 검증
- [ ] 수집량, 신규 상품, 갱신 상품, 실패 작업, 소요시간 결과 보고

완료 기준: ABC마트와 29CM의 여러 검색어·페이지 작업이 RabbitMQ를 통해 제한된 Worker에 분배되고, Redis가 속도 제한과 진행 상태를 관리하며, Python Backend가 결과를 PostgreSQL에 안정적으로 저장한다.

## Phase 5: MCP와 AI Runtime Gateway

### 5.1 공통 MCP Server

- [ ] Python MCP SDK 의존성과 stdio server 구성
- [ ] MCP 도구 공통 오류·응답·근거 계약 정의
- [ ] `search_products`: PostgreSQL 조건 검색
- [ ] `get_product`: 상품·판매처·최신 가격·옵션·근거 조회
- [ ] `compare_products`: 후보 상품 공통 비교 데이터 반환
- [ ] `verify_offer`: RabbitMQ에 우선순위 재검증 작업 등록
- [ ] `get_verification_status`: 재검증 진행 상태 조회
- [ ] `get_evidence`: 가격·재고·상품 사실의 출처 조회
- [ ] LLM이 SQL을 직접 생성하지 않고 정해진 MCP 입력만 사용하도록 제한
- [ ] FAISS·pgvector 없이 PostgreSQL 조건 검색으로 PoC 검증

### 5.2 Codex·Claude Code 실행 경계

- [ ] Next.js가 직접 CLI를 실행하지 않도록 Agent Gateway 책임 정의
- [ ] 공통 `AI Runtime Adapter` 계약 정의
- [ ] Codex CLI adapter와 stream 중계 구현
- [ ] Claude Code CLI adapter와 stream 중계 구현
- [ ] 요청별 process, 대화 session, timeout, 취소 정책 정의
- [ ] Codex Plugin에서 공통 MCP 도구 선택 workflow 연결
- [ ] Claude Code에서 같은 MCP Server 연결 검증
- [ ] Agent별 인증 정보와 실행 권한을 브라우저에 노출하지 않는 구성
- [ ] MCP 도구 호출과 최종 답변의 근거 누락 contract test

완료 기준: 같은 MCP Server와 PostgreSQL을 사용하면서 Next.js 요청을 Codex CLI 또는 Claude Code CLI 중 하나에 전달하고, 상품 검색 결과를 근거와 함께 반환한다.

## Phase 6: Next.js Web

### 6.1 공통 기반

- [ ] Next.js + React + TypeScript 구조 **(부분 구현: `apps/purchase-web` 기본 scaffold 생성)**
- [x] Astryx MIT 라이선스와 Next.js 지원 여부 확인
- [x] Astryx `AI Chat Conversation` 템플릿을 채팅 화면 기준 UI로 선정
- [ ] Astryx core, neutral theme, CLI 의존성 추가
- [ ] Astryx 초기화와 theme provider 적용
- [ ] 기본 생성 화면을 Astryx 템플릿 기반 화면으로 교체
- [ ] 사용자 화면과 관리자 화면의 공통 navigation 구성
- [ ] 데스크톱·모바일 반응형 동작 확인

### 6.2 사용자 구매 채팅 화면 `/chat`

- [ ] Astryx AI Chat Conversation 템플릿을 가능한 그대로 적용
- [ ] 정적인 사용자 질문·Agent 답변 예제로 화면 검증
- [ ] Codex·Claude Code 실행 환경 선택 UI
- [ ] 채팅 입력, 메시지 목록, 응답 stream 표시
- [ ] DB 상품 검색과 MCP 도구 실행 상태 표시
- [ ] 오른쪽 상품 비교·근거 panel
- [ ] 상품 가격·옵션·재고·판매처·마지막 수집 시각 표시
- [ ] 구매 전 재검증 요청과 상태 표시
- [ ] 오류·취소·timeout 상태 표시

### 6.3 관리자 수집 화면 `/admin/collections`

- [ ] 판매처 선택 UI
- [ ] 검색어·최대 페이지·최대 상품·Worker 상한 입력 UI
- [ ] 수집 작업 생성·중단 UI
- [ ] 작업별 진행률과 성공·실패·대기 개수 표시
- [ ] RabbitMQ Queue와 Dead Letter Queue 요약 표시
- [ ] Redis·RabbitMQ·Go Worker·Python Worker 상태 표시
- [ ] 실패 작업 원인과 재시도 이력 표시
- [ ] 판매처별 마지막 수집 시각과 수집 상품 수 표시
- [ ] 일반 사용자에게 관리자 화면을 노출하지 않는 접근 정책

### 6.4 API와 검증

- [ ] FastAPI research session API
- [ ] Agent Gateway API
- [ ] 수집 작업 생성·조회·취소 API
- [ ] SSE 또는 stream 기반 Agent 응답 전달
- [ ] SSE 기반 수집 진행 상태 전달
- [ ] UI component test
- [ ] 채팅 → MCP → PostgreSQL 검색 E2E test
- [ ] 관리자 화면 → RabbitMQ → 수집 → PostgreSQL 저장 E2E test

완료 기준: 사용자는 `/chat`에서 Codex 또는 Claude Code의 근거 기반 구매 답변을 받고, 운영자는 `/admin/collections`에서 백그라운드 수집 작업과 진행 상태를 관리한다.

## Phase 7: 리뷰 분석과 비교

- [ ] 개인정보 제거와 최소 저장 정책
- [ ] 규칙 기반 size/foot-width/fit signal 추출
- [ ] 선택적 LLM structured extraction
- [ ] confidence와 derived 표시
- [ ] 필수 조건 filter
- [ ] 설명 가능한 가중치 점수
- [ ] 주장과 evidence 연결

완료 기준: 후보 3개를 점수 구성, 근거, 주의사항과 함께 비교한다.

## Phase 8: 확장

- [x] 두 번째 운영 대상 판매처 29CM Adapter
- [ ] 동일 상품 매칭
- [ ] 사용자 치수·선호 프로필
- [ ] 검색·수집 캐시 정책
- [ ] 판매처별 HTML fixture 변경 비교 자동화
- [ ] live smoke에서 필수 field 누락과 DOM 변경 자동 감지
- [ ] JSON-LD·공개 JSON 우선 추출과 HTML selector fallback
- [ ] parser 수정안 자동 생성과 회귀 테스트 실행
- [ ] 자동 수정안의 사람 승인·배포 절차
- [ ] 주문·결제 지원 범위 재검토

## Phase 9: 여러 AI 실행 환경 지원

- [ ] Phase 5의 공통 `AI Runtime Adapter`를 API·로컬 모델까지 확장
- [ ] Codex CLI 연결부 회귀 테스트
- [ ] Claude Code 연결부 회귀 테스트
- [ ] Codex·Claude Code 공통 규칙과 명령어 원본 위치 결정
- [ ] Rulesync와 AgentSync 최소 예제 비교
- [ ] 선택한 설정 동기화 도구의 CI 검증 추가
- [ ] Ollama REST API 연결부
- [ ] llama.cpp OpenAI 호환 API 연결부
- [ ] GPU 모델 서버 연결부
- [ ] 모델별 한국어 이해와 MCP 기능 선택 평가
- [ ] 모델별 근거 왜곡·누락과 JSON 계약 평가
- [ ] 응답 속도·동시 사용자·GPU 메모리·운영 비용 비교
- [ ] 모델 및 실행 도구 라이선스 점검

완료 기준: 같은 Next.js 화면과 MCP 기능을 유지한 채 설정만 바꿔 Codex, Claude Code, 로컬 모델, GPU 서버 중 지원 대상 환경을 선택해 실행할 수 있다.

## 제출 준비: 대회 규정 대응

이 영역은 현재 기능 개발을 막는 선행 작업이 아니다. 제출 시점의 라이선스, 외부 구성요소, AI 사용, 판매처 정책과 참가 자격은 [오픈소스 개발자대회 규정 대응 체크리스트](오픈소스_개발자대회_규정_대응_체크리스트.md)에서 별도로 관리한다.

## 구현 우선순위

```text
계약
→ Go fixture collector
→ ABC마트·29CM 실제 판매처 Adapter
→ Python DB 적재
→ Redis·RabbitMQ 기반 batch 수집
→ PostgreSQL 상품 데이터 확보
→ MCP 상품 검색
→ Codex·Claude Code Agent Gateway
→ Astryx 채팅 화면과 수집 관리 화면
→ 리뷰 분석·비교와 구매 전 재검증
→ 여러 AI 실행 환경
```
