# Purchase Research Agent 개발 진행 관리

최종 갱신일: 2026-07-19

## 목적

이 문서는 구현 계획의 완료 근거, 코드 위치, 검증 결과, 개발 중 발생한 문제와 해결 과정을 추적한다. 앞으로 할 일은 [구현 TODO](../planning/Purchase_Research_Agent_TODO.md)에서 관리하고, 완료된 작업의 증거는 이 문서에서 관리한다.

## 기록 규칙

- 상위 작업은 TODO에서, 세부 작업은 이 문서의 영역별 체크리스트에서 관리한다.
- 세부 작업은 골격, 정상 경로, 실패 경로, 테스트, 문서, 검증처럼 독립적으로 확인 가능한 크기로 나눈다.
- 세부 항목 자체가 실제로 끝났으면 `[x]`, 끝나지 않았거나 검증하지 못했으면 `[ ]`로 표시한다.
- 영역은 모든 필수 세부 항목과 완료 조건을 충족한 경우에만 `완료`로 판정한다.
- 구현 위치는 `경로:시작 줄`과 class/function/method/schema 이름을 함께 기록한다.
- 하나의 작업이 여러 파일에 걸치면 핵심 진입점과 테스트 위치를 각각 기록한다.
- 줄 번호가 바뀌는 코드 변경에서는 관련 구현 근거의 줄 번호도 함께 갱신한다.
- 문제는 숨기지 않고 증상, 원인, 해결, 남은 위험을 기록한다.
- 검증하지 못한 항목은 통과로 기록하지 않고 이유와 후속 조치를 남긴다.

## 완성도 판정 기준

| 상태 | 판정 기준 |
|---|---|
| 미착수 | 계획만 있고 코드·계약·테스트 결과물이 없다. |
| 부분 구현 | 하나 이상의 세부 항목은 끝났지만 필수 구현이나 테스트가 남아 있다. |
| 구현 완료·검증 필요 | 기능 코드는 갖춰졌지만 테스트, 계약 검증, 문서 또는 실행 확인이 남아 있다. |
| 완료 | 필수 세부 항목, 실패 경로, 테스트, 문서, 검증 명령이 모두 완료되었다. |
| 차단 | 외부 결정이나 환경 문제 때문에 다음 세부 항목으로 진행할 수 없다. |

`완료`는 합의한 완료 조건을 충족했다는 뜻이며 결함이 절대 없다는 의미는 아니다. 이후 결함이 확인되면 관련 항목을 다시 `[ ]`로 바꾸고 문제 기록을 추가한다.

## 현재 진행 요약

| 구현 영역 | 상태 | 현재 범위 | 다음 완료 조건 |
|---|---|---|---|
| 구조와 계약 | 부분 구현 | 역할 분리, Collector v1 스키마와 예제 작성 | 첫 판매처와 요청 계약 확정, Go/Python DTO 매핑 |
| Go Collector 기반 | 부분 구현 | module, 설정, HTTP lifecycle, health·실제 검색 endpoint | 공통 URL 검증, retry, 동시성 제한, 나머지 operation |
| 실제 판매처 Adapter | 부분 구현 | 판매처 Registry와 ABC마트·29CM 공개 검색, 무신사 검색 PoC | 29CM·ABC마트 상품 상세·옵션·리뷰 구현 |
| Python Backend와 DB | 부분 구현 | 패키지 골격과 미구현 MCP entrypoint | Collector 검증·정규화·저장 수직 흐름 |
| 리뷰 분석과 비교 | 미착수 | 구현 코드 없음 | 후보 3개에 점수·근거·주의사항 연결 |
| MCP와 Codex Plugin | 부분 구현 | Plugin manifest와 workflow 초안 | 실제 MCP tool과 application use case 연결 |
| Next.js Web | 미착수 | PoC Codex Gateway 경계와 README만 정리 | Codex 응답 stream 기반 핵심 사용자 흐름 |
| 공통 품질·운영 | 부분 구현 | Go 단위 테스트 일부만 존재 | 자동화된 전체 검증과 개발 실행 환경 |

## 영역별 상세 체크리스트

### 0. 구조와 공통 계약

상태: **부분 구현**

- [x] Go Collector, Python Backend, Codex Plugin, Next.js 책임 분리
- [x] repository 기본 디렉토리 구조 작성
- [x] 검색 요청 JSON Schema 초안 작성
- [x] 수집 결과 JSON Schema 초안 작성
- [x] 재검증 결과 JSON Schema 초안 작성
- [x] success, partial, changed 예제 작성
- [x] provenance 누락 무효 예제 작성
- [x] 1차 개발 판매처를 ABC마트와 29CM로 확정하고 무신사 확장 보류
- [ ] 상품 상세 수집 요청 계약 작성
- [ ] 리뷰 수집 요청과 pagination 계약 작성
- [ ] 현재 offer 재수집 요청·응답 계약 작성
- [ ] status별 products, warnings, errors 불변조건 확정
- [ ] 재검증 책임 확정: Go 현재 상태 수집, Python snapshot 비교
- [ ] Go transport DTO와 Python Pydantic model 매핑 확정
- [ ] 유효 예제 자동 schema 검증
- [ ] 무효 예제가 예상대로 실패하는 자동 검증
- [ ] v1 호환성·변경 정책 최종 검토

완료 조건: 요청과 응답 예제만으로 모든 operation, 실패 상태, Go/Python 책임을 설명할 수 있고 자동 계약 검증이 통과해야 한다.

### 1. Go Collector 기반

상태: **부분 구현**

- [x] Go module 생성
- [x] Collector process configuration 구조와 기본값 구현
- [x] timeout 환경변수 parsing과 양수 검증 구현
- [x] configuration 기본값 단위 테스트
- [x] configuration 환경변수 override 단위 테스트
- [x] configuration 잘못된 값·빈 주소 실패 테스트
- [x] HTTP server와 route mux 생성
- [x] health GET 정상 응답 구현
- [x] health 비허용 method 405 응답 구현
- [x] health handler 정상·실패 테스트
- [x] signal context와 graceful shutdown 구현
- [x] listen 실패와 graceful shutdown 테스트
- [x] 모든 기존 type/function/test의 한국어 주석 정비
- [x] 검색용 Go transport request/response DTO 구현
- [x] 검색용 merchant adapter interface 구현
- [x] 판매처 이름으로 Searcher를 선택하는 Registry 구현
- [ ] merchant/domain allowlist 구현
- [ ] URL scheme, host, port 검증 구현
- [ ] DNS·redirect 이후 private IP와 localhost 차단
- [ ] 공통 HTTP client와 response body 상한 구현
- [x] ABC마트 검색 timeout 설정
- [ ] idempotent 요청 retry 상한과 backoff 구현
- [x] ABC마트 요청 최소 1초 간격 제한
- [x] 29CM 요청 최소 1초 간격 제한
- [ ] 다른 판매처 추가 시 판매처별 rate limiter 정책 구현
- [ ] 판매처별 concurrency limiter 구현
- [ ] blocked, unsupported, temporarily_unavailable 상태 매핑 **(부분 구현: 미등록 판매처 unsupported, 원격 오류 temporarily_unavailable)**
- [ ] request ID와 merchant를 포함한 구조화 로그
- [x] 저장된 실제 ABC마트 검색 HTML fixture 구성
- [x] 실제 ABC마트 search endpoint 구현과 handler·adapter test
- [x] 저장된 29CM 검색 JSON fixture와 adapter test
- [ ] fixture product endpoint 구현과 contract test
- [ ] fixture reviews endpoint 구현과 contract test
- [ ] fixture current-offer endpoint 구현과 contract test

완료 조건: 외부 네트워크 없이 fixture merchant의 네 operation이 계약대로 응답하고 정상·부분 실패·차단·일시 실패 테스트가 모두 통과해야 한다.

### 2. 실제 판매처 한 곳

상태: **부분 구현**

- [x] ABC마트의 로그인 없는 공개 검색 접근 확인
- [ ] 상품 상세·옵션·리뷰 공개 접근 범위 확인
- [x] ABC마트 검색 host의 `robots.txt`에서 `User-agent: * / Allow: /` 확인
- [x] 개발·smoke test 요청에 최소 1초 간격 적용
- [ ] 이용 정책과 운영 요청 빈도 제한 최종 확정
- [x] 첫 판매처를 ABC마트로 결정하고 1차 지원 범위를 공개 검색으로 제한
- [x] 1차 목표 판매처를 ABC마트와 29CM로 변경하고 무신사 추가 개발 보류
- [x] 29CM 일반 Agent 공개 검색·상품 경로 허용 범위 확인
- [x] 29CM 공개 검색 상품 응답 구조 확인
- [x] 29CM 상품 기본정보 Searcher 구현
- [x] 29CM fixture 단위 테스트와 opt-in live smoke test
- [x] 무신사 현재 robots 정책과 일반 Collector 차단 범위 재확인
- [x] 무신사 공개 검색 HTML의 서버 렌더링 JSON 파서 구현
- [x] 무신사 상품 기본정보 Searcher와 opt-in live smoke test 구현
- [ ] 무신사 공식 상품 API·MCP·제휴 Feed 또는 별도 허가 확보 **(외부 결정 필요)**
- [ ] 무신사 장기 운영 수집 범위와 요청 빈도 확정
- [x] ABC마트 검색 adapter 구현
- [ ] 상품 상세·가격·배송 adapter 구현
- [ ] 옵션·재고·사이즈표 adapter 구현
- [ ] 공개 리뷰 pagination adapter 구현
- [ ] 상품 단위 리뷰 작업 큐와 제한된 고루틴 Worker Pool 구현
- [ ] 리뷰 작성자 식별정보 제외 검증
- [ ] 리뷰 이미지를 저장하지 않고 `hasImage`만 반환하는지 검증
- [x] 검색 결과 provenance와 collector version 포함 검증
- [ ] partial, blocked, unsupported 상태 처리
- [x] 저장된 실제 ABC마트 HTML 기반 parser 회귀 테스트
- [x] 낮은 빈도의 opt-in ABC마트 검색 live smoke test

완료 조건: 공개 정보만 사용해 실제 후보를 반환하고, 접근 제한을 우회하지 않으며, 모든 판매처 사실에 provenance가 연결되어야 한다.

### 3. Python Backend와 PostgreSQL

상태: **부분 구현**

- [x] `src` layout 패키지 골격 생성
- [x] `pyproject.toml`과 MCP script entrypoint 초안 작성
- [x] 미구현 MCP 실행을 명시적으로 차단하는 placeholder 작성
- [x] `uv.lock`과 서비스 내부 `.venv` 기반 DB 의존성 고정
- [ ] 환경설정 model과 `.env.example` 정비
- [ ] Collector transport Pydantic model 구현
- [ ] Collector 응답 schema validation 구현
- [ ] Collector HTTP client와 timeout 구현
- [ ] Collector 오류·상태의 application 오류 매핑
- [ ] 공통 product/offer/option/review domain model 구현
- [ ] transport DTO에서 domain model 정규화
- [x] PostgreSQL local compose 구성 (`compose.yaml`: PostgreSQL 16, volume, health check)
- [x] SQLAlchemy 2 모델과 Alembic 첫 migration 작성 (`services/research-backend/migrations/versions/20260721_0001_initial_collection_tables.py`)
- [x] Docker Compose에서 migration 실제 적용·재적용 검증 (PostgreSQL 16에서 두 번 실행 후 5개 테이블 확인)
- [x] 루트 `.env.example`로 PostgreSQL 포트·DB 이름·사용자·비밀번호 Compose 덮어쓰기 구성
- [ ] research session repository 구현
- [ ] product/offer/option repository 구현
- [ ] snapshot/evidence repository 구현
- [ ] 중복 수집 식별과 upsert 정책 구현
- [ ] transaction·rollback 정책 구현
- [ ] 추천 snapshot과 verification snapshot 분리 저장
- [ ] unit test: schema validation과 정규화
- [ ] integration test: Collector stub과 PostgreSQL 저장

완료 조건: fixture Collector 결과가 Python에서 검증·정규화되고 PostgreSQL에 중복 없이 재현 가능하게 저장되어야 한다.

### 4. 리뷰 분석과 상품 비교

상태: **미착수**

- [ ] 리뷰 최소 저장·개인정보 제거 정책 구현
- [ ] size signal 규칙 기반 추출
- [ ] foot-width signal 규칙 기반 추출
- [ ] fit/comfort signal 규칙 기반 추출
- [ ] signal별 source review evidence 연결
- [ ] derived와 confidence 저장
- [ ] 선택적 LLM structured extraction 계약
- [ ] LLM 결과 schema validation과 실패 fallback
- [ ] 필수 조건 filter 구현
- [ ] 사용자 우선순위 가중치 모델 구현
- [ ] 설명 가능한 점수 구성 구현
- [ ] 상품 주장과 evidence 연결
- [ ] 근거 부족과 불확실성 표시
- [ ] 추천 snapshot 생성
- [ ] 과거 추천과 최신 offer 차이 계산
- [ ] 단위 테스트: 추출·filter·score·diff 경계값

완료 조건: 후보 3개를 필수 조건, 점수 구성, 출처, 수집 시각, 주의사항과 함께 비교하고 동일 입력으로 결과를 재현할 수 있어야 한다.

### 5. Next.js Codex Gateway, MCP와 Codex Plugin

상태: **부분 구현**

- [x] Plugin manifest 작성
- [x] Plugin MCP 실행 설정 초안 작성
- [x] 구매 질문·근거·재검증 skill workflow 초안 작성
- [ ] Next.js server 전용 Codex Gateway
- [ ] Codex process/app-server 실행과 JSON event stream 중계
- [ ] 대화 session, timeout, 취소와 동시 요청 상한
- [ ] 장기 서비스용 OpenAI API Agent 교체 경계
- [ ] Python MCP SDK 의존성 추가
- [ ] stdout protocol과 stderr log 분리
- [ ] `search_products` 구현과 테스트
- [ ] `collect_product` 구현과 테스트
- [ ] `collect_reviews` 구현과 테스트
- [ ] `compare_products` 구현과 테스트
- [ ] `verify_offer` 구현과 테스트
- [ ] `get_evidence` 구현과 테스트
- [ ] 공식 사실·리뷰 신호·Agent 추론 응답 구분
- [ ] stale, blocked, partial 상태 사용자 설명 검증
- [ ] 선택 상품 응답 전 재검증 workflow 연결
- [ ] Plugin validation과 로컬 설치 검증
- [ ] Codex E2E: 질문 구체화부터 재검증까지

완료 조건: Codex에서 구매 조건 질문, 실제 후보 조사, 근거 비교, 선택 offer 재검증을 끊김 없이 수행해야 한다.

### 6. Next.js Web

상태: **미착수**

- [x] Next.js Web의 예정 책임과 최종 사용자 경로 README 작성
- [ ] Next.js, React, TypeScript project 구성
- [ ] 공통 API type과 client 구성
- [ ] 구매 조건 대화 UI
- [ ] 구조화 조건 profile panel
- [ ] research session FastAPI endpoint
- [ ] SSE 진행 상태 endpoint와 client
- [ ] 판매처별 진행·부분 실패 표시
- [ ] 상품 비교 UI와 점수 구성 표시
- [ ] evidence panel과 출처·수집 시각 표시
- [ ] 선택 상품 재검증 UI
- [ ] 추천 snapshot과 최신 snapshot diff 표시
- [ ] loading, empty, error, stale 상태 처리
- [ ] component와 API integration test
- [ ] 핵심 구매 흐름 E2E test

완료 조건: 브라우저에서 조건 입력부터 근거 비교와 구매 전 재검증까지 수행하고 실패·오래된 정보 상태를 명확히 확인할 수 있어야 한다.

### 7. 공통 품질과 운영

상태: **부분 구현**

- [x] Go health handler unit test
- [x] 현재 Go 범위 `go test ./...` 통과
- [x] 현재 Go 범위 `go test -race ./...` 통과
- [x] 현재 Go 범위 `go vet ./...` 통과
- [ ] repository 공통 개발 명령 또는 Makefile/Taskfile
- [ ] JSON Schema 자동 검증 명령
- [ ] Go format, test, vet CI
- [ ] Python format, lint, typecheck, test CI
- [ ] React lint, typecheck, test CI
- [ ] PostgreSQL integration test CI
- [ ] 실제 판매처 smoke test의 기본 CI 제외와 opt-in 설정
- [ ] secret scan과 `.env` 추적 방지 검증
- [ ] dependency vulnerability 검증
- [ ] 관측 가능한 request ID, 상태, latency 로그
- [ ] README의 실제 실행·검증 명령 갱신

완료 조건: 새 checkout에서 문서화된 명령으로 build·test·lint·contract 검증을 재현하고 실제 판매처 요청 없이 기본 CI가 통과해야 한다.

## 구현 근거

| 작업 | 상태 | 구현 위치 | 검증 |
|---|---|---|---|
| Go module 생성 | 완료 | `services/collector/go.mod:1` module 선언 | `go test ./...`, `go vet ./...` 통과 |
| Collector 설정 로딩과 timeout 검증 | 완료 | `services/collector/internal/config/config.go:29` `Load` | `services/collector/tests/unit/config/config_test.go:20`, `:36`, `:58`; test·race·vet 통과 |
| Collector HTTP 서버 생성 | 완료 | `services/collector/internal/transport/http/server.go:22` `NewServer` | health handler와 lifecycle test·race·vet 통과 |
| Collector 실행과 graceful shutdown | 완료 | `services/collector/internal/transport/http/server.go:63` `Server.Run`, `:78` `Server.Serve`, `services/collector/cmd/server/main.go:22` `run` | `services/collector/tests/unit/http/server_test.go:150`; test·race·vet 통과 |
| Health endpoint | 완료 | `services/collector/internal/transport/http/health.go:14` `healthHandler` | `services/collector/tests/unit/http/server_test.go:28` 테스트 통과 |
| 기존 Go 한국어 주석 | 완료 | `services/collector/cmd/server/main.go`, `internal/config`, `internal/transport/http`의 기존 type/function/test | `gofmt`, `go test`, `go vet` 통과 |
| 검색 공통 DTO와 검증 | 완료 | `services/collector/internal/collector/search.go:38` `Searcher`, `:43` `SearchRequest`, `:55` `ApplyDefaults`, `:68` `Validate` | `services/collector/tests/unit/collector/search_test.go`; test·race·vet 통과 |
| ABC마트 실제 검색 adapter | 완료 | `services/collector/internal/merchants/abcmart/search.go:101` `Searcher.Search`, `:187` `normalizeItem`, `:224` `parseSizeStocks` | 저장 JSON 단위 테스트와 실제 검색 통과; `totalCount=1650`, `hasNext=true` 확인 |
| ABC마트 요청 간격 제한 | 완료 | `services/collector/internal/merchants/abcmart/search.go:160` `Searcher.waitForTurn` | `services/collector/tests/unit/abcmart/search_test.go:90`; test·race 통과 |
| ABC마트 검색 HTTP endpoint | 완료 | `services/collector/internal/transport/http/search.go:17` `searchHandler`, `:33` `ServeHTTP`, `internal/transport/http/server.go:47` route | `services/collector/tests/unit/http/server_test.go:55`; test·race·vet 통과 |
| 판매처 Search Registry | 완료 | `services/collector/internal/collector/registry.go:8` `SearchRegistry`, `:34` `Search` | `services/collector/tests/unit/collector/registry_test.go:19`, `:35`; 테스트 통과 |
| 무신사 공개 검색 Adapter | 완료 | `services/collector/internal/merchants/musinsa/search.go` `Searcher`, `Search`, `parseSearchItems` | 단위 테스트와 `MUSINSA_LIVE_SMOKE=1` 실제 상품 3개 변환 통과 |
| Registry HTTP 연결 | 완료 | `services/collector/internal/transport/http/search.go` `newSearchHandler`, Registry 호출 | ABC마트·무신사 등록과 HTTP 전달 테스트 통과 |
| 검색 요청 계약 초안 | 초안 | `contracts/collector/v1/search-request.schema.json:1` `Collector Search Request v1` | 예제 존재, 자동 검증 재확인 필요 |
| 수집 결과 계약 초안 | 초안 | `contracts/collector/v1/collector-result.schema.json:1` `Collector Result v1` | `totalCount`, `hasNext` 선택 필드 추가; 성공·부분 성공 예제 `check-jsonschema` 통과 |
| 재검증 결과 계약 초안 | 초안 | `contracts/collector/v1/verification-result.schema.json:1` `Collector Verification Result v1` | 변경 예제 존재, 책임 경계 재검토 필요 |
| MCP entrypoint | placeholder | `services/research-backend/src/research_backend/interfaces/mcp/server.py:8` `main` | import 가능, 실행 시 미구현 오류 반환 |

## 문제 기록

| 날짜 | 영역 | 증상 | 원인 | 해결 또는 후속 조치 | 상태 |
|---|---|---|---|---|---|
| 2026-07-16 | Go 개발환경 | `vulncheck`의 `Prompt` 값을 지원하지 않는다는 경고 | 에디터 설정과 기존 `gopls` 버전 불일치 | `gopls v0.23.0`으로 업데이트하고 VS Code를 다시 로드 | 해결 |
| 2026-07-16 | Go 개발환경 | 터미널에서 `gopls` 명령을 찾지 못함 | `/Users/iseoin/go/bin`이 shell `PATH`에 없음 | `$(go env GOPATH)/bin`을 `PATH`에 추가 | 해결 |
| 2026-07-16 | 계약 검증 | `uvx check-jsonschema` 자동 검증을 현재 실행 환경에서 완료하지 못함 | `uvx` tool directory 권한 오류 후 실행기 panic 발생 | 고정된 로컬 검증 명령 또는 CI job으로 재현 가능한 계약 검증 추가 | 미해결 |
| 2026-07-16 | HTTP server test | 실제 loopback listener bind가 `operation not permitted`로 실패 | 격리된 실행 환경이 test process의 socket bind를 제한 | `Server.serve`를 분리하고 실제 socket 없는 `blockingListener` test double로 lifecycle 검증 | 해결 |
| 2026-07-16 | HTTP server refactor | `serve` 분리 직후 `undefined: err` compile 오류 | 기존 `Run` 지역변수를 새 method에서도 재사용 | `serve` 종료 결과를 method 내부 지역변수로 선언하고 전체 Go 검증 재실행 | 해결 |
| 2026-07-16 | Go 검증 명령 | 저장소 root의 `go test ./...`가 main module을 찾지 못함 | Go module이 `services/collector` 하위에 위치 | `services/collector`에서 Go 검증 명령을 실행하고 작업 기록에 실행 위치 명시 | 해결 |
| 2026-07-16 | 실제 판매처 조사 | 격리 환경에서 판매처 host DNS 조회 실패 | 기본 실행 환경의 외부 네트워크 제한 | 승인된 낮은 빈도 요청으로 공개 페이지와 robots 정책 확인 | 해결 |
| 2026-07-16 | 판매처 정책 | 무신사 검색 구현 후 일반 Collector user-agent가 robots에서 전체 차단됨을 확인 | 무신사 `robots.txt`의 `User-agent: * / Disallow: /` 정책 | 무신사 구현을 제거하고 `User-agent: * / Allow: /`인 ABC마트로 전환 | 해결 |
| 2026-07-16 | 무신사 parser test | 폐기 전 최소 HTML의 `__NEXT_DATA__` JSON 해석 실패 | 테스트 자료를 줄이는 과정에서 닫는 중괄호가 하나 많았음 | JSON을 수정해 원인을 확인했으나 robots 정책 확인 후 무신사 코드는 최종 제거 | 해결 |
| 2026-07-16 | ABC마트 검색 조건 | 결과 개수 1개와 270 사이즈를 함께 요청하면 앞 상품이 맞지 않아 결과가 비어 보임 | 서버에서 1개만 받은 뒤 Collector가 사이즈 조건을 적용함 | 서버에서는 최소 30개를 받은 뒤 조건을 적용하고 마지막에 요청 개수만큼 잘라 반환 | 해결 |
| 2026-07-18 | Collector 구조 | 구현하지 않은 기능의 빈 폴더와 `.gitkeep` 때문에 현재 사용 파일을 구분하기 어려움 | 초기 설계용 placeholder를 실제 구현 후에도 유지함 | `browser`, `observability`, 빈 `fixture`·`musinsa`와 불필요한 `.gitkeep` 제거, 현재 구조 문서화 | 해결 |
| 2026-07-19 | 무신사 데이터 접근 | 무신사 검색 페이지는 사용자 브라우저에서 열리지만 자체 Go Collector로 자동 수집할 수 없음 | 지정된 Agent만 허용하고 일반 User-agent는 전체 경로를 차단하는 robots 정책 | User-agent 위장은 제외하고 정책 Adapter로 `blocked` 반환. 공식 상품 API·MCP·제휴 Feed 또는 별도 허가 확보를 후속 작업으로 등록 | 부분 해결 |
| 2026-07-19 | 무신사 소량 검색 PoC | 리뷰뿐 아니라 검색어 기반 상품 후보가 필요함 | 검색 페이지가 SPA이지만 초기 상품은 HTML의 `__NEXT_DATA__`에 서버 렌더링됨 | 일반 User-agent와 최소 1초 간격을 사용하는 Searcher 구현, 실제 `구두` 상품 3개 smoke test 통과 | PoC 해결 |
| 2026-07-20 | ABC마트 검색 원본 선택 | 상품 전체 수와 다음 페이지 여부를 HTML 파서로 정확히 알 수 없음 | 기존 구현이 화면용 HTML 조각만 해석함 | 공개 검색 화면의 `result-total/list` JSON으로 전환하고 `SEARCH_COUNT`, `PAGE.finalPageNo`를 공통 결과에 매핑 | 해결 |

### 2026-07-16 Go Collector 기본 골격 테스트와 한국어 주석 정비

- 진행상황: configuration 정상·실패 경로와 HTTP listen 실패·graceful shutdown 테스트를 완료했다.
- 구현 위치:
  - `services/collector/internal/config/config.go:29` `Load`: 환경변수 설정 로딩과 실패 계약
  - `services/collector/tests/unit/config/config_test.go:20` `TestLoadDefaults`: 기본값 검증
  - `services/collector/tests/unit/config/config_test.go:36` `TestLoadOverrides`: 환경변수 override 검증
  - `services/collector/tests/unit/config/config_test.go:58` `TestLoadRejectsInvalidValues`: 빈 주소와 잘못된 timeout 거부 검증
  - `services/collector/internal/transport/http/server.go:63` `Server.Run`: listener 생성과 오류 wrapping
  - `services/collector/internal/transport/http/server.go:78` `Server.Serve`: serve와 graceful shutdown lifecycle
  - `services/collector/tests/unit/http/server_test.go:150` `TestServerLifecycle`: listen 실패와 context 취소 종료 검증
- 발생 문제: 격리 환경에서 실제 socket bind가 금지됐고 refactor 중 지역변수 범위 compile 오류가 발생했다.
- 원인: lifecycle test가 OS network에 의존했고 기존 `err` 선언 범위가 `Run`에 한정돼 있었다.
- 해결: 준비된 listener를 받는 `Server.serve`를 분리하고 `blockingListener` test double을 사용했으며 종료 오류를 method 내부에서 선언했다.
- 남은 위험: 실제 OS socket을 사용한 process-level graceful shutdown integration test는 아직 없으며 이후 opt-in integration 계층에서 보강한다.
- 검증:
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go test -count=1 ./...`: 통과
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go test -race ./...`: 통과
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go vet ./...`: 통과

### 2026-07-16 ABC마트 실제 상품 검색

- 진행상황: ABC마트 공개 검색 결과에서 상품 번호, 이름, 브랜드, 가격, URL과 공개 사이즈별 재고를 수집하는 검색 endpoint를 구현했다. 상품 상세와 리뷰 본문은 아직 수집하지 않는다.
- 구현 위치:
  - `services/collector/internal/collector/search.go:38` `Searcher`: 판매처 검색 공통 인터페이스
  - `services/collector/internal/collector/search.go:43` `SearchRequest`: 검색 요청 DTO
  - `services/collector/internal/collector/search.go:115` `SearchResult`: 검색 결과 DTO
  - `services/collector/internal/merchants/abcmart/search.go:79` `Searcher.Search`: 실제 ABC마트 공개 검색 요청과 조건 필터
  - `services/collector/internal/merchants/abcmart/search.go:160` `Searcher.waitForTurn`: ABC마트 요청 사이 최소 1초 간격 적용
  - `services/collector/internal/merchants/abcmart/search.go:192` `buildSearchURL`: ABC마트 검색 parameter 생성
  - `services/collector/internal/merchants/abcmart/search.go:216` `checkRedirect`: A-RT 외부 redirect 차단
  - `services/collector/internal/merchants/abcmart/search.go:229` `parseSearchItems`: 상품 HTML parsing
  - `services/collector/internal/merchants/abcmart/search.go:306` `parseSizeStocks`: 사이즈별 공개 수량 parsing
  - `services/collector/internal/merchants/abcmart/search.go:346` `toProduct`: 공통 상품·옵션 결과 변환
  - `services/collector/tests/unit/abcmart/search_test.go:26` `TestSearcherSearch`: 저장 HTML parsing, 검색 변환과 조건 적용 검증
  - `services/collector/tests/unit/abcmart/search_test.go:90` `TestSearcherStopsCanceledRateLimitWait`: 요청 대기 취소와 추가 HTTP 호출 방지 검증
  - `services/collector/tests/integration/abcmart_live_test.go:14` `TestABC마트실제검색`: 실제 ABC마트 opt-in 검증
  - `services/collector/internal/transport/http/search.go:32` `searchHandler.ServeHTTP`: 검색 HTTP endpoint
  - `services/collector/internal/transport/http/server.go:25`: `/internal/v1/collect/search` route
  - 당시 사용한 ABC마트 저장 HTML fixture는 2026-07-20 JSON 전환 후 제거하고 `search-products.json`으로 교체했다.
- 발생 문제: 최초 선택한 무신사가 일반 Collector user-agent를 robots에서 차단했고, ABC마트 검색에서 결과 개수를 먼저 1개로 제한하면 뒤쪽의 요청 사이즈 상품을 찾지 못했다.
- 원인: 무신사 wildcard 정책이 전체 경로를 금지했으며, ABC마트 결과에는 서버 개수 제한 뒤에 Collector의 사이즈 조건이 적용됐다.
- 해결: 무신사 코드를 제거하고 wildcard 접근을 허용하는 ABC마트로 전환했다. ABC마트에서는 최소 30개를 수집한 뒤 조건을 적용하고 최종 반환 개수를 제한하며, 요청 사이에는 최소 1초 간격을 적용했다.
- 당시 남은 위험: ABC마트 HTML class 변경 위험이 있었으며, 이 문제는 2026-07-20 JSON 전환으로 줄였다. JSON 필드 변경, 상품 상세·리뷰, retry, 동시성 상한 위험은 남아 있다.
- 검증:
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go test -count=1 ./...`: 통과
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go test -race ./...`: 통과
  - `services/collector`에서 `GOCACHE=/tmp/purchase-research-go-cache go vet ./...`: 통과
  - `services/collector`에서 `ABCMART_LIVE_SMOKE=1 GOCACHE=/tmp/purchase-research-go-cache go test -count=1 -run TestABC마트실제검색 -v ./tests/integration`: `페니 로퍼`, 공개 옵션 7개 확인

### 2026-07-18 Collector 1차 폴더 정리

- 진행상황: 현재 실행에 사용되지 않는 빈 placeholder 폴더와 `.gitkeep`을 제거하고, Collector 구조를 실제 구현 기준으로 축소했다.
- 제거 대상: `internal/browser`, `internal/observability`, `internal/merchants/fixture`, 빈 `internal/merchants/musinsa`, 빈 `testdata/musinsa`, 불필요한 `.gitkeep` 7개
- 유지 대상: 실행 서버, 공통 수집 형식, 설정, ABC마트 adapter, HTTP transport, ABC마트 testdata, unit·integration tests
- 발생 문제: planned 구조와 구현된 구조가 같은 디렉토리에 섞여 현재 사용 여부를 파악하기 어려웠다.
- 해결: planned 기능은 TODO에만 남기고 실제 코드가 생길 때 폴더를 생성하는 규칙으로 정리했다.
- 남은 위험: 당시에는 무신사 수집을 중단했으며, 이후 2026-07-19 소량 검색 PoC로 초기 상품 JSON 수집을 다시 구현했다. 장기 운영 정책은 별도로 확정해야 한다.

### 2026-07-19 판매처 Registry와 무신사 정책 Adapter(당시 결정)

- 진행상황: ABC마트로 고정된 HTTP 분기를 제거하고 판매처 이름으로 Searcher를 선택하는 Registry를 구현했다. 무신사 요청은 외부 상품 페이지에 접속하지 않고 정책 출처가 포함된 `blocked` 결과를 반환한다.
- 구현 위치:
  - `services/collector/internal/collector/registry.go:8` `SearchRegistry`: 판매처와 Searcher 등록
  - `services/collector/internal/collector/registry.go:34` `Search`: 등록 판매처 전달과 미등록 판매처 `unsupported` 처리
  - `services/collector/internal/merchants/musinsa/search.go:17` `Searcher`: 무신사 정책 Adapter
  - `services/collector/internal/merchants/musinsa/search.go:36` `Search`: 무신사 `blocked` 결과와 robots 출처 반환
  - `services/collector/internal/transport/http/search.go:28` `newSearchHandler`: ABC마트와 무신사 Registry 등록
  - `services/collector/tests/unit/collector/registry_test.go:19` `TestSearchRegistryRoutesMerchant`: 등록 판매처 전달 검증
  - `services/collector/tests/unit/collector/registry_test.go:35` `TestSearchRegistryRejectsUnknownMerchant`: 미등록 판매처 검증
  - `services/collector/tests/unit/musinsa/search_test.go:12` `TestSearchReturnsPolicyBlocked`: 무신사 정책 응답 검증
  - `services/collector/tests/unit/http/server_test.go:87` `TestDefaultServerRoutesMusinsaPolicy`: 운영 Registry HTTP 연결 검증
- 발생 문제: 무신사의 현재 robots 정책은 `ChatGPT-User` 등 지정된 Agent에는 접근을 허용하지만 일반 User-agent에는 전체 경로를 허용하지 않는다. 공개된 기업 CMS API는 상품 검색용이 아니고, 공식 발표된 무신사 MCP의 외부용 endpoint도 확인되지 않았다.
- 원인: 무신사가 데이터 접근 주체와 경로를 제한하고 있으며 우리 Go Collector는 허용 목록에 포함되지 않는다.
- 해결: 허용된 Agent를 사칭하거나 접근 통제를 우회하지 않고, 정책 차단을 정상적인 Collector 상태로 모델링했다. 실제 데이터 구현은 승인된 API·MCP·제휴 Feed 또는 별도 허가가 확보되면 같은 `Searcher` 자리에 연결한다.
- 남은 위험: 현재 무신사 상품 데이터는 반환하지 않는다. robots 정책 변경 여부와 공식 데이터 접근 수단은 다시 확인해야 한다.
- 검증:
  - `services/collector`에서 `go test ./...`: 전체 통과
  - Registry, 무신사 Adapter, 운영 HTTP 연결 단위 테스트 통과

### 2026-07-19 무신사 공개 검색 PoC

- 진행상황: 일반 User-agent로 `구두` 검색을 한 번 확인하고, HTML의 `__NEXT_DATA__`에 포함된 초기 상품을 공통 `Product`로 변환하는 실제 Searcher로 정책 Adapter를 교체했다.
- 수집 필드: 상품번호, 상품명, 브랜드, 현재 가격, 상품 URL, 썸네일, 품절 여부, 평점, 리뷰 수
- 구현 위치:
  - `services/collector/internal/merchants/musinsa/search.go` `Searcher.Search`: 검색 요청, 응답 제한, 상태 변환
  - 같은 파일 `parseSearchItems`: `__NEXT_DATA__` JSON 상품 목록 해석
  - 같은 파일 `toProduct`: 개인정보 없는 공통 상품 구조 변환
  - `services/collector/tests/unit/musinsa/search_test.go`: 가짜 HTML 응답 기반 성공·구조 변경 테스트
  - `services/collector/tests/integration/musinsa_live_test.go`: 명시적으로 활성화하는 실제 검색 smoke test
- 발생 문제: 기본 Go 빌드 캐시가 sandbox 밖에 있어 `operation not permitted`가 발생했고, 최소 테스트 JSON에 닫는 중괄호가 하나 많아 파싱이 실패했다.
- 해결: `GOCACHE=/private/tmp/purchase-research-go-cache`를 사용하고 테스트 JSON 구조를 수정했다.
- 남은 작업: 상품 상세·옵션, 공개 리뷰 최소 필드 변환, 상품 단위 작업 큐와 제한된 고루틴 Worker Pool
- 검증:
  - `GOCACHE=/private/tmp/purchase-research-go-cache go test ./...`: 전체 통과
  - `GOCACHE=/private/tmp/purchase-research-go-cache go vet ./...`: 통과
  - `MUSINSA_LIVE_SMOKE=1 ... TestMusinsaActualSearch`: 실제 `구두` 상품 3개 변환 통과

### 2026-07-20 검색 결과 totalCount·hasNext 구현과 ABC마트 JSON 전환

- 진행상황: 공통 `SearchResult`에 판매처 검색 기준 전체 상품 수와 다음 페이지 여부를 추가했다. 29CM의 기존 pagination을 연결하고 ABC마트는 HTML 파서 대신 `result-total/list` JSON을 사용하도록 교체했다.
- 구현 위치:
  - `services/collector/internal/collector/search.go:117` `SearchResult`: nullable `totalCount`, `hasNext` 공통 필드
  - `services/collector/internal/merchants/abcmart/search.go:101` `Searcher.Search`: ABC마트 JSON 요청·검증·로컬 필터
  - `services/collector/internal/merchants/abcmart/search.go:149`: `SEARCH_COUNT`, `PAGE.finalPageNo` 페이지 정보 매핑
  - `services/collector/internal/merchants/abcmart/search.go:187` `normalizeItem`: 문자열 가격·리뷰 수·사이즈 재고 변환
  - `services/collector/internal/merchants/twentyninecm/search.go:179`: 29CM pagination 매핑
  - `contracts/collector/v1/collector-result.schema.json`: 두 선택 필드 계약 추가
- 발생 문제: 최초 단위 테스트에서 이전 HTML 응답 보조 함수 이름이 남아 컴파일이 실패했고, JSON fixture의 두 상품이 모두 270 사이즈 필터를 통과해 기대 개수와 달랐다.
- 원인: HTML에서 JSON으로 테스트를 전환하며 보조 함수 한 곳을 빠뜨렸고, fixture 재고 조건이 테스트 목적과 겹쳤다.
- 해결: 응답 보조 함수를 JSON 기준으로 통일하고 두 번째 상품의 270 재고를 품절로 조정해 필터 조건을 명확히 했다.
- 남은 위험: `result-total/list`는 외부 개발자용으로 문서화된 공식 API가 아니므로 응답 필드 변경을 fixture와 opt-in smoke test로 감지해야 한다. `totalCount`와 `hasNext`는 로컬 필터 적용 후 개수가 아니라 판매처 원본 검색 기준이다.
- 검증:
  - `GOCACHE=/tmp/opensource-competition-go-cache go test ./...`: 통과
  - `GOCACHE=/tmp/opensource-competition-go-cache go test -race ./...`: 통과
  - `GOCACHE=/tmp/opensource-competition-go-cache go vet ./...`: 통과
  - ABC마트·29CM opt-in live smoke test: 통과
  - ABC마트 실제 `구두` 검색: `totalCount=1650`, `hasNext=true` 확인
  - `uvx check-jsonschema ...`: 성공·부분 성공 예제 검증 통과

## 작업 기록 템플릿

새 작업을 완료할 때 아래 형식을 복사해 기록한다.

```md
### YYYY-MM-DD 작업명

- 진행상황: 완료한 범위와 남은 범위
- 구현 위치:
  - `path/to/file.go:줄` `TypeOrFunctionName`: 구현 책임
  - `path/to/file_test.go:줄` `TestName`: 검증 목적
- 발생 문제: 증상과 영향
- 원인: 확인된 기술적 원인
- 해결: 적용한 변경과 선택 이유
- 남은 위험: 미해결 사항 또는 없음
- 검증:
  - `검증 명령`: 통과/실패와 핵심 결과
```

## 다음 갱신 대상

- Collector 요청 계약과 재검증 책임 확정
- Go transport DTO 및 contract test 위치 기록
- fixture 기반 search/product/reviews/current-offer 구현 근거 추가
