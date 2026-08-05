# Purchase Research Agent 개발 진행 관리

최종 갱신일: 2026-08-03

## 목적

이 문서는 구현 계획의 완료 근거, 코드 위치, 검증 결과, 개발 중 발생한 문제와 해결 과정을 추적한다. 상위 기능과 완료 기준은 [기능 목록](../planning/Purchase_Research_Agent_기능_목록.md), 앞으로 할 일은 [구현 TODO](../planning/Purchase_Research_Agent_TODO.md), 구현 commit 이력은 [코드트래커](../reports/코드트래커/INDEX.md)에서 관리한다.

## 기록 규칙

- 상위 작업은 TODO에서, 세부 작업은 이 문서의 영역별 체크리스트에서 관리한다.
- 새 작업 기록과 구현 근거에는 기능 목록의 기능 ID를 함께 기록한다.
- 기능 목록, 코드트래커와 현재 상태를 연결하는 순서는 [기능 ID 기반 개발 추적 프로세스](기능_ID_기반_개발_추적_프로세스.md)를 따른다.
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
| 구조와 계약 | 부분 구현 | 역할 분리, Collector v1 스키마와 예제 작성 | 요청 계약 확정, Go/Java DTO 매핑 |
| Go Collector 기반 | 부분 구현 | module, 설정, HTTP lifecycle, health·실제 검색 endpoint | 공통 URL 검증, retry, 동시성 제한, 나머지 operation |
| 실제 판매처 Adapter | 부분 구현 | 판매처 Registry와 ABC마트·29CM 공개 검색, 무신사 검색 PoC | 29CM·ABC마트 상품 상세·옵션·리뷰 구현 |
| Spring Boot Product Backend와 DB | 부분 구현 | 환경설정, Java Contract, Flyway schema, 검색 문맥/JPA 적재, RabbitMQ 작업 발행/결과 Consumer, 상품 조회 API | 작업 상태 DB, 동시 저장 보강, 실제 전체 Queue E2E |
| Redis/RabbitMQ 수집 기반 | 부분 구현 | 검색 작업과 결과 계약, Spring producer, Go Worker, Spring 결과 Consumer 및 retry/DLQ | 실제 전체 Queue E2E, Redis limiter, 다중 페이지 |
| 리뷰 분석과 비교 | 미착수 | 구현 코드 없음 | 후보 3개에 점수·근거·주의사항 연결 |
| MCP와 Codex Plugin | 부분 구현 | stdio MCP 도구, Product Backend REST 연결, Codex 조건 구조화와 확인 후 검색 E2E | stream/취소/Plugin 설치와 Claude Code 실행 경계 |
| Next.js Web | 부분 구현 | Landing/Chat/Compare V2, Codex 조건 확인, MCP DB 후보 표시 | 실제 browser/접근성/stream과 `/admin/collections` 구현 |
| 공통 품질과 운영 | 부분 구현 | 루트 Makefile과 PostgreSQL/Redis/RabbitMQ 로컬 실행 기반 | Java 저장 경로, Queue 통합 테스트와 E2E |
| Python/Go 크롤러 비교 | 완료 | 공통 Contract, pagination/checkpoint, 양쪽 판매처 최대 10,000개 실수집과 최신 비교 보고서 | 새 판매처 또는 수집 방식 변경 시 회귀 측정 |

## 영역별 상세 체크리스트

### 0. 구조와 공통 계약

상태: **부분 구현**

- [x] Go Collector, Spring Boot Product Backend, MCP Server, Codex Plugin, Next.js 책임 분리
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
- [ ] 재검증 책임 확정: Go 현재 상태 수집, Product Backend snapshot 비교
- [ ] Go transport DTO와 Java DTO 매핑 확정
- [ ] 유효 예제 자동 schema 검증
- [ ] 무효 예제가 예상대로 실패하는 자동 검증
- [ ] v1 호환성·변경 정책 최종 검토

완료 조건: 요청과 응답 예제만으로 모든 operation, 실패 상태, Go/Java 책임을 설명할 수 있고 자동 계약 검증이 통과해야 한다.

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

### 3. Spring Boot Product Backend와 PostgreSQL

상태: **부분 구현**

- [x] Spring Boot 4.1.0과 Java 21 Gradle 프로젝트 생성
- [x] Web MVC, Validation, JPA, Flyway, PostgreSQL, AMQP, Actuator 의존성 추가
- [x] PostgreSQL과 RabbitMQ Testcontainers 의존성 추가
- [x] product/collection/evidence 도메인 우선 package 구조 적용
- [ ] local/test/production profile과 환경변수 설정 **(부분 구현)**
- [ ] health check와 공통 오류 응답 **(Actuator health 완료)**
- [ ] Collector 요청과 응답 Java DTO **(CollectorResult 응답 완료)**
- [ ] Contract 예제 기반 Java 검증 테스트 **(정상/무효 예제 완료)**
- [x] Product, MerchantProduct, OfferSnapshot, ProductOption, Evidence JPA entity
- [x] Flyway 초기 schema
- [x] 동일 판매처 상품 upsert와 snapshot 추가 transaction
- [x] 저장된 최신 상품 검색 REST API
- [x] 실제 ABC마트/29CM 결과 Swagger 수동 적재와 PostgreSQL 행 검증
- [x] 수집 요청 검색어와 적용 filters 저장 및 조회
- [ ] 조사 세션과 작업 상태

완료 조건: fixture Collector 결과가 Java Contract로 검증되고 PostgreSQL에 중복 없이 재현 가능하게 저장되어야 한다.

### 3-A. Redis/RabbitMQ 수집 실행 기반

상태: **부분 구현**

- [x] Docker Compose Redis 서비스 추가
- [x] Redis 비밀번호, AOF 영구 volume, health check 구성
- [x] Docker Compose RabbitMQ management 서비스 추가
- [x] RabbitMQ 전용 사용자·비밀번호·virtual host 구성
- [x] RabbitMQ AMQP·관리 화면 포트와 영구 volume 구성
- [x] Redis·RabbitMQ 환경 변수 예제 작성
- [x] Redis 인증 ping 실제 검증
- [x] RabbitMQ broker 실행 상태와 관리 화면 HTTP 응답 실제 검증
- [ ] `CollectionJob` 영구 상태 계약
- [x] 검색 `CollectionTask`, `CollectionResult` JSON Schema와 Go 계약
- [x] RabbitMQ exchange, queue, routing key, 5초 retry·DLQ topology
- [x] Go consumer·result publisher와 작업 timeout
- [x] Spring Boot `CollectionTask` producer와 수집 요청 API
- [x] persistent 작업 메시지와 RabbitMQ publisher confirm
- [x] Spring Boot `CollectionResult` Consumer와 수동 ACK
- [x] 계약 위반 결과 reject와 결과 DLQ 이동
- [x] 성공/부분 성공 결과의 JPA transaction 저장 연결
- [ ] 실제 ABC마트 `구두` 3개 Queue 수집과 DB 저장 재검증
- [ ] Redis rate limiter·중복 방지·진행 상태 adapter

완료 조건: Product Backend가 등록한 수집 작업을 Go Worker가 안전하게 소비하고 결과를 다시
Product Backend에 전달하며, Redis가 판매처 전체 속도 제한과 짧은 작업 상태를 일관되게
관리해야 한다.

### 3-B. 루트 개발 명령

상태: **기본 구현 완료**

- [x] `make help` 명령 목록과 수집 예시 제공
- [x] `.env` 준비, 로컬 인프라 실행·중지·상태·로그 명령 제공
- [x] PostgreSQL 접속 명령 제공
- [x] Go Collector 실행·테스트 명령 제공
- [x] Spring Boot 실행과 테스트 명령 제공
- [x] RabbitMQ Go Worker 명령 제공
- [x] Next.js 설치·실행·lint·build 명령 제공
- [x] 전체 기본 검증 `make test`, 전체 검증 `make check` 제공

완료 조건: 개발자가 저장소 루트에서 `make help`를 보고 Go, Spring Boot, Next.js와
로컬 인프라의 기본 실행·검증 명령을 찾을 수 있어야 한다.

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

### 5. MCP, Agent Gateway와 Plugin

상태: **부분 구현**

- [x] Plugin manifest 작성
- [x] Plugin MCP 실행 설정 초안 작성
- [x] 구매 질문·근거·재검증 skill workflow 초안 작성
- [x] Next.js server에서 동작하는 공통 Agent Gateway
- [ ] Codex CLI adapter와 JSON event stream 중계 **(부분 구현: 구조화 JSON 완료, stream 남음)**
- [ ] Claude Code CLI adapter와 stream 중계
- [ ] 대화 session, timeout, 취소와 동시 요청 상한 **(부분 구현: DB session과 process timeout 완료)**
- [ ] 장기 서비스용 OpenAI API Agent 교체 경계
- [ ] Python MCP SDK 의존성 추가
- [x] stdout protocol과 stderr log 분리
- [x] `search_products` 구현과 테스트 **(`search_product_candidates`)**
- [ ] `get_product` 구현과 테스트
- [ ] `compare_products` 구현과 테스트
- [ ] `verify_offer` 구현과 테스트
- [ ] `get_verification_status` 구현과 테스트
- [ ] `get_evidence` 구현과 테스트
- [ ] 공식 사실·리뷰 신호·Agent 추론 응답 구분
- [ ] stale, blocked, partial 상태 사용자 설명 검증
- [ ] 선택 상품 응답 전 재검증 workflow 연결
- [ ] Plugin validation과 로컬 설치 검증
- [ ] Codex E2E: 질문 구체화부터 재검증까지 **(부분 구현: 질문/확인/DB 후보 완료, 구매 전 재검증 남음)**

완료 조건: Codex 또는 Claude Code에서 구매 조건 질문, DB 후보 검색, 근거 비교,
선택 offer 재검증을 같은 MCP 도구로 수행해야 한다.

### 6. Next.js Web

상태: **부분 구현**

- [x] Next.js Web의 예정 책임과 최종 사용자 경로 README 작성
- [x] Next.js, React, TypeScript project scaffold 생성
- [x] Astryx AI Chat Conversation 템플릿과 MIT 라이선스 확인
- [x] Figma Editorial Commerce Landing V2 정적 화면과 반응형 CSS 구현
- [ ] Astryx 의존성·neutral theme 적용
- [ ] `/chat` 사용자 구매 채팅 화면 **(부분 구현: Codex 조건 카드/확인/MCP 후보 표시 완료, stream 남음)**
- [x] `/compare` 실제 DB 상품 후보 비교와 출처 시각 표시
- [ ] `/admin/collections` 관리자 수집 화면
- [ ] 공통 navigation과 관리자 접근 정책
- [x] Product Backend 후보 조회용 공통 API type과 client 구성
- [x] 구매 조건 대화 UI
- [x] 구조화 조건 profile panel
- [x] Spring Boot research session REST endpoint
- [ ] SSE 진행 상태 endpoint와 client
- [ ] 판매처별 진행·부분 실패 표시
- [ ] 상품 비교 UI와 점수 구성 표시
- [ ] evidence panel과 출처·수집 시각 표시
- [ ] 선택 상품 재검증 UI
- [ ] 추천 snapshot과 최신 snapshot diff 표시
- [ ] loading, empty, error, stale 상태 처리 **(loading/empty/error 완료, stale 남음)**
- [x] component와 API integration test **(server route 정상/조건 부족/AI 오류/MCP 오류/미확정/빈 결과)**
- [ ] 핵심 구매 흐름 E2E test **(HTTP E2E 통과, 실제 browser 자동화 연결 없음)**

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

아래 표와 날짜별 기록에는 Spring Boot 전환 전에 완료했던 Python 구현도 포함한다.
`services/research-backend` 경로가 적힌 항목은 현재 실행 가능한 코드가 아니라
이전 구조에서 검증한 과거 근거다.

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
| 수집 결과 계약 초안 | 초안 | `contracts/collector/v1/collector-result.schema.json:1` `Collector Result v1` | `totalCount`, `hasNext`, 검색 query와 filters 추가 / Go 및 Java 테스트 통과 / 실제 Schema 자동 검증 CI 남음 |
| 재검증 결과 계약 초안 | 초안 | `contracts/collector/v1/verification-result.schema.json:1` `Collector Verification Result v1` | 변경 예제 존재, 책임 경계 재검토 필요 |
| RabbitMQ 검색 작업 계약 | 구현 완료 및 검증 필요 | `contracts/collection/v1/collection-task.schema.json:1`, `collection-result.schema.json:1`; Go `internal/messaging/contracts.go:31` `CollectionTask`; Java `collection/dto/CollectionTaskMessage.java:22` `CollectionTaskMessage`, `CollectionResultEnvelope.java:27` `CollectionResultEnvelope` | Go 작업/결과 계약과 Java 작업 발행/결과 소비 테스트 통과 / 서비스 간 실제 판매처 E2E 남음 |
| Go RabbitMQ 검색 Worker | 검색 1페이지 완료 | `services/collector/internal/messaging/processor.go:17` `NewProcessor`, `:29` `Process`; `rabbitmq.go:55` `RabbitWorker.Run`, `:110` `handleDelivery`; `cmd/worker/main.go:25` `run` | Go 전체 테스트와 ABC마트 실제 작업 1건 성공 |
| Spring Boot Product Backend 초기화 | 완료 | `services/product-backend/build.gradle:1` `plugins/dependencies`; `src/main/java/com/purchasesearch/product_backend/ProductBackendApplication.java:11` `ProductBackendApplication` | `./gradlew test`: Testcontainers를 포함해 통과 |
| 별도 MCP Server 경계 | 문서 완료 | `services/mcp-server/README.md:6` `현재 상태`; `plugins/purchase-research-agent/.mcp.json:2` `mcpServers` | 미구현 명령을 등록하지 않은 빈 설정 확인 |
| `BACKEND-001` Collector 결과 수동 적재 API | 부분 구현 | `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionResultController.java:35` `CollectionResultController`; `collection/service/CollectorResultStoreService.java:82` `store`; `collection/entity/CollectionSearchContext.java:25` `CollectionSearchContext`; `product/repository/MerchantProductRepository.java:35` `search`; `src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:153` `storesCollectorResultAndReturnsLatestProductWithoutDuplicatingProduct` | `./gradlew test --rerun-tasks` 통과 / ABC마트와 29CM 실제 수동 저장 검증 / 요청 검색어와 filters 저장 및 조회 완료 / 동시 최초 저장 충돌과 Queue E2E 남음 |
| `QUEUE-002` Spring RabbitMQ 작업 발행과 결과 저장 | 구현 완료 및 검증 필요 | `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionTaskController.java:71` `publish`; `collection/service/CollectionTaskPublisher.java:73` `publish`, `:111` `createTask`; `collection/config/RabbitCollectionConfiguration.java:48` `collectionSearchTaskQueue`; `collection/messaging/CollectionResultConsumer.java:43` `consume`; `src/test/java/com/purchasesearch/product_backend/CollectionTaskPublisherIntegrationTests.java:71` `publishesSearchTaskThroughHttpEndpoint`; `CollectionResultConsumerIntegrationTests.java:93` `consumesSuccessfulResultAndStoresProducts` | RabbitMQ Testcontainers에서 HTTP 202, 작업 계약/persistent/confirm/멱등성/미지원 page 거절과 결과 저장/DLQ 통과 / 실제 판매처 전체 E2E와 작업 상태 DB 남음 |
| `OPS-002` CI/보안/관측 가능성 | 부분 구현 | `services/product-backend/src/main/java/com/purchasesearch/product_backend/common/config/OpenApiConfiguration.java:14` `OpenApiConfiguration`; `collection/controller/CollectionResultController.java:57` `store OpenAPI annotations`; `src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:221` `exposesOpenApiDocumentAndSwaggerUi` | Swagger/OpenAPI 통합 테스트 통과 / 계약 CI, 구조화 로그, metric과 운영 보안 점검 남음 |
| `OPS-003` 기능 ID 기반 개발 추적 | 완료 | `.agents/skills/feature-catalog/SKILL.md:6` `기능 목록 동기화`; `.agents/skills/code-tracker/SKILL.md:6` `코드 변경 기록 작성`; `.agents/skills/feature-progress/SKILL.md:6` `기능 진행상황 점검`; `docs/development/기능_ID_기반_개발_추적_프로세스.md:11` `문서별 책임` | 스킬 3개 YAML/필수 필드, 기능 ID 25개 중복, `make docs-check`, `git diff --check` 통과 / 구현 commit `3b59cd7`과 코드트래커 commit `10bf0ab`을 실제 상태 감사로 연결 |
| Python RabbitMQ 작업/결과 Worker | 이전 구현 | `services/research-backend`의 삭제 전 `RabbitMQBroker`, `enqueue_search`, `consume_results` | 이전 Python 17개 테스트와 실제 상품 3개 저장 기록, 현재 코드 제거 |

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
| 2026-07-31 | 스킬 형식 검증 | 공식 `generate_openai_yaml.py`와 `quick_validate.py`가 `ModuleNotFoundError: yaml`로 실행되지 않음 | 로컬 Python 환경에 PyYAML이 설치되지 않음 | 프로젝트 의존성을 추가하지 않고 Ruby 기본 YAML 파서와 별도 필수 필드 검사로 같은 파일 구조를 검증 / 공식 검사 재실행은 개발환경 PyYAML 준비 후 수행 | 부분 해결 |
| 2026-07-31 | 검증 명령 | 첫 문서 검사에서 `rg`, `make`, `git`을 찾지 못함 | zsh의 특수 변수 `path`를 반복 변수로 사용해 해당 shell의 명령 검색 경로를 덮어씀 | 반복 변수명을 `doc_file`로 변경하고 전체 검증을 다시 실행 | 해결 |
| 2026-07-31 | Product Backend 실행 | Flyway가 비어 있지 않은 `public` schema와 없는 `flyway_schema_history`를 감지해 서버 시작을 중단 | 이전 Python/Alembic 테이블과 데이터가 PostgreSQL Docker Volume에 남아 있음 | 로컬 schema를 정리한 뒤 Flyway V1 적용과 서버 실행 성공 / `flyway_schema_history` 존재, `alembic_version` 없음, Swagger 실제 적재 확인 | 해결 |
| 2026-07-31 | Spring Boot 검증 | 최종 Gradle 재실행이 사용자 Gradle cache의 lock 파일 접근 권한 때문에 실패 | 격리 실행 환경이 workspace 밖의 `~/.gradle` lock 파일 쓰기를 제한 | 승인된 프로젝트 Gradle Wrapper 명령으로 재실행해 전체 테스트 통과 | 해결 |
| 2026-08-02 | 29CM 저장 상품 검색 | DB에는 29CM 상품 3개가 있지만 `merchant=29cm&query=구두` 조회 결과가 0개 | Product Backend가 상품명과 브랜드만 검색했고 CollectorResult와 DB에 요청 검색어가 없었음 | CollectorResult에 query/filters를 추가하고 `collection_search_contexts`와 snapshot을 requestId로 연결 / 수집 검색어 조회 통합 테스트 통과 | 해결 |
| 2026-07-16 | 판매처 정책 | 무신사 검색 구현 후 일반 Collector user-agent가 robots에서 전체 차단됨을 확인 | 무신사 `robots.txt`의 `User-agent: * / Disallow: /` 정책 | 무신사 구현을 제거하고 `User-agent: * / Allow: /`인 ABC마트로 전환 | 해결 |
| 2026-07-16 | 무신사 parser test | 폐기 전 최소 HTML의 `__NEXT_DATA__` JSON 해석 실패 | 테스트 자료를 줄이는 과정에서 닫는 중괄호가 하나 많았음 | JSON을 수정해 원인을 확인했으나 robots 정책 확인 후 무신사 코드는 최종 제거 | 해결 |
| 2026-07-16 | ABC마트 검색 조건 | 결과 개수 1개와 270 사이즈를 함께 요청하면 앞 상품이 맞지 않아 결과가 비어 보임 | 서버에서 1개만 받은 뒤 Collector가 사이즈 조건을 적용함 | 서버에서는 최소 30개를 받은 뒤 조건을 적용하고 마지막에 요청 개수만큼 잘라 반환 | 해결 |
| 2026-07-18 | Collector 구조 | 구현하지 않은 기능의 빈 폴더와 `.gitkeep` 때문에 현재 사용 파일을 구분하기 어려움 | 초기 설계용 placeholder를 실제 구현 후에도 유지함 | `browser`, `observability`, 빈 `fixture`·`musinsa`와 불필요한 `.gitkeep` 제거, 현재 구조 문서화 | 해결 |
| 2026-07-19 | 무신사 데이터 접근 | 무신사 검색 페이지는 사용자 브라우저에서 열리지만 자체 Go Collector로 자동 수집할 수 없음 | 지정된 Agent만 허용하고 일반 User-agent는 전체 경로를 차단하는 robots 정책 | User-agent 위장은 제외하고 정책 Adapter로 `blocked` 반환. 공식 상품 API·MCP·제휴 Feed 또는 별도 허가 확보를 후속 작업으로 등록 | 부분 해결 |
| 2026-07-19 | 무신사 소량 검색 PoC | 리뷰뿐 아니라 검색어 기반 상품 후보가 필요함 | 검색 페이지가 SPA이지만 초기 상품은 HTML의 `__NEXT_DATA__`에 서버 렌더링됨 | 일반 User-agent와 최소 1초 간격을 사용하는 Searcher 구현, 실제 `구두` 상품 3개 smoke test 통과 | PoC 해결 |
| 2026-07-20 | ABC마트 검색 원본 선택 | 상품 전체 수와 다음 페이지 여부를 HTML 파서로 정확히 알 수 없음 | 기존 구현이 화면용 HTML 조각만 해석함 | 공개 검색 화면의 `result-total/list` JSON으로 전환하고 `SEARCH_COUNT`, `PAGE.finalPageNo`를 공통 결과에 매핑 | 해결 |
| 2026-07-26 | Queue 통합 검증 | 먼저 실행한 Python 결과 Worker가 결과 도착 직전에 30초 timeout으로 종료 | 권한 승인과 최초 Go Worker 빌드가 결과 Worker 대기 시간보다 오래 걸림 | RabbitMQ에 남아 있던 persistent 결과를 Worker 재실행으로 정상 저장; 상시 Worker에서는 계속 대기하도록 구성 | 해결 |
| 2026-07-26 | Queue JSON 계약 | 실제 Python 작업 JSON에 선택 가격 필드가 `null`로 포함됐지만 Schema는 정수 또는 필드 생략만 허용 | Pydantic JSON 직렬화가 기본적으로 `None`을 포함 | RabbitMQ 발행과 멱등성 canonical JSON에 `exclude_none=True` 적용, 성공·실패 Schema 예제 재검증 | 해결 |
| 2026-07-26 | 로컬 명령 보안 | Make가 AMQP URL을 포함한 Worker 실행 명령을 터미널에 그대로 출력 | Make recipe의 기본 echo 동작 | Queue recipe에 `@`를 적용해 비밀번호 포함 URL을 숨기고 루트 `.env` 값을 읽도록 구성 | 해결 |

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

### 2026-07-25 Collector 실제 검색 결과 PostgreSQL 적재

- 진행상황: Python Backend가 Go Collector 검색 API를 호출하고 Pydantic으로 응답을 검증한 뒤, 상품·가격 snapshot·옵션·근거를 하나의 DB transaction으로 저장하도록 구현했다.
- 구현 위치:
  - `services/research-backend/src/research_backend/clients/collector/models.py:47` `SearchRequest`: Collector 검색 요청 계약과 timezone 검증
  - `services/research-backend/src/research_backend/clients/collector/models.py:153` `CollectorResult`: Collector v1 응답 계약 검증
  - `services/research-backend/src/research_backend/clients/collector/http.py:29` `CollectorHttpClient.search`: 내부 검색 API POST와 오류 변환
  - `services/research-backend/src/research_backend/repositories/search_result.py:24` `SqlAlchemySearchResultRepository.save`: 상품·snapshot·옵션·근거 저장
  - `services/research-backend/src/research_backend/repositories/search_result.py:91` `_upsert_merchant_product`: `merchant + externalId` 중복 처리
  - `services/research-backend/src/research_backend/application/use_cases/collect_search.py:31` `CollectSearchProducts.execute`: Collector 상태 확인과 transaction 경계
  - `services/research-backend/src/research_backend/interfaces/cli/collect_search.py:50` `run`: 실제 검색·DB 저장 CLI
  - `services/research-backend/tests/integration/test_postgres_search_result.py:23` `test_search_result_is_saved_to_postgres`: PostgreSQL 저장과 정리 검증
- 발생 문제: 최초 실제 Collector 실행은 sandbox의 8090 포트 바인딩 제한으로 실패했고, 최초 PostgreSQL 통합 테스트는 조회가 자동 시작한 transaction 안에서 다시 `session.begin()`을 호출해 실패했다.
- 원인: 로컬 서버 실행 권한이 없었고, SQLAlchemy Session의 autobegin 동작을 테스트 정리 코드가 고려하지 않았다.
- 해결: 허용된 환경에서 Collector를 실행하고 통합 테스트의 저장·조회·정리를 별도 Session으로 분리했다. 테스트 전후 `integration-test` 판매처 행을 자동 정리한다.
- 저장 정책: 같은 `merchant + externalId`는 기존 상품 연결을 갱신하고, 가격·재고와 근거는 재수집마다 새로운 snapshot·evidence로 추가한다. 가격이 없는 상품은 상품과 근거만 저장한다.
- 남은 위험: 검색 결과만 저장하며 리뷰, 실측값, 배송 snapshot, 조사 세션과 verification snapshot은 아직 저장하지 않는다.
- 검증:
  - `uv run pytest`: 12 passed, 실제 PostgreSQL opt-in 1 skipped
  - `RUN_POSTGRES_INTEGRATION=1 ... pytest tests/integration/test_postgres_search_result.py`: 1 passed
  - ABC마트 `구두` 2개 실제 적재: snapshot 2개, option 12개, evidence 2개
  - 29CM `구두` 2개 실제 적재: snapshot 2개, evidence 2개
  - ABC마트 동일 검색 재적재: 상품·판매처 상품은 총 4개 유지, snapshot·evidence는 총 6개로 증가

### 2026-07-26 Redis·RabbitMQ 로컬 수집 실행 기반

- 진행상황: PostgreSQL과 함께 Redis와 RabbitMQ를 루트 Docker Compose에서
  실행할 수 있도록 구성했다. Redis는 속도 제한·중복 방지·진행 상태용,
  RabbitMQ는 수집 작업·결과 전달용으로 역할을 분리했다.
- 구현 위치:
  - `compose.yaml:23` `redis`: 비밀번호 인증, AOF volume, health check와 호스트 포트
  - `compose.yaml:44` `rabbitmq`: 전용 계정·virtual host, AMQP·관리 포트, volume과 health check
  - `compose.yaml:76` `volumes`: PostgreSQL·Redis·RabbitMQ 영구 volume
  - `.env.example:20` `REDIS_*`: Redis 로컬 포트와 비밀번호 예제
  - `.env.example:26` `RABBITMQ_*`: AMQP·관리 포트와 계정·virtual host 예제
- 발생 문제: 격리된 실행 환경에서 Docker daemon socket 접근이 거부됐다.
- 원인: Docker Desktop socket이 workspace sandbox 밖에 있어 기본 권한으로
  컨테이너 이미지를 확인하거나 실행할 수 없었다.
- 해결: 사용자 승인을 받은 Docker Compose 명령으로 공식 Redis·RabbitMQ 이미지를
  내려받고 컨테이너를 실행했다.
- 남은 위험: 로컬 기본 비밀번호는 개발 편의를 위한 값이므로 운영에서는 반드시
  변경해야 한다. Redis key 정책과 application adapter는 다음 구현 범위다.

### 2026-07-26 RabbitMQ 검색 작업 수직 흐름

- 진행상황: Python이 ABC마트 검색 작업을 RabbitMQ에 등록하고, Go Worker가 기존
  Searcher로 공개 검색을 실행한 뒤, Python 결과 Worker가 계약을 검증해
  PostgreSQL에 저장하는 첫 Queue 수직 흐름을 구현했다.
- 구현 위치:
  - `contracts/collection/v1/collection-task.schema.json:1`: 검색 작업 JSON Schema
  - `contracts/collection/v1/collection-result.schema.json:1`: 성공·부분·실패 결과 봉투
  - `services/collector/internal/messaging/contracts.go:31` `CollectionTask`: Go 작업 DTO와 검증
  - `services/collector/internal/messaging/processor.go:29` `Processor.Process`: timeout이 있는 기존 Searcher 연결
  - `services/collector/internal/messaging/rabbitmq.go:55` `RabbitWorker.Run`: prefetch 1 작업 소비
  - 같은 파일 `:110` `handleDelivery`: publisher confirm 후 ACK, retry·DLQ 분기
  - `services/collector/cmd/worker/main.go:25` `run`: Worker process 진입점
  - `services/research-backend/src/research_backend/infrastructure/messaging/contracts.py:50` `CollectionTask`: Python 작업 계약
  - `services/research-backend/src/research_backend/infrastructure/messaging/rabbitmq.py:39` `RabbitMQBroker`: topology, 발행, 결과 수신과 ACK
  - `services/research-backend/src/research_backend/interfaces/cli/enqueue_search.py:42` `run`: 작업 등록 CLI
  - `services/research-backend/src/research_backend/interfaces/cli/consume_results.py:42` `run`: 결과 검증·DB 저장 Worker
  - `services/research-backend/src/research_backend/application/use_cases/store_search_result.py:14` `StoreCollectedSearchResult`: HTTP·Queue 공용 저장 transaction
- 실제 결과: ABC마트 `구두`, 1페이지, 최대 3개 작업을 처리해 상품 3개,
  가격 snapshot 3개, 옵션 17개, evidence 3개를 저장했다. 판매처 전체 결과는
  `totalCount=1650`, `hasNext=true`였다.
- 발생 문제: 최초 결과 Worker의 30초 대기 중 권한 승인과 Go 최초 빌드가 길어져
  Worker가 먼저 종료됐다. 결과는 durable Queue에 남아 있었고 Worker를 다시
  실행해 손실 없이 저장했다. 또한 선택 필드 `null`을 Schema에 맞게 생략하도록
  직렬화를 수정했다.
- 남은 위험: 검색은 1페이지만 지원한다. Redis 멱등성 차단, 작업 상태 DB,
  판매처 전체 rate limiter, retry·DLQ 장애 통합 테스트와 여러 Worker 검증은
  아직 구현 전이다.
- 검증:
  - `services/collector`에서 `GOCACHE=/private/tmp/purchase-research-go-cache go test ./...`: 통과
  - `services/research-backend`에서 `uv run pytest`: 17 passed, PostgreSQL opt-in 1 skipped
  - `uvx check-jsonschema`: CollectionTask와 성공·실패 CollectionResult 예제 통과
  - `make enqueue`, `make collector-worker-once`, `make result-worker-once`: 실제 수직 흐름 통과
- 검증:
  - `docker compose config --quiet`: 통과
  - `docker compose up -d redis rabbitmq`: 컨테이너·volume 생성 성공
  - `docker compose ps`: PostgreSQL·Redis·RabbitMQ 모두 `healthy`
  - Redis 인증 `PING`: `PONG`
  - `rabbitmq-diagnostics -q check_running`: broker 정상 실행
  - `http://localhost:35673/`: RabbitMQ 관리 화면 HTTP 200

### 2026-07-30 Spring Boot Product Backend와 별도 MCP Server 구조 전환

- 진행상황: 사용자 화면 폴더를 `apps`에서 `frontend`로 옮겼다. 기존 Python
  Research Backend를 제거하고 Spring Boot Product Backend와 별도 MCP Server
  경계로 문서, Contract 설명, 루트 실행 명령을 갱신했다. Product Backend는
  기본 프로젝트와 의존성만 준비됐으며 Flyway schema, JPA 저장, RabbitMQ
  producer/consumer는 미구현이다.
- 구현 위치:
  - `Makefile:6` `PRODUCT_BACKEND_DIR`, `MCP_SERVER_DIR`, `WEB_DIR`: 새 디렉토리와 실행 명령 연결
  - `docs/architecture/Purchase_Research_Agent_시스템_구조.md:24` `전체 구조`: Next.js → MCP Server → Product Backend → Collector 경계
  - `docs/architecture/Purchase_Research_Agent_시스템_구조.md:69` `사용자 질문과 백그라운드 수집`: DB 우선 조회와 제한된 추가 수집 흐름
  - `services/product-backend/build.gradle:1` `plugins/dependencies`: Spring Boot 4.1.0, JPA, Flyway, AMQP, PostgreSQL, Testcontainers 구성
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/ProductBackendApplication.java:11` `ProductBackendApplication`: Product Backend 시작점
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductBackendApplicationTests.java:13` `ProductBackendApplicationTests`: PostgreSQL/RabbitMQ Testcontainers 문맥 검증
  - `services/mcp-server/README.md:6` `현재 상태`: MCP Server의 책임과 금지 경계
  - `plugins/purchase-research-agent/.mcp.json:2` `mcpServers`: MCP 구현 전 빈 실행 설정
- 발생 문제: 기본 sandbox에서 Go는 사용자 Library build cache에 접근하지 못했고,
  Gradle은 사용자 `.gradle` cache lock과 Docker Testcontainers에 접근하지 못했다.
  Next.js production build는 Google Fonts 네트워크 접근이 차단돼 실패했다. 또한
  Markdown 날짜 줄의 강제 줄바꿈 공백이 `git diff --check`에 걸렸다.
- 원인: Go와 Gradle의 기본 cache 위치가 workspace 밖이었고 Spring 문맥 테스트가
  Docker daemon을 사용한다. Markdown 줄 끝 공백은 Git whitespace 검사 대상이다.
- 해결: Go는 `GOCACHE=/private/tmp/purchase-research-go-cache`로 검증했다. Spring
  테스트는 승인된 Gradle/Docker 접근으로 실행했다. Next.js build는 승인된
  네트워크 접근으로 외부 폰트를 받은 뒤 재실행했다. 문서의 줄 끝 공백을 제거했다.
- 남은 위험: Spring Boot용 데이터베이스 환경설정, Flyway migration, JPA entity,
  RabbitMQ producer/consumer가 없어 현재 DB 자동 적재는 동작하지 않는다. MCP
  구현 언어와 실행 명령도 아직 결정하지 않았다.
- 검증:
  - `GOCACHE=/private/tmp/purchase-research-go-cache go test ./...`: 통과
  - `GOCACHE=/private/tmp/purchase-research-go-cache go vet ./...`: 통과
  - `services/product-backend`에서 `./gradlew test`: `BUILD SUCCESSFUL`
  - `frontend/purchase-web`에서 `npm run lint`: 통과
  - `frontend/purchase-web`에서 `npm run build`: production build 통과
  - `docker compose config --quiet`: 통과
  - `git diff --check`: 통과

### 2026-07-30 대회 규정 근거 공개와 문서 동기화 검사

- 진행상황: 운영규정이 특정 문서 파일명을 요구하지 않는다는 점과 프로젝트가
  공개 근거를 남기기 위해 선택한 관리 방식이라는 점을 구분했다. 현재 직접
  사용하는 외부 구성요소와 AI 개발 보조 범위를 각각 공개하고, 관련 manifest와
  AI integration 변경에 문서 갱신이 빠지면 실패하는 로컬/CI 검사를 추가했다.
- 구현 위치:
  - `THIRD_PARTY_NOTICES.md:5` `이 문서를 공개하는 이유`: 운영규정 5쪽 제8조
    제5항과 6쪽 제8조 제6항의 근거 및 파일 형식은 프로젝트 선택이라는 설명
  - `THIRD_PARTY_NOTICES.md:16` `현재 직접 사용하는 구성요소`: Go, Spring Boot,
    Next.js 및 container image의 현재 version/출처/license
  - `AI_USAGE.md:5` `이 문서를 공개하는 이유`: 운영규정 7쪽 제9조 제4항과
    제5항의 직접 의무 및 자발적 공개 문서 구분
  - `AI_USAGE.md:22` `사람의 검토 원칙`: AI 작성 결과의 diff/test/설명 책임
  - `AGENTS.md:86` `규정과 공개 문서 동기화`: 의존성/model/AI 변경 시 같은
    작업과 commit에서 공개 문서를 갱신하는 규칙
  - `scripts/check-document-sync.sh:4` `문서 동기화 검사`: 변경 파일을 분석해
    `THIRD_PARTY_NOTICES.md`와 `AI_USAGE.md` 갱신 누락 차단
  - `.github/workflows/document-sync.yml:1` `Document Sync`: PR과
    `develop`/`main` push에서 동기화 검사 실행
  - `Makefile:94` `docs-check`: 루트에서 실행하는 문서 동기화 검사 명령
- 발생 문제: `AGENTS.md` 규칙만으로는 사람이 직접 manifest를 바꾼 경우의 문서
  누락을 자동으로 막을 수 없고, 자동 검사가 license 내용의 정확성이나 일반
  source code의 AI 사용 여부까지 판별할 수 없다.
- 원인: 지침 파일은 작업자와 에이전트의 행동 규칙이며 Git 변경 자체를 강제하지
  않는다. 또한 license 해석과 AI 사용 여부에는 사람의 판단 및 외부 근거가 필요하다.
- 해결: 규칙과 함께 변경 파일 기반 검사 스크립트 및 GitHub Actions를 추가했다.
  자동 검사는 갱신 누락을 차단하고, 공식 출처 확인과 AI 사용 기록은 사람의
  책임으로 명시했다.
- 남은 위험: Spring Boot 간접 의존성과 container image 내부 package 전체
  license audit는 제출 시점에 다시 수행해야 한다. 저장소 자체 `LICENSE`도 팀
  협의 후 추가해야 한다.
- 검증:
  - `make docs-check`: 현재 변경 범위 통과
  - `bash -n scripts/check-document-sync.sh`: shell 문법 검사 통과
  - `DOC_SYNC_CHANGED_FILES='services/collector/go.mod' ./scripts/check-document-sync.sh`:
    공개 문서 누락을 의도대로 실패 처리
  - `DOC_SYNC_CHANGED_FILES=$'services/collector/go.mod\nTHIRD_PARTY_NOTICES.md' ./scripts/check-document-sync.sh`:
    공개 문서 동반 변경을 통과 처리
  - `git diff --check`: 통과

### 2026-07-30 Product Backend 상품 수집 DB 저장과 조회

- 진행상황: Spring Boot가 시작될 때 Flyway로 상품 수집 테이블을 만들고, ABC마트
  CollectorResult 계약 예제를 검증해 PostgreSQL에 저장하는 transaction을 구현했다.
  같은 판매처 상품은 중복 생성하지 않고 매 수집 시점의 가격/재고/옵션/근거를 새
  snapshot으로 남긴다. 저장된 최신 상품을 판매처와 검색어로 조회하는 REST API도
  구현했다. RabbitMQ 결과 consumer와 실제 Collector부터 DB까지 연결하는 E2E는
  남아 있다.
- 구조 결정: Product Backend는 `product`, `collection`, `evidence` 업무 도메인을
  먼저 나누고 각 도메인 안에서 `controller`, `dto`, `entity`, `repository`,
  `service`, `exception` 역할을 나누는 package-by-feature 구조를 사용한다.
- 구현 위치:
  - `services/product-backend/src/main/resources/application.yaml:1` `spring`: 로컬
    환경변수 기반 PostgreSQL/RabbitMQ, Flyway, JPA validate 및 Actuator 설정
  - `services/product-backend/src/main/resources/application-prod.yaml:1` `spring`:
    운영 환경에서 필수 연결값을 외부 환경변수로 주입
  - `services/product-backend/src/main/resources/db/migration/V1__initial_product_collection.sql:1`
    `V1__initial_product_collection`: 상품, 판매처 상품, offer snapshot, 옵션 및 근거
    schema와 제약조건
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectorResult.java:34`
    `CollectorResult`: Go Collector v1 결과의 Java DTO와 Bean Validation
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:31`
    `CollectorResultStoreService`: 상품 upsert와 snapshot/옵션/근거 저장 transaction
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/controller/ProductQueryController.java:24`
    `ProductQueryController.search`: 최신 상품 검색 REST API
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductQueryService.java:28`
    `ProductQueryService.search`: 최신 snapshot과 옵션을 결합하고 `totalCount`와
    `hasNext`를 반환
  - `contracts/collector/v1/examples/collector-result.abcmart-success.json:1`
    `collector-result.abcmart-success`: Java 저장 통합 테스트용 공통 계약 예제
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:41`
    `ProductStorageIntegrationTests`: 실제 PostgreSQL에서 migration 재실행, 유효/무효
    계약, 중복 방지, snapshot 추가 및 조회 API 검증
- 발생 문제:
  - 최초 코드가 `application/domain/infrastructure/interfaces` 최상위 계층으로
    나뉘어 팀이 선호하는 도메인 중심 탐색 방식과 맞지 않았다.
  - Flyway의 `CHAR(3)` 통화 열과 JPA의 `VARCHAR(3)` 매핑 차이로 Hibernate schema
    검증이 실패했다.
  - 현재 Flyway 버전의 migration 결과 개수는 method가 아니라 field로 제공되어
    테스트 compile이 한 차례 실패했다.
- 원인:
  - Spring 계층형 package 구조를 기본값으로 적용해 팀의 기존 개발 방식이 반영되지
    않았다.
  - PostgreSQL의 `CHAR`와 Hibernate의 `VARCHAR` type 판단 차이를 초기 migration에서
    고려하지 않았다.
  - 현재 의존성의 Flyway API를 확인하기 전에 이전 API 형태를 사용했다.
- 해결:
  - 코드를 `collection`, `product`, `evidence` 도메인으로 이동하고 같은 구조를
    `AGENTS.md`에 규칙으로 고정했다.
  - 통화 열을 `VARCHAR(3)`으로 맞춰 Flyway schema와 JPA 검증을 일치시켰다.
  - 현재 Flyway API에 맞춰 `migrationsExecuted` field를 검증했다.
- 남은 위험: RabbitMQ 결과 consumer 미구현, 동시에 최초 저장할 때 unique 충돌을
  재시도하는 정책 미구현, JSON Schema 직접 검증 미구현, 리뷰/실측값 저장 미구현,
  실제 ABC마트/29CM 결과를 Queue부터 PostgreSQL까지 저장하는 E2E 미검증
- 검증:
  - `services/product-backend`에서 `./gradlew test`: Testcontainers PostgreSQL과
    RabbitMQ를 사용한 전체 테스트 통과

### 2026-07-31 BACKEND-001 Collector 결과 수동 적재 API

- 진행상황: Go Collector가 만든 성공 또는 부분 성공 JSON을
  `POST /internal/v1/collection-results`로 받아 기존 transaction 저장 서비스에
  연결했다. 저장 개수를 HTTP 응답으로 반환하고 차단 상태 또는 Contract 위반은
  400으로 거절한다. Swagger UI에서 ABC마트 `manual-abc-001` 결과를 전송해 로컬
  PostgreSQL 적재까지 확인했다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionResultController.java:35`
    `CollectionResultController`: 수동 적재 내부 API와 저장 불가 상태의 400 응답
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectionResultStoreResponse.java:14`
    `CollectionResultStoreResponse`: 상품, snapshot, 옵션과 근거 저장 개수 응답
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/exception/UnstorableCollectorResultException.java:7`
    `UnstorableCollectorResultException`: 저장 불가 상태만 400으로 변환하고 내부
    프로그래밍 오류와 구분
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:79`
    `storesCollectorResultThroughHttpEndpoint`: HTTP 요청부터 실제 PostgreSQL 저장까지
    정상 경로 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:105`
    `rejectsNonStorableCollectorResultThroughHttpEndpoint`: 저장 불가 상태의 400 응답과
    미저장 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:127`
    `rejectsContractViolationThroughHttpEndpoint`: Bean Validation 실패의 400 응답과
    미저장 검증
- 발생 문제: 최초 실행 시 로컬 PostgreSQL에 이전 Alembic 테이블과 데이터가 남아
  Flyway 기반 Product Backend가 시작되지 않았다.
- 원인: `make infra-down`은 PostgreSQL Volume을 보존하며 기존 DB에는
  `alembic_version`만 있고 `flyway_schema_history`가 없다.
- 해결: 로컬 schema를 정리하고 Flyway V1 마이그레이션을 적용했다. 현재
  `flyway_schema_history`가 존재하고 `alembic_version`은 없으며, Swagger UI로
  전송한 실제 ABC마트 결과가 정상 저장됐다. API 정상/실패 경로는 독립
  Testcontainers PostgreSQL에서도 검증했다.
- 남은 위험: 이 내부 API에는 아직 인증과 요청 본문 크기 제한이 없으므로 운영
  환경에 외부 노출하면 안 된다. 최종 자동 적재는 RabbitMQ consumer가 같은 저장
  서비스를 호출해야 한다. 29CM 실제 결과 적재도 별도로 검증해야 한다.
- 검증:
  - `services/product-backend`에서 `./gradlew test`: 통과
  - `docker compose exec -T postgres psql`: Flyway V1 성공과 Alembic history 제거 확인
  - ABC마트 `manual-abc-001` SQL 확인: 상품 3개, 판매처 상품 3개, 가격/재고
    snapshot 3개, 옵션 19개, 근거 3개

### 2026-07-31 OPS-002 Product Backend Swagger UI

- 진행상황: Spring Boot 4용 springdoc-openapi를 추가해 수동 적재 API와 상품 조회 API를
  Swagger UI에서 직접 호출할 수 있게 했다. OpenAPI JSON 경로와 Swagger UI redirect도
  통합 테스트로 고정했다.
- 구현 위치:
  - `services/product-backend/build.gradle:28`
    `springdoc-openapi-starter-webmvc-ui`: Spring Boot 4용 OpenAPI와 Swagger UI 의존성
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/common/config/OpenApiConfiguration.java:14`
    `OpenApiConfiguration`: 내부 API 제목, 버전과 설명
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionResultController.java:57`
    `store OpenAPI annotations`: 수동 적재 설명과 200/400 응답
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/controller/ProductQueryController.java:49`
    `search OpenAPI annotations`: 최신 상품 검색 설명
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:221`
    `exposesOpenApiDocumentAndSwaggerUi`: OpenAPI 경로와 Swagger UI 진입점 검증
- 발생 문제: 없음
- 해결: 로컬 profile에서는 `/swagger-ui.html`과 `/v3/api-docs`를 제공하고 운영
  profile에서는 두 기능을 기본 비활성화했다.
- 남은 위험: Swagger UI와 수동 적재 API는 인증이 구현되기 전까지 운영 환경에
  노출하면 안 된다.
- 검증:
  - `services/product-backend`에서 `./gradlew test`: 통과
  - `make docs-check`: 통과
  - `git diff --check`: 통과

### 2026-08-02 BACKEND-001 / OPS-002 / OPS-003 기능 진행상황 감사

- 진행상황:
  - `BACKEND-001`: Flyway schema, Java DTO, 상품 upsert, snapshot/옵션/근거 저장,
    최신 조회, 정상/실패 통합 테스트와 ABC마트 실제 적재를 확인해 `부분 구현`으로
    판정했다.
  - `OPS-002`: Swagger/OpenAPI 구현과 테스트는 완료됐지만 기능 범위에 포함된 계약
    CI, 구조화 로그, metric과 운영 보안 점검이 남아 `부분 구현`을 유지했다.
  - `OPS-003`: 세 스킬 형식, 기능 ID 중복, 구현 commit, 코드트래커와 현재 상태
    감사를 하나의 실제 흐름으로 연결해 `완료`로 판정했다.
- 구현 근거:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:75`
    `CollectorResultStoreService.store`: 검증된 결과의 transaction 저장
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:79`
    `storesCollectorResultThroughHttpEndpoint`: 실제 PostgreSQL 저장 정상 경로
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:105`
    `rejectsNonStorableCollectorResultThroughHttpEndpoint`: 저장 불가 상태 실패 경로
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:127`
    `rejectsContractViolationThroughHttpEndpoint`: 필수값 위반 실패 경로
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:221`
    `exposesOpenApiDocumentAndSwaggerUi`: Swagger/OpenAPI 통합 경로
  - `.agents/skills/feature-catalog/SKILL.md:6` `기능 목록 동기화`
  - `.agents/skills/code-tracker/SKILL.md:6` `코드 변경 기록 작성`
  - `.agents/skills/feature-progress/SKILL.md:6` `기능 진행상황 점검`
- 발생 문제: 개발 진행 관리에서 Swagger 하위 구현을 `완료`로 표시해 상위
  `OPS-002` 전체 상태와 혼동됐고, `BACKEND-001`은 상태 기준에 없는 `부분 완료`
  표현을 사용했다.
- 원인: 구현 단위의 완료 여부와 상위 기능 ID 전체 완료 여부를 같은 상태 열에서
  구분하지 않았다.
- 해결: 상위 기능 범위를 기준으로 두 항목을 `부분 구현`으로 통일하고, 완료된
  Swagger 범위와 남은 운영 범위를 검증 근거에 분리했다. `OPS-003`은 이번 실제
  코드트래커와 상태 감사로 완료 기준 충족을 확인했다.
- 남은 위험:
  - `BACKEND-001`: 29CM 실제 적재, 동시 최초 저장 충돌과 Queue E2E
  - `OPS-002`: 계약 CI, 구조화 로그, metric, 인증과 운영 보안 점검
  - `OPS-003`: 새 기능에서 같은 절차가 누락되지 않도록 지속 적용
- 검증:
  - `services/product-backend`에서 `./gradlew test --rerun-tasks`: 전체 작업 재실행 후
    통과
  - Ruby YAML 검사: 세 스킬의 frontmatter와 `openai.yaml` 필수 필드 통과
  - 기능 목록 검사: 기능 ID 25개 중복 없음
  - `make docs-check`: 통과
  - `git diff --check`: 통과

### 2026-08-02 BACKEND-001 29CM 실제 결과 PostgreSQL 적재 검증

- 진행상황: 사용자가 실행한 Collector와 Product Backend를 사용해 29CM `구두`
  검색 결과 3개를 수집하고 `POST /internal/v1/collection-results`로 저장했다.
- 검증 결과:
  - Collector 요청 ID: `manual-29cm-20260802-001`
  - Collector 응답: `status=success`, `totalCount=5466`, `hasNext=true`, 상품 3개
  - Product Backend 응답: 상품 3개, snapshot 3개, 옵션 0개, 근거 3개
  - PostgreSQL SQL 조회: 요청 ID 기준 snapshot 3개, 판매처 상품 3개, 옵션 0개,
    근거 3개
  - `GET /internal/v1/products?merchant=29cm&limit=10`: 저장 상품 3개 조회
- 발생 문제: `merchant=29cm&query=구두` 조회는 0개를 반환했다.
- 원인: 현재 조회 API는 상품명과 브랜드만 검색하며 수집 요청의 `query`와
  `filters`를 DB에 저장하지 않는다. 이번 29CM 상품명과 브랜드에는 `구두` 문자열이
  포함되지 않았다.
- 해결: 1차 검증에서는 판매처만 지정해 적재 성공을 확인했다. 이후 CollectorResult에
  `query`와 `filters`를 추가하고 `collection_search_contexts`에 요청당 한 번 저장했다.
  `offer_snapshots.request_id`와 연결한 조회 SQL 및 통합 테스트로 상품명에 `구두`가
  없는 상품도 수집 검색어로 조회되는 것을 확인했다.
- 남은 위험: 29CM 검색 결과에는 옵션이 없어 옵션 0개가 정상 저장됐다. 옵션은 향후
  상품 상세 Adapter에서 수집해야 한다.

### 2026-08-02 BACKEND-001 수집 검색어와 filters 저장 및 조회

- 진행상황: Collector가 검색 요청의 `query`와 적용 `filters`를 결과에 포함하고,
  Product Backend가 요청별 검색 문맥을 PostgreSQL에 저장하도록 구현했다.
- 구현 위치:
  - `services/collector/internal/collector/search.go:119` `SearchResult`: query와 filters 공통 응답 필드
  - `services/collector/internal/collector/registry.go:34` `Search`: 요청 검색 문맥을 결과에 보존
  - `contracts/collector/v1/collector-result.schema.json:1` `Collector Result v1`: search operation 조건부 계약
  - `services/product-backend/src/main/resources/db/migration/V2__add_collection_search_context.sql:1` `collection_search_contexts`: 요청별 검색 문맥 schema
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/entity/CollectionSearchContext.java:25` `CollectionSearchContext`: JSONB filters entity
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:113` `storeSearchContext`: 중복 없는 문맥 저장과 requestId 오용 차단
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/repository/MerchantProductRepository.java:35` `search`: 상품명/브랜드/수집 검색어 조회
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:153` `storesCollectorResultAndReturnsLatestProductWithoutDuplicatingProduct`: 검색 문맥 저장과 조회 검증
- 발생 문제: 첫 Go 테스트가 macOS 사용자 Go build cache 접근 제한으로 실패했다.
- 원인: 격리 환경에서 workspace 밖의 `/Users/iseoin/Library/Caches/go-build` 쓰기가
  허용되지 않았다.
- 해결: `GOCACHE=/private/tmp/purchase-research-go-build`로 테스트 cache만 변경했다.
  제품 코드나 실행 설정은 변경하지 않았다.
- 남은 위험: 이미 저장된 과거 snapshot에는 검색 문맥이 자동 생성되지 않는다. 새
  CollectorResult부터 연결되며, Queue consumer와 동시 최초 상품 upsert는 별도 작업이다.
- 검증:
  - `GOCACHE=/private/tmp/purchase-research-go-build go test ./...`: 전체 통과
  - `./gradlew test --rerun-tasks`: V2 Flyway 및 PostgreSQL 통합 테스트 포함 전체 통과
  - 실제 Collector 요청 `manual-29cm-lineage-20260802-001`: `query=구두`,
    `filters.inStockOnly=true`, 상품 3개 반환
  - 실제 Product Backend 저장: 상품 3개, snapshot 3개, 옵션 0개, 근거 3개
  - `GET /internal/v1/products?merchant=29cm&query=구두&limit=10`: 상품명에
    `구두`가 없는 29CM 상품 3개 조회
  - PostgreSQL `collection_search_contexts`: 요청 ID, 판매처 `29cm`, 검색어 `구두`,
    `{"inStockOnly": true}` 저장 확인

### 2026-08-02 QUEUE-002 Spring Boot RabbitMQ 결과 Consumer

- 진행상황: Go Worker가 발행한 `CollectionResult`를 Spring Boot가 RabbitMQ 결과
  Queue에서 받아 계약을 검증하고 기존 `CollectorResultStoreService`로 PostgreSQL에
  자동 저장하는 경로를 구현했다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/config/RabbitCollectionConfiguration.java:20` `RabbitCollectionConfiguration`: 결과 Queue, DLQ와 binding 선언
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectionResultEnvelope.java:27` `CollectionResultEnvelope`: Java Queue 결과 계약과 상태 의미 검증
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectionResultMessageService.java:49` `process`: JSON/Bean Validation 및 저장 서비스 연결
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/messaging/CollectionResultConsumer.java:43` `consume`: manual ACK와 reject 처리
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionResultConsumerIntegrationTests.java:93` `consumesSuccessfulResultAndStoresProducts`: RabbitMQ 결과의 PostgreSQL 저장 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionResultConsumerIntegrationTests.java:113` `rejectsInvalidResultToDeadLetterQueue`: 계약 위반 결과 DLQ 검증
- 발생 문제: 없음.
- 해결: 성공/부분 성공은 저장 후 ACK하고, 정상 `failed` 결과는 상품을 저장하지 않고
  ACK한다. JSON/계약 위반과 DB 저장 예외는 requeue 없이 reject해 결과 DLQ로 보낸다.
- 남은 위험: DB의 일시 장애도 현재는 결과 DLQ로 이동한다. 결과 Queue retry 정책과
  `collection_jobs`/`collection_tasks` 실패 상태 저장은 후속 구현이 필요하다. 실제
  Product Backend 시작점부터 판매처와 PostgreSQL까지의 전체 E2E도 아직 남아 있다.
- 검증:
  - `./gradlew test --tests com.purchasesearch.product_backend.CollectionResultConsumerIntegrationTests --rerun-tasks`: 4개 통합 테스트 통과
  - `./gradlew test --rerun-tasks`: 기존 저장/API 테스트를 포함한 전체 통과

### 2026-08-02 QUEUE-002 Spring Boot CollectionTask 발행 API

- 진행상황: Swagger 또는 관리 화면이 검색 조건을 보내면 Spring Boot가 Queue v1
  `CollectionTask`를 만들고 RabbitMQ broker ACK를 확인한 뒤 `202 QUEUED`를 반환하는
  시작 경로를 구현했다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionTaskController.java:71` `publish`: 수집 요청 HTTP 202 API와 400/503 오류 응답
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectionTaskRequest.java:26` `CollectionTaskRequest`: API 입력과 필터 Bean Validation
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectionTaskMessage.java:22` `CollectionTaskMessage`: Go Worker와 공유하는 Java Queue v1 계약
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectionTaskPublisher.java:73` `publish`: persistent 메시지 발행과 5초 publisher confirm
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectionTaskPublisher.java:111` `createTask`: 기본값, 의미 검증, 추적 ID와 SHA-256 멱등성 키 생성
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/config/RabbitCollectionConfiguration.java:48` `collectionSearchTaskQueue`: Go Worker와 같은 검색/retry/DLQ topology
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionTaskPublisherIntegrationTests.java:71` `publishesSearchTaskThroughHttpEndpoint`: HTTP 요청부터 실제 RabbitMQ 작업 확인
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionTaskPublisherIntegrationTests.java:112` `createsStableIdempotencyKeyForSameSearchConditions`: 동일 조건 멱등성 키 안정성 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionTaskPublisherIntegrationTests.java:136` `rejectsUnsupportedPageBeforePublishing`: 미지원 page의 Queue 유입 차단
- 발생 문제: Spring AMQP 4.1의 `QueueBuilder.ttl()`에 `Duration`을 전달해 첫 compile이
  실패했고, 수신 메시지의 영속성 검증이 `deliveryMode`를 확인해 첫 통합 테스트가 실패했다.
- 원인: 현재 API의 TTL 인자는 밀리초 `int`이며, Spring이 broker에서 받은 메시지의
  영속성은 `receivedDeliveryMode`에 기록한다.
- 해결: Go Worker topology와 같은 `ttl(5000)`을 사용하고 수신 속성 검증을
  `getReceivedDeliveryMode()`로 변경했다.
- 남은 위험: 멱등성 키는 생성하지만 Redis 중복 차단은 아직 없다. `QUEUED` 상태도
  PostgreSQL에 영구 저장하지 않으므로 재시작 뒤 API로 작업 진행 상태를 조회할 수 없다.
  실제 ABC마트/29CM 수집을 포함한 전체 E2E는 사용자가 Product Backend와 Go Worker를
  다시 실행한 뒤 수동 검증해야 한다.
- 검증:
  - `./gradlew compileJava`: 수정 후 통과
  - `./gradlew test --tests com.purchasesearch.product_backend.CollectionTaskPublisherIntegrationTests --rerun-tasks`: 3개 통합 테스트 통과

### 2026-08-03 QUEUE-002 빈 검색 필터 결과 저장 수정

- 진행상황: 실제 Swagger 요청에서 생성된 ABC마트 작업이 Go Worker 수집에는 성공했지만
  Spring 결과 Consumer에서 거절된 문제를 수정했다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectorResult.java:111` `SearchFilters.inStockOnly`: 생략 가능한 Boolean 입력과 false 기본값 정규화
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:108` `storesCollectorResultWithEmptyFilters`: 빈 filters HTTP 저장 회귀 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/CollectionResultConsumerIntegrationTests.java:93` `consumesSuccessfulResultAndStoresProducts`: 빈 filters Queue 결과 저장 회귀 검증
- 발생 문제: `POST /internal/v1/collection-tasks`는 `202 QUEUED`를 반환하고 Go Worker도
  상품 3개를 수집했지만 상품 조회 API는 0개를 반환했으며 결과 DLQ에 메시지 1개가 남았다.
- 원인: Go의 JSON은 false 기본값을 생략해 `filters: {}`를 보냈고, Java Queue DTO의
  원시 `boolean inStockOnly`은 누락된 creator 값을 역직렬화하지 못했다.
- 해결: Java 입력 필드를 nullable `Boolean`으로 바꾸고 저장 map에서는
  `Boolean.TRUE.equals(inStockOnly)`로 false 기본값을 명시했다.
- 남은 위험: 기존 결과 DLQ 메시지는 자동 재처리하지 않는다. 이번 실패 결과는 같은
  CollectorResult를 수동 저장해 복구했으며, 새 코드를 적용하려면 Product Backend를
  재시작해야 한다.
- 검증:
  - 실제 task `task-3b26378e-73e2-419e-9815-3710aa783883`: Go 수집 성공과 결과 DLQ 이동 확인
  - 빈 filters에 false를 적용한 실제 결과 수동 저장: 상품 3개, snapshot 3개, 옵션 19개, 근거 3개
  - `GET /internal/v1/products?merchant=abcmart&query=구두&limit=10`: 복구 저장한 실제 상품 3개 조회
  - `./gradlew test --tests com.purchasesearch.product_backend.ProductStorageIntegrationTests.storesCollectorResultWithEmptyFilters --rerun-tasks`: 통과
  - `./gradlew test --rerun-tasks`: 작업 발행/결과 소비/DB 저장 전체 테스트 통과

### 2026-08-03 OPS-004 Python/Go 비교 Contract와 실행 기준

- 진행상황: 정우님 Python 결과와 현재 Go `CollectorResult`를 같은 조건으로 비교할
  `v1-unified` Schema/20건 예제를 저장소에 반영하고, 운영 계약을 약하게 바꾸지 않는
  비교 Adapter 경계를 설계했다. 언어별 Adapter와 대량 수집 코드는 진행 중이다.
- 구현 위치:
  - `contracts/collector/unified/unified-product.schema.json:1` `통일 상품 스키마 v1-unified`: Python/Go 비교 상품 한 건 규격
  - `contracts/collector/unified/README.md:1` `Python/Go 크롤러 비교 계약`: 운영 `CollectorResult`와 비교 계약의 필드 매핑 및 사용 제한
  - `docs/architecture/Python_Go_크롤러_확장성과_성능_비교_설계.md:1` `Python/Go 크롤러 확장성과 성능 비교 설계`: 최대 10,000개 단계 수집, 안전 중단, Queue/Redis와 성능 측정 기준
  - `docs/reference/크롤링_및_Contract_설계서_v2.0.html:1` `크롤링 및 Contract 설계서 v2.0`: 협업자가 전달한 원본 설계 참고 자료
- 발생 문제: 전달받은 `v1-unified`는 가격을 `"19,000원"` 문자열로 표현하고 상품
  사실의 `provenance`를 포함하지 않아 현재 Product Backend 운영 입력으로 바로 사용할
  수 없었다.
- 원인: 이 Schema는 기존 Python 크롤러 두 판매처 결과를 한 배열로 합치는 목적이며,
  현재 Go/Spring 운영 계약은 정수 가격/통화와 출처 추적을 요구한다.
- 해결: 운영 경계는 `contracts/collector/v1`로 유지하고 `v1-unified`는 언어 성능과
  필드 완전성 비교용 호환 계약으로 분리했다. 각 언어가 별도 Adapter로 변환하도록
  책임을 명시했다.
- 남은 위험: 비교 Adapter가 아직 없고, 원본 Python 구현의 브라우저 기반 ABC마트
  수집과 Go JSON 수집은 네트워크 작업량이 달라 E2E 시간만으로 언어 성능을 단정할 수
  없다. 동일 fixture parser benchmark를 별도로 구현해야 한다.
- 검증:
  - `uvx check-jsonschema --schemafile /private/tmp/unified-products-array.schema.json contracts/collector/unified/examples/unified_구두_top20_20260803_002024.json`: 20건 전체 통과
  - `make docs-check`: 통과
  - `git diff --check`: 통과

### 2026-08-03 OPS-004 Python 대량 수집 코어

- 진행상황: `origin/dev-jw`의 Python ABC마트/29CM Adapter와 Contract 검증 책임만
  선별 이식했다. 현재 구조에서는 두 판매처 모두 공개 검색 JSON을 사용하며, 최대
  10,000개 고유 상품/pagination/중복 제거/checkpoint/요청 예산/timeout/retry/403 및
  429 중단/gzip NDJSON 저장을 공통 실행기가 관리한다. Python은 ABC마트와 29CM에서
  100건/1,000건 단계를 순차 실행했다.
- 구현 위치:
  - `services/python-collector/src/purchase_collector/runner.py:34` `CollectionRunner`: 수집 반복, 중복 제거, checkpoint, 요청 예산과 성능 지표
  - `services/python-collector/src/purchase_collector/merchants/abcmart.py:109` `AbcMartAdapter`: ABC마트 공개 검색 JSON pagination과 비교 상품 변환
  - `services/python-collector/src/purchase_collector/merchants/twentyninecm.py:84` `TwentyNineCmAdapter`: 29CM 공개 검색 JSON pagination과 비교 상품 변환
  - `services/python-collector/src/purchase_collector/contract.py:29` `unified_validator`: 공통 Schema runtime 검증
  - `services/python-collector/tests/test_runner.py:61` `CollectionRunnerTests`: 중복 제거/checkpoint 재개/429 중단 검증
- 발생 문제: 기존 Python ABC마트 구현은 browser를 매 페이지 실행하고 HTML을 저장해,
  현재 Go JSON Adapter와 요청 비용 및 parser 입력이 달랐다. 또한 중단 후 재개 상태와
  요청 예산이 없었다.
- 원인: 기존 구현은 소량 화면 검증 목적이었고 10,000건 성능 비교를 목표로 하지 않았다.
- 해결: 판매처별 JSON 해석은 Adapter에 두고, 요청 반복/안전 상한/저장은 언어와
  판매처에 독립적인 `CollectionRunner`로 분리했다. 새 실행은 401/403/429를 즉시
  중단하며 일시 네트워크/5xx 오류만 설정 상한 안에서 재시도한다.
- 남은 위험: 검색 JSON endpoint는 판매처가 외부 개발자용으로 보장한 API가 아니므로
  구조가 바뀔 수 있다. 여러 검색어를 사용하는 최대 10,000건 단계에서는 검색어 사이
  중복률과 판매처별 실제 고유 상품 상한을 확인해야 한다.
- 추가 문제와 해결: 29CM 1,000건의 마지막 페이지는 50건을 받았지만 목표 도달 뒤
  47건을 저장하지 않았다. 기존 통계에는 이 차이가 보이지 않아
  `skipped_after_target_count`를 추가했다. 또한 공통 client에 있던 29CM Origin/Referer가
  ABC마트 요청에도 붙는 문제를 찾아 29CM Adapter 요청으로 헤더 책임을 옮겼다.
- 재개 통계 문제와 해결: 체크포인트 재개 후 `unique_count`는 누적값이었지만
  `contract_pass_count`와 `missing_field_count`는 새 실행 구간만 표시됐다. checkpoint에
  두 누적값을 저장하고, 이전 형식 checkpoint는 gzip 결과를 읽어 복원하도록 수정했다.
- 검증:
  - `make python-collector-test`: 9개 통과
  - `python3 -m compileall -q src tests`: 통과
  - `git diff --check`: 통과
  - Python ABC마트 100건: 요청 2회, 계약 100/100, 중복 0, 오류/429 0, wall 1.423초
  - Python 29CM 100건: 요청 2회, 계약 100/100, 중복 0, 오류/429 0, wall 1.375초
  - Python ABC마트 1,000건: 요청 20회, 계약 1,000/1,000, 중복 0, 오류/429 0, wall 22.144초
  - Python 29CM 1,000건: 요청 21회, 계약 1,000/1,000, 중복 3, 오류/429 0, wall 23.985초
  - Python ABC마트 최대 단계: 고유 9,417건, 총 요청 420회, 오류/429 0, 검색어 소진으로 중단
  - Python 29CM 최대 단계: 고유 10,000건, 요청 225회, 오류/429 0, wall 271.762초
  - `uvx check-jsonschema`: 최대 단계 19,417건 전체 통과

### 2026-08-04 OPS-004 Python 저장 fixture parser benchmark

- 진행상황: Go와 Python이 공유하는 ABC마트/29CM 원본 JSON fixture를 대상으로 JSON
  decode/판매처별 정규화/`v1-unified` Contract 검증을 반복하는 Python benchmark를
  구현했다. 네트워크 요청은 측정 범위에서 제외했다.
- 구현 위치:
  - `services/python-collector/src/purchase_collector/benchmark.py:19` `fixture_path`: 공통 fixture 경로 선택
  - `services/python-collector/src/purchase_collector/benchmark.py:51` `run_benchmark`: warmup과 wall/CPU/메모리/상품 처리량 측정
  - `services/python-collector/src/purchase_collector/merchants/abcmart.py:108` `parse_page_payload`: ABC마트 순수 페이지 변환
  - `services/python-collector/src/purchase_collector/merchants/twentyninecm.py:84` `parse_page_payload`: 29CM 순수 페이지 변환
  - `services/python-collector/tests/test_benchmark.py:10` `ParserBenchmarkTests`: 양쪽 fixture 측정 경계 검증
- 발생 문제: 실제 수집 wall time은 요청 간 1초 제한과 판매처 응답 시간이 대부분이어서
  Python/Go parser 성능을 직접 설명하지 못한다.
- 원인: E2E 측정에는 네트워크와 의도적인 rate limit이 포함된다.
- 해결: fixture bytes를 메모리에 미리 읽은 뒤 각 반복에서 JSON decode/정규화/Contract
  검증만 실행하고 처리 상품 수를 기준으로 기록했다.
- 남은 위험: fixture가 ABC마트 3개/29CM 2개인 작은 표본이므로 절대 처리량은 환경에
  따라 달라진다. Go도 같은 반복 수와 측정 범위를 사용해야 비교할 수 있다.
- 검증:
  - `uv run python -m unittest discover -s tests -v`: 10개 통과
  - Python ABC마트 1,000회: 3,000개/0.256125초/11,713.042개/초
  - Python 29CM 1,000회: 2,000개/0.143613초/13,926.292개/초
  - `uv run python -m compileall -q src tests`: 통과

### 2026-08-04 OPS-004 dev-jw Python 원본 기준선 이식과 비교

- 진행상황: `origin/dev-jw@e2d863c`에서 Python ABC마트/29CM 크롤러와 ABC마트
  Contract 관련 파일만 `sandbox-python-crawler/ls`에 원본 그대로 선별 이식했다.
  기존 `services/python-collector`와 수집 방식 및 실행 책임을 비교했다. 원본 실행과
  공통 Adapter 연결은 진행 전이다.
- 구현 위치:
  - `purchase-research-agent/app/crawlers/abcmart/crawler.py:67` `AbcMartCrawler`: Crawl4AI와 BeautifulSoup 기반 ABC마트 검색/카테고리 수집 원본
  - `purchase-research-agent/app/crawlers/cm29/crawler.py:23` `Cm29Crawler`: HTTPX와 29CM JSON 응답 기반 검색 수집 원본
  - `purchase-research-agent/app/services/crawler_service.py:12` `CrawlerService`: 판매처 선택/상세 연결/ABC마트 Contract 검증 원본
  - `contracts/collector/v1-abcmart/abcmart-crawl-item.schema.json:1` `ABC마트 크롤러 상품 항목 v1-abcmart`: 정우님 ABC마트 원본 결과 계약
  - `contracts/collector/v1-abcmart/product-batch-request.schema.json:1` `Product Batch Request`: 당시 Spring Boot/MySQL 배치 요청 계약
  - `docs/reports/2026-08-04_dev-jw_Python_크롤러_가져오기와_차이_분석.md:1` `dev-jw Python 크롤러 가져오기와 차이 분석`: 이식 범위와 기존 Python 구현 차이
- 발생 문제: 아직 Git이 추적하지 않는 파일에 일반 `git diff origin/dev-jw`를 사용하자
  원본 브랜치 쪽 파일이 삭제된 것처럼 표시됐다. 또한 sandbox에서 `uvx`가 사용자 cache에
  접근하지 못해 Contract 검사가 처음 실패했다.
- 원인: 일반 Git diff는 현재 index에 없는 untracked 파일을 비교 대상으로 처리하지
  않았고, uv cache는 현재 workspace 밖에 있다.
- 해결: 각 로컬 파일의 `git hash-object`와 `origin/dev-jw:<path>` object ID를 비교해
  byte 동일성을 확인했다. Contract 검사는 승인된 `uvx check-jsonschema` cache 접근으로
  다시 실행했다.
- 남은 위험: 정우님 ABC마트 원본은 browser/HTML을 사용하고 기존 Python/Go 비교 구현은
  JSON을 사용한다. 이 E2E 시간을 언어 성능 차이로 직접 해석하면 안 된다. 또한 원본
  `requirements.txt`는 최소 version만 지정해 재현 가능한 lock 파일이 필요하다.
- 검증:
  - 가져온 로컬 파일과 `origin/dev-jw@e2d863c` object ID 비교: 모두 동일
  - `python3 -m compileall -q purchase-research-agent/app purchase-research-agent/scripts`: 통과
  - `uvx check-jsonschema` ABC마트 crawl 정상 예제: 통과
  - `uvx check-jsonschema` batch request 정상 예제: 통과
  - `make docs-check`: 통과

### 2026-08-04 OPS-004 dev-jw Python 수집 결과 PostgreSQL 수동 적재 연결

- 진행상황: Swagger에서 Python ABC마트/29CM 수집과 현재 Spring Boot Product Backend
  저장을 한 번에 실행하는 `POST /api/v1/collect-and-store`를 구현했다. Python 원본
  상품을 현재 `CollectorResult`로 변환하고 Product Backend만 PostgreSQL을 쓰는 기존
  책임 경계를 유지했다. API는 최대 500개를 받고 Spring Boot 계약 상한에 맞춰 50개씩
  나눠 순차 저장한다.
- 구현 위치:
  - `purchase-research-agent/app/api/endpoints/store.py:18` `CollectAndStoreRequest`: 판매처/검색어/수집 상한/상세 상한 입력 검증
  - `purchase-research-agent/app/api/endpoints/store.py:37` `collect_and_store`: Python 수집/변환/Spring Boot 저장 연결
  - `purchase-research-agent/app/services/collector_result_adapter.py:25` `build_collector_result`: 정우님 원본 상품을 현재 공통 저장 계약으로 변환
  - `purchase-research-agent/app/services/collector_result_adapter.py` `build_collector_result_batches`: 최대 500개 결과를 50개 이하 저장 batch로 분할
  - `purchase-research-agent/app/services/backend_store_service.py:15` `BackendStoreService`: Product Backend 수동 적재 API 호출
  - `purchase-research-agent/tests/test_collector_result_adapter.py:8` `CollectorResultAdapterTests`: 가격/옵션/리뷰/부분 성공 변환 검증
  - `Makefile` `python-crawler-setup`, `python-crawler-run`, `python-crawler-test`: 루트에서 가상환경 준비/서버 실행/검증
- 발생 문제: 정우님 Python 상품은 가격을 원화 문자열로 표현하고 `source_product_id`,
  `title`, `link` 필드를 사용하지만 현재 Spring Boot는 정수 Money, `externalId`, `name`,
  `productUrl`, `provenance`를 요구한다. Python compileall은 macOS 사용자 cache가 sandbox
  밖에 있어 첫 실행이 실패했다.
- 원인: 정우님 원본 Contract와 현재 운영 `CollectorResult`의 목적 및 필드 구조가
  다르며 Python 3.14가 기본 bytecode cache를 사용자 Library 아래에 만들려고 했다.
- 해결: 원본 크롤러를 수정하지 않고 별도 Adapter에서 가격/옵션/리뷰/출처를 변환했다.
  확인할 수 없는 재고와 옵션 재고는 `unknown`으로 저장하고 판매처가 주지 않은
  pagination의 `hasNext`는 `null`로 보존했다. compileall은
  `PYTHONPYCACHEPREFIX=/private/tmp/purchase-research-python-cache`로 다시 실행했다.
- 남은 위험: ABC마트 원본 옵션은 size와 color의 실제 조합 및 재고를 제공하지 않으므로
  Adapter가 조합을 추측하지 않는다. size만 개별 옵션으로 저장하며 stock status는
  `unknown`이다. 실제 판매처/PostgreSQL 수직 검증은 사용자가 세 서비스를 실행한 뒤
  Swagger로 확인해야 한다.
- 검증:
  - `python3 -m unittest discover -s tests -v`: 3개 통과
  - `PYTHONPYCACHEPREFIX=/private/tmp/purchase-research-python-cache python3 -m compileall -q app scripts tests`: 통과
  - 생성한 Python 변환 결과와 `contracts/collector/v1/collector-result.schema.json`: 통과
  - `make docs-check`: 통과
  - `git diff --check`: 통과

### 2026-08-04 OPS-004 Python ABC마트/29CM JSON/HTML 전수 대조와 DB 저장

- 진행상황: ABC마트 JSON 검색 응답을 기본 수집값으로 사용하고 동일한
  검색어와 페이지의 렌더링 HTML을 수집한 모든 상품과 대조하도록
  구현했다. 상품별 검증 상태와 필드별 차이는 Spring Boot를 통해
  PostgreSQL에 저장한다. ABC마트 자동 테스트와 실제 판매처/DB 수동 E2E는
  통과했다. 29CM도 검색 JSON과 공개 상세 HTML JSON-LD 전수 비교, 상품별 DB
  저장 및 실제 3개 수동 E2E가 통과했다.
- 구현 위치:
  - `purchase-research-agent/app/crawlers/abcmart/json_fetcher.py:27` `AbcJsonFetcher`: 공개 검색 JSON pagination, 원본 저장과 Python 상품 변환
  - `purchase-research-agent/app/crawlers/abcmart/crawler.py:71` `AbcMartCrawler.crawl`: 같은 페이지의 JSON/HTML 수집과 전수 대조 흐름
  - `purchase-research-agent/app/crawlers/abcmart/verification.py:23` `reconcile_page`: 상품 ID 기준 필드 비교와 상태 분류
  - `purchase-research-agent/app/crawlers/cm29/crawler.py:36` `Cm29Crawler.crawl`: 검색 JSON 수집과 선택한 모든 상품의 상세 HTML 검증 연결
  - `purchase-research-agent/app/crawlers/cm29/verification.py:28` `verify_products`: 상품별 상세 HTML 저장, JSON-LD 파싱과 순차 비교
  - `purchase-research-agent/app/crawlers/cm29/verification.py:86` `parse_product_json_ld`: SEO Product JSON-LD의 상품/가격/이미지 변환
  - `purchase-research-agent/app/services/collector_result_adapter.py:166` `_convert_verification`: Python 검증 결과를 운영 `CollectorResult` 계약으로 변환
  - `services/product-backend/src/main/resources/db/migration/V3__add_product_verifications.sql:1` `product_verifications`: 상품 snapshot별 검증 결과 schema
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/evidence/entity/ProductVerification.java:35` `ProductVerification`: 상태, 비교 필드와 차이 저장 entity
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:123` `saveVerification`: 검증 결과 transaction 저장
  - `purchase-research-agent/tests/test_abcmart_verification.py:11` `test_marks_every_json_product_with_verification_status`: JSON 상품 전체 상태 부여 검증
  - `purchase-research-agent/tests/test_cm29_verification.py:9` `Cm29VerificationTests`: 29CM JSON-LD 파싱, 일치와 가격 차이 검증
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:87` `storesCollectorResultThroughHttpEndpoint`: Contract 입력의 검증 결과 DB 저장 검증
- 발생 문제: JSON 전용 수집은 구조화된 대량 수집에 유리하지만 실제 화면에
  노출된 값과 동일한지 확인할 경로가 없었다. HTML 표본 검사만으로는 검사하지
  않은 상품의 품질을 설명할 수 없었다.
- 원인: JSON 수집과 browser/HTML 수집이 서로 다른 구현으로 나뉘어 있었고
  검증 결과를 표현하는 Contract와 DB schema가 없었다.
- 해결: JSON을 기본값으로 유지하되 모든 수집 페이지의 HTML을 렌더링했다.
  상품 ID로 연결해 `MATCHED`, `MISMATCH`, `MISSING_IN_HTML`, `FAILED`를
  상품별로 기록하고 검사한 필드와 양쪽 값을 JSONB로 저장하도록 했다.
- 수동 E2E 문제와 해결: `limit: 3`을 요청했지만 한 페이지 30개의 경고가 반환되고
  저장한 3개가 모두 `color`, `style_code` 불일치로 표시됐다. ABC마트 검색
  HTML은 두 필드를 노출하지 않으므로 실제 값 차이가 아닌 비교 대상 오류였다.
  HTML에 노출되는 필드만 비교하고, 무할인 상품의 정상가와 0% 할인율 생략을
  의미상 일치로 처리했다. 또한 실제 `limit` 안에 저장할 상품만 대조 경고에
  포함하도록 수정했다.
- 29CM 문제와 해결: 기존 29CM은 검색 JSON만 저장해 `PENDING`이었고
  `verificationCount`가 0이었다. 검색 페이지는 browser 렌더링 후에도 비교할
  상품 정보가 HTML에 없었지만 공개 상세 HTML의 Product JSON-LD에는 구조화된
  상품 정보가 있었다. browser 없이 상품별 상세 HTML을 요청하고 JSON-LD를
  파싱해 검색 JSON과 전수 대조하도록 수정했다.
- 29CM 수동 E2E 문제와 해결: 최초 재검증은 3개 모두 `discount_percent`
  불일치였다. 검색 JSON은 `sellPrice`와 쿠폰 적용 `displayPrice`를 모두 주며
  `saleRate`는 `displayPrice`를 기준으로 했다. 기존 Python은 저장 가격은 `sellPrice`,
  할인율은 `saleRate`를 사용해 서로 기준이 달랐다. HTML JSON-LD와 같게
  정상가와 일반 판매가로 할인율을 계산하도록 수정했다.
- 원본 파일 정리: JSON과 HTML 원본을 각각
  `output/raw_json/{merchant}`와 `output/raw_html/{merchant}`에 판매처별로 저장하도록
  통일했다. 기존 실행 파일도 삭제하지 않고 같은 하위 디렉토리로 옮겼다.
- 공통 집계와 계약 정렬: Python의 기존 `{"MATCHED": 3}` 상태 map을 Go와 같은
  `total/matched/mismatched/failed/missingInHtml/missingInJson/pending` 구조로
  바꿨다. 각 50개 저장 batch가 자기 상품만 집계하며, Adapter는 전송 전에 공통
  CollectorResult JSON Schema를 강제한다.
- 추가 구현 위치:
  - `purchase-research-agent/app/services/verification_summary.py:18` `summarize_verifications`: Python과 Go의 공통 검증 집계 구조 생성
  - `purchase-research-agent/app/services/collector_result_validation.py:20` `validate_collector_result`: Spring Boot 전송 전 공통 JSON Schema 검증
  - `purchase-research-agent/app/services/collector_result_adapter.py:30` `build_collector_result`: batch별 검증 집계와 계약 검사 연결
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectorResult.java:39` `CollectorResult.VerificationSummary`: Python/Go 공통 집계 역직렬화와 Bean Validation
- 남은 위험: browser 렌더링은 JSON 전용 수집보다 느리고 페이지 스크립트 변경에
  민감하다. 검증 경고가 많은 실행의 응답 크기와 DB 용량 상한도 추가로
  정해야 한다. 실제 대량 수집 시 원본 파일 보존 기간과 용량 제한도 필요하다.
- 검증:
  - `make python-crawler-test`: 13개 통과
  - 수동 E2E에서 저장한 ABC마트 1페이지 JSON/HTML fixture 30개 재비교: `MATCHED` 30개, 경고 0개
  - ABC마트 Swagger 수동 E2E 3개: `MATCHED` 3개, `verificationCount` 3개, 경고 0개
  - 29CM Swagger 수동 E2E 3개: `MATCHED` 3개, `verificationCount` 3개, 경고 0개
  - `services/product-backend/./gradlew test`: `BUILD SUCCESSFUL`
  - `uvx check-jsonschema --schemafile contracts/collector/v1/collector-result.schema.json contracts/collector/v1/examples/collector-result.abcmart-success.json`: 통과
  - `uvx check-jsonschema --schemafile contracts/collector/v1-abcmart/abcmart-crawl-item.schema.json contracts/collector/v1-abcmart/examples/abcmart-crawl-item.valid.json`: 통과

### 2026-08-05 WEB-001 Figma Landing V2 화면

- 진행상황: Next.js 기본 생성 화면을 Figma의 `Desktop / Landing V2 / Editorial Commerce`
  디자인을 반영한 공개 랜딩 화면으로 교체했다. 실제 상품 이미지, 판매처 공간, 검증 지표,
  화면 내 navigation과 데스크톱/모바일 반응형 CSS를 구현했다. 검색 입력과 Agent Gateway
  연결은 아직 구현 전이다.
- 구현 위치:
  - `frontend/purchase-web/app/page.tsx:4` `landingImages`: 저장소 내부 Figma 상품 이미지와 아이콘 경로
  - `frontend/purchase-web/app/page.tsx:16` `Home`: 랜딩 hero, 검색 capsule, 판매처 공간과 검증 지표
  - `frontend/purchase-web/app/page.module.css:1` `.page`: Editorial Commerce 색상과 전체 화면 기반
  - `frontend/purchase-web/app/page.module.css:75` `.hero`: 데스크톱 hero와 상품 collage 배치
  - `frontend/purchase-web/app/page.module.css:420` `@keyframes ticker`: ticker animation과 반응형 규칙
  - `frontend/purchase-web/app/layout.tsx:15` `bodoni/notoSansKr`: 디자인 글꼴과 한국어 문서 metadata
- 발생 문제: 격리 환경에서 production build가 Google Fonts에 접속하지 못해 Geist,
  Bodoni Moda와 Noto Sans KR 다운로드 단계에서 실패했다.
- 원인: `next/font/google`은 build 시점에 글꼴을 내려받지만 기본 검증 환경의 외부 네트워크
  접근이 제한돼 있었다.
- 해결: 승인된 네트워크 환경에서 같은 build를 다시 실행해 글꼴을 내려받고 정적 페이지
  생성을 확인했다. 실제 상품 이미지와 아이콘은 만료되는 Figma URL 대신 `public` 아래에
  저장했다.
- 남은 위험: 데스크톱/모바일 실제 브라우저 화면 확인과 접근성 점검이 필요하다. 검색 capsule은
  정적 예시이며 `/chat`과 Agent Gateway가 구현된 뒤 실제 질문 입력으로 교체해야 한다.
- 검증:
  - `cd frontend/purchase-web && npm run lint`: 통과
  - `cd frontend/purchase-web && npm run build`: 통과, `/` 정적 페이지 생성
  - `git diff --check`: 통과

### 2026-08-05 WEB-002 Figma Chat/Compare V2 사용자 화면

- 진행상황: 공개 랜딩에서 `/chat`으로 진입하고, 구매 조건을 입력한 뒤 정적 추천 후보와
  검증 근거를 확인하며 `/compare`에서 세 상품을 비교하는 사용자 동선을 구현했다. 현재 질문
  제출은 화면의 쇼핑 브리프만 갱신하며 Agent Gateway, Codex, Product Backend와는 연결하지 않았다.
- 구현 위치:
  - `frontend/purchase-web/app/page.tsx:17` `Home`: 랜딩의 질문 및 판매처 CTA를 `/chat`에 연결
  - `frontend/purchase-web/app/chat/chat-experience.tsx:14` `ChatExperience`: 질문 입력, 추천 후보,
    검증 근거와 비교 화면 이동
  - `frontend/purchase-web/app/chat/chat-experience.tsx:19` `handleSubmit`: 사용자 입력을 정적 쇼핑
    브리프에 반영하는 UI 시제품 동작
  - `frontend/purchase-web/app/chat/page.module.css:1` `page`: Figma Chat V2 기반 데스크톱/모바일 스타일
  - `frontend/purchase-web/app/compare/page.tsx:15` `ComparePage`: 상품 세 개의 가격, 재고, 검증 근거와
    최종 선택 근거 표시
  - `frontend/purchase-web/app/compare/page.module.css:1` `page`: Figma Compare V2 기반 반응형 스타일
- 발생 문제: Figma가 생성한 코드는 Tailwind 절대 좌표 기반이어서 그대로 사용하면 작은 화면에서
  내용이 잘리고 프로젝트의 CSS Module 방식과 맞지 않았다.
- 원인: 디자인 원본은 1440px 고정 프레임이며 Figma MCP 출력은 디자인 전달용 참고 코드다.
- 해결: 색상, 서체, 간격과 상품 이미지는 유지하면서 CSS Grid/Flex와 반응형 media query를 사용한
  CSS Module로 변환했다. 만료되는 Figma 이미지 주소 대신 기존 `public/images/landing-v2` 자산을
  재사용했다.
- 남은 위험: 실제 브라우저에서 데스크톱/모바일 화면과 키보드 접근성을 확인해야 한다. 추천 값은
  예시 데이터이므로 실제 서비스 주장으로 사용할 수 없으며 Agent Gateway 응답으로 교체해야 한다.
- 검증:
  - `cd frontend/purchase-web && npm run lint`: 통과
  - `cd frontend/purchase-web && npm run build`: 통과, `/`, `/chat`, `/compare` 정적 페이지 생성

### 2026-08-05 BACKEND-001/WEB-002 PostgreSQL 상품 후보 화면 연결

- 진행상황: Product Backend에 사용자 질문 문맥과 명시적 검색어를 받는 상품 후보 API를
  추가했다. Next.js server route가 이 API를 호출하며 `/chat`과 `/compare`는 고정 상품 대신
  실제 DB 후보의 가격, 재고, 판매처, 공개 출처와 수집 시각을 표시한다. MCP와 Codex는 아직
  연결하지 않았다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/controller/ProductCandidateController.java:48` `search`: 사용자 질문용 상품 후보 HTTP API
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductCandidateService.java:34` `findCandidates`: 기존 상품 검색을 후보 응답으로 변환
  - `frontend/purchase-web/app/api/product-candidates/route.ts:47` `handleProductCandidateRequest`: server 전용 backend 중계와 오류 경계
  - `frontend/purchase-web/app/chat/chat-experience.tsx:20` `ChatExperience`: DB 후보를 표시하는 구매 질문 화면
  - `frontend/purchase-web/app/compare/compare-experience.tsx:17` `CompareExperience`: DB 후보 세 개 비교 화면
- 발생 문제: 새 `package.json` test script 때문에 문서 동기화 검사가 외부 구성요소 공개 문서
  갱신을 요구했고, React lint는 effect 시작 시 동기 상태 갱신을 거절했다.
- 원인: 문서 검사기는 manifest 변경 자체를 감지하며 React 19 lint는 effect 안의 즉시 `setState`
  호출을 연쇄 rendering 위험으로 본다.
- 해결: 새 외부 package가 없고 Node 내장 test runner를 사용한다는 내용을
  `THIRD_PARTY_NOTICES.md`에 기록했다. 초기 조회는 promise 완료 callback에서만 상태를 갱신하고
  사용자 제출 시에만 loading 상태를 즉시 변경하도록 분리했다.
- 남은 위험: Product Backend 프로세스가 실행 중이지 않아 Next.js server route를 거친 실제
  HTTP E2E와 browser 렌더링은 아직 확인하지 못했다. 후보 순서는 최신 수집 순서이며 추천
  점수나 최저가 순위가 아니다.
- 검증:
  - `cd services/product-backend && ./gradlew test --tests com.purchasesearch.product_backend.ProductStorageIntegrationTests`: 통과
  - `cd frontend/purchase-web && npm test`: 통과, 정상/400/최대 3개/502/503 5개
  - `cd frontend/purchase-web && npm run lint`: 통과
  - `cd frontend/purchase-web && npm run build`: 통과, 동적 server route 생성
  - `make docs-check`: 통과
  - `git diff --check`: 통과

### 2026-08-05 BACKEND-001/WEB-002 실제 상품 후보 HTTP E2E 검증

- 진행상황: 사용자가 실행한 PostgreSQL, Spring Boot와 Next.js를 대상으로 Product Backend
  직접 호출과 Next.js server route 호출을 검증했다. `구두` 검색은 DB의 503건 중 최대 후보
  3건을 반환했고, 두 응답에는 같은 상품/가격/재고/공개 출처/수집 시각이 포함됐다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/controller/ProductCandidateController.java:48` `search`: 실제 PostgreSQL 후보 조회 HTTP 진입점
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductCandidateService.java:34` `findCandidates`: 후보 최대 3건 조회 및 질문 문맥 보존
  - `frontend/purchase-web/app/api/product-candidates/route.ts:47` `handleProductCandidateRequest`: Spring Boot 응답 중계와 입력/timeout/장애 경계
  - `frontend/purchase-web/app/chat/chat-experience.tsx:20` `ChatExperience`: 실제 후보를 표시하도록 구성된 채팅 화면
  - `frontend/purchase-web/app/compare/compare-experience.tsx:17` `CompareExperience`: 같은 질문과 검색어로 후보를 다시 조회하는 비교 화면
- 발생 문제: 최초 결과 확인용 `jq` 경로를 최상위 `sourceUrl`로 잘못 지정해 근거가 `null`처럼
  보였고, 자동 브라우저 연결을 사용할 수 없어 실제 화면 렌더링을 확인하지 못했다.
- 원인: 근거 계약은 후보의 `source.sourceUrl`과 `source.collectedAt`에 중첩돼 있다. 현재
  세션에는 연결 가능한 자동 브라우저가 없었다.
- 해결: 전체 후보 JSON을 다시 확인해 중첩된 근거 URL, 수집 시각과 Collector 버전을 검증했다.
  Spring Boot와 Next.js 응답을 정렬 비교했으며 숫자의 소수점 직렬화 표현 외 실제 값은 같았다.
- 남은 위험: `/chat`과 `/compare`의 실제 데스크톱/모바일 렌더링, 키보드 접근성과 화면에서의
  오류 표시를 수동 브라우저로 확인해야 한다. 추천 점수와 자연어 답변은 Agent Gateway와 MCP
  연결 전이므로 아직 제공하지 않는다.
- 검증:
  - Product Backend 실제 후보 요청: HTTP 200 / `totalCount=503` / `candidateCount=3`
  - Next.js server route 실제 후보 요청: HTTP 200 / 같은 후보 3건과 근거 반환
  - Product Backend와 Next.js 빈 결과 요청: 각각 HTTP 200 / `candidateCount=0`
  - Next.js `limit=4` 요청: HTTP 400 / `INVALID_REQUEST`

### 2026-08-05 MCP-001 Product Backend 조사 세션 MCP 도구

- 진행상황: TypeScript와 공식 MCP SDK 기반 stdio server를 구현하고 조사 세션 생성, 사용자
  조건 확인과 확정 조건 상품 검색 도구를 Product Backend REST API에 연결했다.
- 구현 위치:
  - `services/mcp-server/src/index.ts:26` `main`: stdio 연결과 세 MCP 도구 등록
  - `services/mcp-server/src/backend-client.ts:43` `ProductBackendClient`: Product Backend 전용 REST client
  - `services/mcp-server/src/mcp-smoke.test.ts:8` `stdio MCP 서버가 조사 세션 도구를 공개한다`: child process 초기화와 tool list 검증
  - `.codex/config.toml:1` `mcp_servers.purchase_research`: 저장소 범위 Codex MCP 설정
  - `plugins/purchase-research-agent/.mcp.json:1` `purchase_research`: Plugin MCP 실행 설정
- 발생 문제: Node의 TypeScript strip-only 실행은 constructor parameter property를 지원하지
  않았고 `.ts` import 확장자는 NodeNext 빌드 설정과 충돌했다.
- 원인: source를 직접 실행하는 Node 실험 기능과 TypeScript compiler의 module 해석 규칙이 달랐다.
- 해결: 모든 MCP 테스트를 `tsc`로 빌드한 뒤 `dist/*.test.js`에서 실행하도록 통일했다.
- 남은 위험: Product Backend 실제 실행 환경에서 Codex가 MCP 도구를 선택하는 전체 E2E와
  timeout/취소/동시 실행 상한이 남아 있다.
- 검증:
  - `cd services/mcp-server && npm test`: 통과, REST client 2개와 stdio tool list 1개

### 2026-08-05 MCP-002 AI 구매 조건과 조사 세션 확인 경계

- 진행상황: AI가 자연어 질문에서 생성할 `PurchaseCondition` 공통 Schema를 정의하고,
  Spring Boot가 이를 DRAFT 조사 세션으로 저장하도록 구현했다. 사용자가 조건을 확인하기 전
  상품 검색은 HTTP 409로 차단하며, CONFIRMED 이후에만 기존 PostgreSQL 후보 검색을 실행한다.
- 구현 위치:
  - `contracts/research/v1/purchase-condition.schema.json:1` `PurchaseCondition`: AI 구조화 출력 공통 계약
  - `services/product-backend/src/main/resources/db/migration/V5__add_research_sessions.sql:1` `research_sessions`: 질문, AI 실행 환경, Plugin과 조건 확인 상태 저장
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/dto/PurchaseCondition.java:20` `PurchaseCondition`: Java Bean Validation 계약
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/service/ResearchSessionService.java:20` `ResearchSessionService`: DRAFT 생성, 사용자 확인과 확인 후 검색 경계
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/controller/ResearchSessionController.java:27` `ResearchSessionController`: 조사 세션 생성/확인/검색 REST API
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:330` `createsDraftResearchSessionWithoutSearchingProducts`: 실제 PostgreSQL 조사 세션 통합 검증
- 발생 문제: 사용자 확인과 상품 검색을 한 API에서 함께 처리하면 MCP의 검색 도구가 확인 상태를
  독립적으로 검사했다는 사실을 검증하기 어려웠다.
- 원인: 최초 구현은 confirm 처리 안에서 즉시 상품을 검색해 미확정 검색 요청 자체가 존재하지 않았다.
- 해결: confirm과 search API를 분리하고 search가 저장된 CONFIRMED 상태를 다시 검사하도록 했다.
- 남은 위험: 현재 조건 검색은 상품 종류와 판매처만 적용하며 가격/색상/사이즈 조건 적용은 후속
  검색 Adapter에서 구현해야 한다. Codex 구조화 출력과 MCP 연결은 아직 진행 중이다.
- 검증:
  - `cd services/product-backend && ./gradlew test --tests com.purchasesearch.product_backend.ProductStorageIntegrationTests`: 통과

### 2026-08-06 BACKEND-001 사용자 확인 조건 DB 필터

- 진행상황: CONFIRMED 조사 세션의 상품 종류/판매처/최소 가격/최대 가격/통화/사이즈/색상과
  최신 재고 상태를 PostgreSQL query에 적용했다. 검색 결과의 `totalCount`와 `hasNext`도 같은
  조건을 사용한다.
- 구현 위치:
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/repository/MerchantProductRepository.java:180` `searchCandidates`: 최신 offer와 옵션 조건 SQL
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductCandidateService.java:72` `findCandidates(String, PurchaseCondition)`: 확인 조건 정규화와 후보 응답 변환
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductQueryService.java:86` `searchCandidates`: 조건 query와 상품 요약 연결
  - `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:422` `appliesConfirmedPriceConditionToResearchCandidates`: 최대 가격 초과 상품 제외 검증
- 발생 문제: 첫 실제 E2E에서 10만 원 이하 조건인데 119,280원 상품이 후보에 포함됐다.
- 원인: 조사 세션 검색이 `productType`과 `merchant`만 기존 상품 검색 API에 전달하고 나머지
  확인 조건을 사용하지 않았다.
- 해결: PostgreSQL의 판매처 상품별 최신 snapshot을 기준으로 가격과 재고를 검사하고, 같은
  snapshot의 재고 있는 옵션에서 사이즈와 색상을 검사하도록 query를 확장했다. 한국어 기본
  색상명과 `270mm` 표기는 Collector 값인 `BLACK`과 `270`으로 정규화한다.
- 남은 위험: 용도와 편안함 같은 의미 조건은 아직 리뷰 분석 및 점수 기능이 없어 DB 필터로
  적용하지 않는다. 최신 snapshot에 옵션이 없으면 오래된 옵션을 현재 사실처럼 사용하지 않아
  결과에서 제외된다.
- 검증:
  - `./gradlew test --tests com.purchasesearch.product_backend.ProductStorageIntegrationTests`: 통과
  - 실제 Go 옵션 재수집 후 Next/Codex/MCP/Spring/PostgreSQL E2E: `구두/검정/270/10만 원 이하` 조건에서 ABC마트 페니 로퍼 69,000원 1건 반환

### 2026-08-06 MCP-002/WEB-002 Codex 구매 조건 확인 E2E

- 진행상황: Next.js server Agent Gateway가 Codex CLI를 읽기 전용으로 실행해 Plugin 규칙과
  공통 Schema에 맞는 구매 조건을 생성한다. browser에는 DRAFT 조건을 먼저 표시하고, 사용자가
  수정하거나 확인한 뒤에만 MCP 확인 및 PostgreSQL 후보 검색을 실행한다.
- 구현 위치:
  - `frontend/purchase-web/app/lib/codex-runtime.ts:58` `runCodexCommand`: shell 없는 Codex child process/환경 변수 allowlist/90초 timeout
  - `frontend/purchase-web/app/lib/codex-runtime.ts:122` `normalizePurchaseCondition`: 색상이 중복된 AI 상품 종류 검색어 정규화
  - `frontend/purchase-web/app/lib/codex-runtime.ts:140` `structurePurchaseQuestion`: Plugin skill과 공통 Schema 기반 조건 구조화 및 동시 실행 1개 제한
  - `frontend/purchase-web/app/lib/research-mcp-client.ts:50` `StdioResearchMcpClient`: 공식 MCP client의 조사 세션 도구 호출과 process 정리
  - `frontend/purchase-web/app/api/research/conditions/handler.ts:36` `handleConditionsRequest`: Codex 결과를 MCP DRAFT로 저장하는 오류 경계
  - `frontend/purchase-web/app/api/research/confirm/handler.ts:26` `handleConfirmRequest`: 미확정 조건 차단과 확인 후 MCP 검색
  - `frontend/purchase-web/app/chat/chat-experience.tsx:31` `ChatExperience`: Codex 선택/질문/조건 수정/확인/실제 후보 상태 화면
  - `frontend/purchase-web/app/lib/codex-runtime.test.ts:38` `상품 종류에서 구조화된 색상 중복을 제거한다`: AI 조건 중복이 DB 검색을 과도하게 좁히지 않는지 검증
- 발생 문제: Next.js 16은 `route.ts`의 HTTP method 외 helper export를 거절했고, 기존 Google
  font가 오프라인 production build를 중단했다. 첫 조건 검색은 최신 Python snapshot에 옵션이
  없어 0건이었으며 자동 브라우저 목록도 비어 있었다.
- 원인: Next Route Handler export 계약을 이전 구조가 따르지 않았고 build 시 Google font
  다운로드에 의존했다. 상품 조건 query는 의도대로 오래된 옵션을 현재 사실로 사용하지 않았다.
  현재 실행 환경에는 연결 가능한 browser backend가 없다.
- 해결: 실제 Route 진입점과 테스트 가능한 handler를 분리하고 system font fallback을 사용했다.
  Go Collector로 최신 옵션을 소량 재수집해 조건이 증명되는 후보만 다시 검증했다. browser 자동
  검증은 우회 도구를 사용하지 않고 미검증으로 남겼다.
- 남은 위험: 실제 데스크톱/모바일 browser 클릭과 접근성, 응답 stream, 사용자 취소, stale 표시,
  구매 전 재검증 및 Plugin marketplace 설치 검증이 남아 있다.
- 검증:
  - `cd frontend/purchase-web && npm test`: 정상/조건 부족/잘못된 AI JSON/AI 실패/미확정 차단/MCP 실패/빈 결과/동시 실행 테스트 통과
  - `cd frontend/purchase-web && npm run lint`: 통과
  - `cd frontend/purchase-web && npm run build -- --webpack`: production build 통과
  - 실제 Codex CLI 구조화: `구두/출근/10만 원/검정/270`과 추가 확인 조건 JSON 반환
  - 실제 Next/Codex/MCP/Spring/PostgreSQL E2E: DRAFT에는 결과 없음, CONFIRMED 후 조건 일치 후보 1건 반환
  - 자동 browser E2E: browser 목록이 비어 있어 미검증

### 2026-08-06 MCP-002/WEB-002 Python 브랜치 동일 사용자 E2E

- 진행상황: Python 크롤러 브랜치에 공통 PurchaseCondition, Spring 조사 세션, MCP Server와
  Next.js Codex Gateway를 동기화했다. Python 29CM 실제 수집 결과를 Spring Boot로 저장한 뒤
  같은 화면 API에서 사용자 확인 전 DRAFT와 확인 후 후보 검색을 검증했다.
- 구현 위치:
  - `purchase-research-agent/app/api/endpoints/store.py:38` `collect_and_store`: Python 수집 결과를 Spring Boot 저장 API에 전달
  - `purchase-research-agent/app/services/verification_summary.py:17` `summarize_verifications`: Python JSON/HTML 검증을 공통 상태로 집계
  - `services/product-backend/src/main/resources/db/migration/V4__add_collection_job_tracking.sql:1` `collection_jobs`: Go/Python 브랜치가 같은 Flyway 이력을 해석하도록 유지하는 공통 migration
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/service/ResearchSessionService.java:45` `create`: DRAFT 저장
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/service/ResearchSessionService.java:60` `confirm`: 사용자 확인 조건 저장
  - `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/service/ResearchSessionService.java:78` `search`: 확인 세션만 후보 검색 허용
  - `services/mcp-server/src/index.ts:35` `main`: 조사 세션 MCP 도구 세 개 등록
  - `frontend/purchase-web/app/lib/codex-runtime.ts:122` `normalizePurchaseCondition`: 구조화 색상과 상품 종류 중복 제거
- 발생 문제: 공용 PostgreSQL에는 Go 브랜치 V4 migration이 적용됐지만 Python 브랜치에는 같은
  파일이 없어 Flyway 검증이 서버 시작을 차단했다. 실제 Codex가 색상을 `colors`와
  `productType` 양쪽에 넣어 첫 DB 검색 결과가 0건이었다.
- 원인: 브랜치별 Spring migration 이력이 달랐고 AI 출력 Schema는 필드 형식만 검사했으며
  의미상 중복까지 제한하지 않았다.
- 해결: V4 migration 파일을 같은 내용으로 공유하고, 프롬프트에서 상품 종류와 속성을 분리하도록
  명시했다. 출력 후에도 구조화 색상을 상품 종류에서 제거해 DB 검색어를 안정화했다.
- 남은 위험: 실제 browser 클릭과 접근성 검증은 연결 가능한 browser가 없어 미검증이다. 의미 조건
  검색, stream, 사용자 취소와 구매 전 재검증도 후속 범위다.
- 검증:
  - Python 원본 크롤러 단위 테스트 13개: 통과
  - Python 비교 Collector 단위 테스트 10개: 통과
  - `cd services/product-backend && ./gradlew test`: 통과
  - `cd services/mcp-server && npm test`: 통과, 3개
  - `cd frontend/purchase-web && npm test`: 통과, 17개
  - `npm run lint`, `npm run build`, `npm audit --omit=dev --offline`: 통과
  - 실제 Python 29CM 수집/HTML 검증/Spring 저장: 3건 수집, 3건 MATCHED, 3건 저장
  - 실제 Next/Codex/MCP/Spring/PostgreSQL E2E: DRAFT 결과 없음, 확인 후 페니 로퍼 69,000원 1건

### 2026-08-06 OPS-004 Python/Go 최대 10,000개 최신 성능 재검증

- 진행상황: 양쪽 언어에서 ABC마트와 29CM 공개 검색을 같은 10개 검색어, 페이지 크기 50,
  요청 간격 1초, timeout 15초와 요청 예산 500으로 순차 실행했다. 검색 JSON 수집, 정규화,
  공통 Contract 검증, 중복 제거와 gzip NDJSON 저장을 같은 측정 경계로 사용했다.
- 구현 위치:
  - `services/python-collector/src/purchase_collector/runner.py:110` `_collect`: Python pagination, 중복 제거, Contract와 checkpoint 실행
  - `services/collector/internal/bulk/runner.go:168` `Runner.collect`: Go pagination, 중복 제거, Contract와 checkpoint 실행
  - `services/collector/internal/merchants/abcmart/search.go:112` `NewBulkSearcher`: Go 검색 성능 측정에서 운영 HTML rendering 경계를 분리
  - `services/collector/internal/merchants/twentyninecm/search.go:121` `NewBulkSearcher`: Go 검색 성능 측정에서 상세 HTML 검증 경계를 분리
- 발생 문제: Go 비교 명령이 운영용 Searcher를 사용해 ABC마트 첫 페이지에서 Chrome HTML 검증까지
  실행했고 50개 처리에 약 166초가 걸렸다.
- 원인: 운영 전수 검증 기능을 추가하면서 대량 검색 benchmark 생성자도 같은 기능을 자동 사용했다.
- 해결: 운영 API와 Worker는 JSON/HTML 전수 검증을 유지하고, 비교 CLI만 검색 JSON 처리 성능을
  측정하는 별도 생성자를 사용하도록 경계를 분리했다. 중단한 첫 실행은 최종 결과에서 제외했다.
- 남은 위험: wall 시간 대부분은 판매처 응답과 의도적인 1초 간격이므로 언어 자체의 parser 성능은
  저장 fixture benchmark를 함께 봐야 한다. 누락 필드 수는 언어별 계산 범위가 달라 품질 비교에
  직접 사용하지 않는다.
- 검증:
  - Go ABC마트: 고유 9,429/받음 20,744/중복 11,315/요청 421/오류 0/wall 484.601초/CPU 2.529초/메모리 23,248 KiB/검색어 소진
  - Python ABC마트: 고유 9,429/받음 20,744/중복 11,315/요청 421/오류 0/wall 482.928초/CPU 4.876초/메모리 49,888 KiB/검색어 소진
  - Go 29CM: 고유 10,000/받음 10,675/중복 653/요청 214/오류 0/wall 258.517초/CPU 1.129초/메모리 23,056 KiB/목표 도달
  - Python 29CM: 고유 10,000/받음 10,675/중복 664/요청 214/오류 0/wall 259.126초/CPU 2.654초/메모리 47,024 KiB/목표 도달

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

- RabbitMQ를 통한 ABC마트/29CM 결과 PostgreSQL 적재 E2E
- `collection_jobs`와 `collection_tasks` 작업 상태 저장
- 최초 상품 동시 upsert 충돌 처리
- JSON Schema 직접 검증과 공통 오류 응답
- 실제 browser 기반 구매 질문 E2E와 접근성 검증
