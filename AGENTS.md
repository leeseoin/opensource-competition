# AGENTS.md

## 프로젝트 개요

- 프로젝트명: Purchase Research Agent(가칭)
- 목적: 자연어 구매 조건을 구체화하고 실제 판매처의 공개 상품·리뷰 정보를 근거 기반으로 비교·재검증한다.
- 현재 상태: ABC마트/29CM 검색 Collector와 Go RabbitMQ Worker 구현, Spring Boot Product Backend의 작업 발행/결과 소비/Flyway/JPA 저장/상품 조회/작업 상태 API 구현, MCP/Web은 planned
- 핵심 기술: Go, Java, Spring Boot, MCP, Next.js, React, PostgreSQL, RabbitMQ, Redis

## 구성요소 책임

- `services/collector`: Go. 외부 판매처 접근, 검색·상세·옵션·리뷰 parsing, rate limit, timeout, retry, 차단 감지
- `services/product-backend`: Java/Spring Boot. 상품 조회 API, RabbitMQ 작업 orchestration과 결과 소비, 데이터 검증/정규화, PostgreSQL 적재, 리뷰 신호 추출, 비교/재검증
- `services/mcp-server`: MCP. Codex/Claude Code가 사용할 도구를 제공하고 Product Backend REST API를 호출하는 얇은 연결 계층
- `frontend/purchase-web`: Next.js + React. `/chat` 사용자 챗봇과 `/admin/collections` 수집 관리 화면, Agent Gateway, 진행 상태, 비교, 근거, 검증 결과 표시
- `plugins/purchase-research-agent`: Codex plugin. PoC에서 구매 질문과 MCP tool 호출 workflow를 담당

## 핵심 경계

- 외부 판매처에는 Go Collector만 접근한다.
- PostgreSQL의 최종 쓰기는 Spring Boot Product Backend만 수행한다.
- MCP Server는 판매처, PostgreSQL, RabbitMQ에 직접 접근하지 않고 Product Backend REST API만 호출한다.
- RabbitMQ는 수집 작업·결과 전달에 사용하고 Redis를 두 번째 작업 Queue로 사용하지 않는다.
- Redis는 판매처별 속도 제한, 중복 방지, 짧은 진행 상태와 캐시에 사용한다.
- browser의 Next.js UI와 Codex Plugin은 크롤러나 DB를 직접 호출하지 않는다.
- PoC에서 최종 사용자 질문은 Next.js server의 Codex Gateway를 거쳐 Codex로 전달한다.
- Codex 실행 권한과 인증정보는 browser에 노출하지 않고 server에서만 관리한다.
- Go는 판매처별 `CollectorResult`를 반환하고 최종 추천을 판단하지 않는다.
- Product Backend는 Collector가 제공하지 않은 판매처 사실을 생성하지 않는다.
- Codex는 구조화된 근거를 설명하며 상품 사실을 추측하지 않는다.

## 작업 원칙

- 기존 사용자 변경을 되돌리지 않는다.
- 구현된 기능과 planned 기능을 문서에서 구분한다.
- 한국어 문서와 사용자 설명에서는 가운데점 문장 부호를 사용하지 않는다.
- 항목을 나란히 표현할 때는 `/`를 사용하고, 문장을 연결할 때는 `및`, `과`, `와`처럼 문맥에 맞는 표현을 사용한다.
- 로그인, CAPTCHA, robots 제한, 접근 통제를 우회하지 않는다.
- 공개적으로 접근 가능한 상품 정보만 수집한다.
- 판매처별 동시성·요청 빈도·timeout·재시도 상한을 둔다.
- 가격·재고·옵션에는 `sourceUrl`, `collectedAt`, `collectorVersion`을 포함한다.
- 추천 snapshot과 구매 전 verification snapshot을 분리한다.
- 리뷰 작성자 이름·프로필 등 식별정보는 저장하지 않는다.
- 리뷰 이미지는 내려받지 않고 사진 존재 여부와 공개 출처 참조만 저장한다.
- LLM 추출값은 `derived`와 confidence를 기록하고 공식 정보와 구분한다.
- API key, cookie, session, 개인 정보는 커밋하지 않는다.
- Java는 Java 21과 서비스 내부 Gradle Wrapper를 사용한다.

## Spring Boot 패키지 규칙

- `services/product-backend`는 기술 계층보다 업무 도메인을 먼저 나누는 package-by-feature 구조를 사용한다.
- 최상위 업무 package는 `product`, `collection`, `evidence`처럼 기능 이름으로 구분한다.
- 각 업무 package 안에서 필요한 경우 `controller`, `dto`, `entity`, `repository`, `service`, `exception`으로 역할을 나눈다.
- 여러 도메인에서 실제로 공유하는 코드만 `common`에 둔다. 한 곳에서만 사용하는 코드를 미리 `common`으로 옮기지 않는다.
- 팀 합의 없이 최상위 package를 `application`, `domain`, `infrastructure`, `interfaces` 계층으로 나누지 않는다.

## Git 브랜치 규칙

- 전체 작업 방식은 `docs/development/Git_브랜치_작업_방식.md`를 따른다.
- `sandbox/ls`는 서인의 개인 실험 브랜치이며 `develop`으로 직접 PR을 만들지 않는다.
- `sandbox/ls`에서 검증한 작업은 필요한 커밋만 `dev-ls`로 옮긴다.
- `dev-ls`와 `dev-jw`는 개인별 검토 가능 작업 브랜치이며 PR의 base는 `develop`으로 지정한다.
- `develop`은 협업 통합 및 테스트 브랜치이며 직접 기능 개발과 직접 push를 피한다.
- `main`은 안정 브랜치이며 검증된 `develop` PR만 반영한다.
- 공유 브랜치에서 강제 push가 필요한 rebase보다 `origin/develop` 일반 merge를 우선한다.
- 브랜치를 전환하기 전에 `git status --short`로 커밋되지 않은 사용자 변경을 확인한다.
- Codex가 코드, 문서, 설계 또는 검증에 실질적으로 참여한 커밋에는 커밋 본문 마지막에 `Co-authored-by: OpenAI Codex <codex@openai.com>` trailer를 반드시 포함한다.

## 코드 주석 규칙

- 새로 만들거나 수정하는 class, struct, interface, type, function, method에는 한국어 주석을 작성한다.
- Go의 exported symbol 주석은 해당 심볼 이름으로 시작하고 책임, 주요 입력·출력, 실패 조건을 설명한다.
- Python은 class/function/method에 한국어 docstring을 작성하고 책임, 인자, 반환값, 발생 가능한 예외를 설명한다.
- Java는 class/interface/method에 한국어 Javadoc을 작성하고 책임, 주요 인자, 반환값, 발생 가능한 예외를 설명한다.
- TypeScript/React는 component, hook, class, named function에 한국어 TSDoc 또는 바로 위 주석을 작성한다.
- 테스트 함수에도 검증 목적을 설명하는 한국어 주석을 작성한다.
- Go의 모든 `*_test.go`는 `services/collector/tests/unit` 또는 `services/collector/tests/integration` 아래에 둔다.
- `services/collector/internal`에는 실행에 사용되는 실제 코드만 두고, 테스트는 공개 type·function·HTTP route를 통해 검증한다.
- 한 줄짜리 익명 callback, 생성 코드, 외부 vendor 코드는 주석 의무에서 제외한다.
- 코드의 동작을 그대로 번역하는 주석보다 책임, 경계, 실패 계약처럼 코드만으로 알기 어려운 내용을 기록한다.

## 진행 관리와 완료 보고

- 아키텍처에서 추출한 상위 기능과 완료 기준은 `docs/planning/Purchase_Research_Agent_기능_목록.md`의 고정 기능 ID로 관리한다.
- 구현 계획은 `docs/planning/Purchase_Research_Agent_TODO.md`의 영역별 체크박스로 관리한다.
- 상위 계획의 세부 구현 상태는 `docs/development/Purchase_Research_Agent_개발_진행_관리.md`의 원자 작업 체크리스트로 관리한다.
- 새 기능을 구현하기 전에 기능 ID를 확인하고, 없다면 `feature-catalog` 스킬로 기능 목록에 먼저 등록한다.
- 기능 구현과 테스트를 commit한 뒤 `code-tracker` 스킬로 해당 기능 ID, commit, 변경 위치와 검증 결과를 기록한다.
- 코드트래커 작성 뒤 `feature-progress` 스킬로 실제 코드와 테스트를 대조해 기능 목록, TODO와 개발 진행 관리 상태를 갱신한다.
- 전체 순서와 각 문서의 책임은 `docs/development/기능_ID_기반_개발_추적_프로세스.md`를 따른다.
- 큰 기능 하나를 한 항목으로 묶지 않고 골격, 정상 경로, 실패 경로, 테스트, 문서, 검증을 독립 체크 항목으로 분리한다.
- 시작한 미완료 항목은 체크하지 않고 항목 끝에 `**(진행 중)**`을 표시한다.
- 코드, 관련 테스트, 문서 또는 계약 갱신이 모두 끝나고 검증 명령이 통과한 경우에만 `[x]`로 변경한다.
- 작업을 완료할 때 `docs/development/Purchase_Research_Agent_개발_진행_관리.md`에 구현 근거를 갱신한다.
- 구현 근거에는 작업명, 상태, 구현 파일, 시작 줄, class/function/method 또는 schema 이름, 검증 명령을 기록한다.
- 줄 번호만 기록하지 않고 `경로:줄 + 심볼 이름`을 함께 남긴다. 이후 코드 이동으로 줄 번호가 바뀌면 같은 작업에서 문서도 갱신한다.
- 개발 중 문제가 발생하면 증상, 원인, 해결 방법, 재발 방지 또는 남은 위험을 문제 기록에 남긴다.
- 에이전트의 최종 작업 보고에는 진행상황, 발생 문제와 해결, 변경 파일과 핵심 시작 줄, 실행한 검증과 결과를 포함한다.

## 규정과 공개 문서 동기화

- 대회 운영규정은 `THIRD_PARTY_NOTICES.md`와 `AI_USAGE.md`라는 파일명을 직접 요구하지 않는다. 이 저장소는 외부 구성요소와 AI 사용 내역을 공개하는 증거 문서로 두 파일을 사용한다.
- 라이브러리, 프레임워크, Gradle plugin, Node package, Go module, container image, 외부 코드, Plugin, MCP SDK 및 AI model을 추가/삭제하거나 version을 변경하면 같은 작업과 commit에서 `THIRD_PARTY_NOTICES.md`를 갱신한다.
- Codex, Claude Code 또는 다른 AI가 중요한 코드/설계/문서를 생성하거나 수정하면 같은 작업의 commit, PR 또는 개발 진행 관리 문서에 사용 범위와 사람의 검토 결과를 남기고 필요하면 `AI_USAGE.md`를 갱신한다.
- Plugin, MCP Server, Agent Gateway, model adapter, runtime AI model, provider 및 실행 방식을 추가/삭제/변경하면 같은 작업과 commit에서 `AI_USAGE.md`를 갱신한다.
- runtime AI model을 추가하면 model 이름/version/제공자/출처/weight 공개 여부/license/실행 위치/전송 데이터/사용 목적을 `THIRD_PARTY_NOTICES.md`, `AI_USAGE.md` 및 대회 규정 대응 체크리스트에 기록한다.
- manifest 또는 AI integration 관련 파일을 변경한 뒤 `make docs-check`를 실행한다. 검사가 통과해도 출처와 license의 정확성은 작성자가 공식 자료로 직접 확인한다.
- 규정 근거가 특정 파일 형식을 요구하는 직접 의무인지, 프로젝트가 증거를 남기기 위해 선택한 운영 방식인지 문서에서 구분한다.

## 실행과 검증

PostgreSQL, Redis, RabbitMQ는 루트 `compose.yaml`로 실행할 수 있다. 검색 작업의
Go consumer와 result publisher는 구현됐다. 기존 Python producer/result consumer와
DB 적재 코드는 Spring Boot 전환 과정에서 제거됐다. Spring Boot의 검색 작업 producer,
결과 consumer, DB 적재와 PostgreSQL 작업 상태 조회는 구현됐으며 Redis application
adapter, MCP와 Agent Gateway는 구현 전이다.
Spring Boot의 Flyway 초기 schema, CollectorResult 검증/JPA 적재 및 상품 조회 API는
구현됐다.

예정 검증 계층:

- Go unit/contract test: parser, rate limit, 저장된 HTML fixture
- Java unit/integration test: 정규화, DB 적재, review signal, REST API와 Queue 계약
- MCP contract test: 도구 입력/출력과 Product Backend API 연결
- E2E: 구매 질문 → 실제 수집 → 근거 비교 → 재검증
- 실제 판매처 smoke test는 기본 CI에서 제외하고 opt-in으로 실행

## 문서

- 시스템 구조: `docs/architecture/Purchase_Research_Agent_시스템_구조.md`
- 기능 ID 목록과 완료 기준: `docs/planning/Purchase_Research_Agent_기능_목록.md`
- 구현 계획: `docs/planning/Purchase_Research_Agent_TODO.md`
- 개발 진행·구현 근거·문제 기록: `docs/development/Purchase_Research_Agent_개발_진행_관리.md`
- 기능 ID 기반 개발 추적 절차: `docs/development/기능_ID_기반_개발_추적_프로세스.md`
- 구현 commit 기록 인덱스: `docs/reports/코드트래커/INDEX.md`
- Git 브랜치 작업 방식: `docs/development/Git_브랜치_작업_방식.md`
- 대회 제출 전 규정 확인: `docs/planning/오픈소스_개발자대회_규정_대응_체크리스트.md`
- 외부 구성요소 공개: `THIRD_PARTY_NOTICES.md`
- AI 사용 공개: `AI_USAGE.md`
- 날짜는 `YYYY-MM-DD` 형식을 사용한다.
