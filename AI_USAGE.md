# AI Usage

최종 갱신일: 2026-08-11

## 이 문서를 공개하는 이유

[2026년 오픈소스 개발자대회 운영규정](https://api.osscontest.kr/static/uploads/b3b4491a-3bbe-454e-a1d8-6ed475b01b14.pdf) 7쪽 제9조 제5항은 GPT와 Claude 등을 코드 작성 및 디버깅에 사용하는 것을 허용한다. 다만 참가자가 AI 작성 코드를 충분히 이해하지 못하면 감점될 수 있다고 명시한다.

운영규정이 `AI_USAGE.md`라는 별도 파일명이나 개발 보조 AI 사용 기록 양식을 요구한 것은 아니다. 이 프로젝트는 AI를 사용한 개발 범위와 사람의 검토 책임을 공개하고, 팀원이 구현을 설명할 수 있다는 근거를 남기기 위해 이 파일을 자발적으로 운영한다.

운영규정 7쪽 제9조 제4항의 실행 model 정보 제출은 별도 사항이다. AI model을 결과물에 내장하거나 적용하면 주최 측 지정 양식에도 model 정보를 제출해야 하며, 이 문서만으로 해당 제출을 대신할 수 없다.

## 개발 과정에서 사용하는 AI

| 도구 | 사용 범위 | 사람이 책임지는 검토 |
|---|---|---|
| OpenAI Codex | 저장소 분석, 설계 대화, Go/Java/TypeScript 코드 초안과 수정, test 보조, 문서 작성 | 요구사항 확정, diff 검토, 실행 검증, commit 승인, architecture 결정 |
| Anthropic Claude Code | 설계 대화, 코드와 문서 작성 보조 | 작업자가 실제 사용 범위와 변경 파일을 기록하고 동일한 검토 절차 수행 |

특정 파일의 작성자를 근거 없이 AI 도구 하나로 단정하지 않는다. AI가 중요한 구현을 생성하거나 수정한 작업은 commit, PR 또는 개발 진행 관리 문서에 사용 도구와 사람이 확인한 내용을 기록한다.

## 사람의 검토 원칙

- AI가 제안한 설계는 팀의 구성요소 책임과 금지 경계를 기준으로 사람이 결정한다.
- 코드 변경은 `git diff`로 확인하고 관련 unit/integration/contract test를 실행한다.
- 실제 판매처 수집은 정책을 우회하지 않으며 opt-in smoke test로 제한한다.
- AI가 만든 코드라도 팀원이 주요 흐름, 실패 조건, 데이터 출처 및 보안 영향을 설명할 수 있어야 한다.
- AI는 자동으로 commit, push, PR merge를 결정하지 않는다. Git 단계별 사용자 승인 규칙을 따른다.
- API key, cookie, session, 개인 정보가 prompt, 코드 및 commit에 포함되지 않도록 사람이 확인한다.
- 검증하지 않은 AI 설명과 생성된 상품 사실을 사용자에게 근거처럼 제시하지 않는다.

## 실행 시 사용하는 AI의 현재 상태

- Codex Plugin은 `purchase-research-agent` workflow와 로컬 stdio MCP 설정을 포함한다.
- Purchase Research MCP Server는 조사 세션 생성/조건 확인/확정 조건 상품 검색과
  상품 상세/공개 근거/후보 비교 도구를 Product Backend REST API에 연결한다.
  PostgreSQL과 판매처에는 직접 접근하지 않는다.
- MCP의 `request_collection`과 `verify_offer`는 Product Backend에 작업을 요청하고
  `get_verification_status`는 저장된 job 및 snapshot 비교 결과만 조회한다. MCP가
  RabbitMQ와 Python 크롤러를 직접 호출하지 않는다.
- Next.js Agent Gateway는 server에서 Codex CLI를 읽기 전용/비대화형으로 실행해
  `PurchaseCondition` JSON만 생성하고 Plugin skill 규칙을 prompt에 적용한다.
- Codex CLI 인증 만료와 일반 실행 실패는 server에서 안전한 오류 코드로 변환한다.
  Plugin prompt, OAuth 오류 원문과 child process stderr는 browser 응답에 노출하지 않는다.
- 사용자가 조건을 확인한 뒤 공식 MCP client가 MCP Server의 확인 및 검색 도구를 호출한다.
  Codex 인증정보와 Product Backend 내부 주소는 browser에 전달하지 않는다.
- Product Backend의 PostgreSQL 전문 검색과 `pg_trgm` 유사도 검색은 결정론적 DB 검색이며
  runtime AI model이나 embedding을 사용하지 않는다.
- 선택 설정 `EMBEDDING_PROVIDER=ollama`는 상품명/브랜드/category/수집 검색어/판매처와
  사용자의 검색 질문을 로컬 Ollama `bge-m3:567m`에 전송해 1024차원 embedding을 만든다.
  model 제공자는 BAAI, 공개 weight/license 표시는 MIT, 실행 위치는 운영자가 설정한 로컬
  Ollama endpoint다. 가격/재고/옵션/개인정보는 embedding 입력에 포함하지 않는다.
- embedding adapter는 기본 비활성화 상태다. model 없음/timeout/응답 계약 오류 시
  PostgreSQL 전문 검색과 trigram 검색만 사용하며 weight를 이 저장소에 포함하지 않는다.
- Claude Code 연동, Ollama, llama.cpp 및 GPU model server는 계획 단계다.
- 현재 저장소에 직접 포함한 AI model weight는 없다.
- runtime model을 추가하면 model 이름/version/제공자/출처/weight 공개 여부/license/실행 위치/전송 데이터/사용 목적을 기록한다.

## 변경 시 갱신 규칙

다음 변경은 같은 commit에서 이 문서를 갱신한다.

- Codex, Claude Code 또는 다른 AI가 중요한 코드/설계/문서를 생성하거나 수정한 경우
- Plugin, MCP Server, Agent Gateway 또는 model adapter를 추가/삭제/변경한 경우
- runtime AI model, provider, version, 실행 방식 또는 전송 데이터가 바뀐 경우
- 사람 검토 절차와 AI 권한 범위가 바뀐 경우

`make docs-check`는 AI integration 관련 설정이 바뀌었는데 이 문서가 함께 변경되지 않은 경우 실패한다. 일반 source code가 AI 도움을 받았는지는 Git만으로 자동 판별할 수 없으므로 작업자와 에이전트가 이 규칙을 지켜 기록해야 한다.

## 변경 기록

| 날짜 | 내용 | 검토 방법 |
|---|---|---|
| 2026-07-30 | 최초 AI 사용 공개 문서 작성 / Codex와 Claude Code 개발 보조 범위 및 runtime AI 미구현 상태 기록 | 저장소 구조, 현재 manifest, 운영규정 7쪽 제9조 제4항과 제5항 대조 |
| 2026-07-30 | Codex를 사용해 Product Backend의 Flyway/JPA 저장과 조회 API를 구현하고 package-by-feature 구조로 수정 | 사용자가 package 구조를 결정하고 diff 검토 및 Testcontainers integration test 수행 |
| 2026-07-31 | Codex를 사용해 기능 ID 목록, 코드트래커, 진행상황 점검 스킬과 문서 추적 절차를 구성 | 사용자가 제안한 문서 역할을 세 스킬로 분리하고 스킬 형식 검사, 문서 동기화 검사 및 diff 검토 |
| 2026-07-31 | Codex를 사용해 Collector JSON 수동 적재 API와 정상/실패 통합 테스트를 구현 | 사용자가 수동 적재 경로를 결정하고 Testcontainers PostgreSQL 기반 전체 Spring Boot 테스트와 diff를 검토 |
| 2026-07-31 | Codex를 사용해 Spring Boot 내부 API에 OpenAPI 문서와 Swagger UI를 추가 | 공식 springdoc-openapi 문서와 라이선스를 확인하고 Swagger/OpenAPI 통합 테스트 및 운영 profile 기본 비활성화를 검토 |
| 2026-08-02 | Codex를 사용해 Collector 검색어/filters 전달, PostgreSQL 검색 문맥 저장과 수집 검색어 기반 상품 조회를 구현 | 사용자가 문제 상황과 목표를 확인하고 Go 전체 테스트, Flyway V2를 포함한 Spring Boot PostgreSQL 통합 테스트 및 diff를 검토 |
| 2026-08-02 | Codex를 사용해 Spring Boot RabbitMQ 결과 Consumer, 수동 ACK, 결과 DLQ와 PostgreSQL 자동 저장 경로를 구현 | RabbitMQ/PostgreSQL Testcontainers로 성공 저장, 정상 실패 무저장, 식별자 불일치 거절과 계약 위반 DLQ 이동을 검증 |
| 2026-08-02 | Codex를 사용해 Spring Boot 수집 요청 API, CollectionTask 발행, 검색 Queue topology와 publisher confirm을 구현 | 사용자가 전체 Queue 구조를 확인하고 RabbitMQ Testcontainers로 HTTP 202, 계약 필드, persistent 메시지, 멱등성 키와 미지원 page 거절을 검증 |
| 2026-08-03 | Codex를 사용해 실제 ABC마트 Queue E2E의 빈 filters 역직렬화 실패를 진단하고 Java DTO 기본값 처리를 수정 | 실제 RabbitMQ Queue/DLQ와 PostgreSQL을 확인하고 빈 filters 수동 저장, HTTP 저장 회귀 테스트와 결과 Consumer 통합 테스트로 검증 |
| 2026-08-03 | Codex를 사용해 전달받은 Python 통합 Schema를 운영 CollectorResult와 분리하고 Python/Go 최대 10,000개 수집 및 성능 비교 설계를 작성 | 사용자가 Python/Go 비교 목표와 브랜치 경계를 결정하고 Schema/20건 예제/dev-jw 구현/현재 Go DTO를 대조해 필드 변환과 검증 기준을 확인 |
| 2026-08-03 | Codex를 사용해 dev-jw Python 크롤러의 판매처 Adapter를 선별 이식하고 pagination/중복 제거/checkpoint/요청 예산/안전 중단/공통 계약 검증 코어를 구현 | 저장된 ABC마트/29CM fixture, 전달받은 20건 예제, checkpoint 재개와 429 중단을 7개 자동 테스트로 검증 |
| 2026-08-09 | Codex를 사용해 정우님 Python 크롤러 실행환경을 Python 3.12/uv.lock으로 고정하고 루트 실행/테스트 명령을 uv 기준으로 전환 | 사용자가 Python runtime 통합 방향을 승인하고 13개 단위 테스트, FastAPI health, aio-pika import와 로컬 RabbitMQ 연결을 검증 |
| 2026-08-09 | Codex를 사용해 Python 크롤러를 Spring Boot CollectionTask/CollectionResult v1과 연결하고 RabbitMQ ACK/retry/DLQ/publisher confirm 경로를 구현 | 사용자가 Python 최종 전환 방향과 자동 작업을 승인하고 24개 Python 테스트 및 격리된 로컬 RabbitMQ vhost의 success/retry/DLQ 통합 경로를 검증 / 실제 판매처 요청은 실행하지 않음 |
| 2026-08-09 | Codex를 사용해 Python Collector의 TLS 검증 비활성화와 자동 redirect를 제거하고 HTTP 접근 오류를 안전하게 분류 | 사용자가 손으로 확인할 작업을 제외한 자동 구현을 승인하고 401/403 non-retryable, 429/5xx retryable, URL/query/traceback 비노출과 금지 설정 정적 검사를 fixture로 검증 / 실제 판매처 요청은 실행하지 않음 |
| 2026-08-05 | Codex를 사용해 Python ABC마트/29CM의 JSON/HTML 검증 집계를 Go Collector와 동일한 CollectorResult 구조로 정렬하고 전송 전 JSON Schema 검사를 추가 | 사용자가 실제 두 판매처 3개 수집과 PostgreSQL 저장을 확인했으며 Python 13개 테스트와 Spring Boot Testcontainers 전체 테스트로 계약 호환성을 검증 |
| 2026-08-06 | Codex를 사용해 Agent Gateway의 빈 실행 설정 fallback과 Codex OAuth 인증 만료 오류 비노출 처리를 구현 | 실제 폐기된 token 401 응답을 재현하고 Next.js unit test/lint/production build로 안전한 사용자 메시지와 stderr 비노출을 검증 |
| 2026-08-06 | Codex를 사용해 필수/선호 구매 조건 계약과 PostgreSQL 전문 검색/pg_trgm 후보 검색을 구현 | 공통 JSON Schema, Java/MCP/Web 계약 대조와 PostgreSQL Testcontainers 통합 테스트 및 Next.js/MCP build/test로 검증 / runtime AI model과 embedding은 추가하지 않음 |
| 2026-08-06 | Codex를 사용해 pgvector 0.8.2와 선택적 로컬 BGE-M3/Ollama embedding adapter 및 전문 검색 fallback을 구현 | provider port, 1024차원/schema validation/content hash와 pgvector cosine 검색을 unit/integration test로 검증 / model weight 미포함 및 품질 평가 전 기본 비활성화 |
| 2026-08-11 | Codex를 사용해 상품 상세/공개 근거/후보 비교 Backend API와 읽기 전용 MCP 도구를 구현 | MCP가 PostgreSQL이나 판매처에 직접 접근하지 않는 REST 경계, Spring Boot 통합 테스트와 MCP stdio 도구 계약 테스트로 검증 |
| 2026-08-11 | Codex를 사용해 데이터 최신성 기반 수집 요청과 선택 상품 가격/재고 재검증 Backend API 및 MCP 도구를 구현 | 기존 검색 Queue 계약만 사용하고 MCP의 RabbitMQ 직접 접근을 금지했으며 Spring Boot unit/integration test와 MCP stdio 계약 테스트로 검증 |
| 2026-08-11 | Codex를 사용해 범용 조건 정규화와 MCP 도구 9개의 설계/기능 목록/TODO/코드트래커 상태를 실제 코드에 맞게 동기화 | 자동 test와 실제 Queue 재검증 결과를 대조했으며 DRAFT Wiki의 PUBLISHED 전환과 최종 diff는 사람의 후속 검토 대상으로 유지 |
| 2026-08-11 | Codex CLI 구조화 출력용 엄격 Schema를 하위 호환 공용 PurchaseCondition 계약과 분리하고 안전한 실패 로그를 추가 | Codex CLI 0.146.1 실제 호출과 Web API에서 조건 생성 및 MCP DRAFT 저장 성공을 검증하고 원본 stderr 비노출을 유지 |
