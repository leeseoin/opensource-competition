# AGENTS.md

## 프로젝트 개요

- 프로젝트명: Purchase Research Agent(가칭)
- 목적: 자연어 구매 조건을 구체화하고 실제 판매처의 공개 상품·리뷰 정보를 근거 기반으로 비교·재검증한다.
- 현재 상태: ABC마트·29CM 검색 Collector와 Python PostgreSQL 적재 구현, Redis·RabbitMQ 로컬 인프라 구성 완료, 작업 Queue·MCP·Web 연결은 planned
- 핵심 기술: Go, Python, MCP, FastAPI, Next.js, React, PostgreSQL, RabbitMQ, Redis

## 구성요소 책임

- `services/collector`: Go. 외부 판매처 접근, 검색·상세·옵션·리뷰 parsing, rate limit, timeout, retry, 차단 감지
- `services/research-backend`: Python. MCP, FastAPI, RabbitMQ 작업 orchestration과 결과 소비, 데이터 검증·정규화, PostgreSQL 적재, 리뷰 신호 추출, 비교·재검증
- `apps/purchase-web`: Next.js + React. `/chat` 사용자 챗봇과 `/admin/collections` 수집 관리 화면, Agent Gateway, 진행 상태, 비교, 근거, 검증 결과 표시
- `plugins/purchase-research-agent`: Codex plugin. PoC에서 구매 질문과 MCP tool 호출 workflow를 담당

## 핵심 경계

- 외부 판매처에는 Go Collector만 접근한다.
- PostgreSQL의 최종 쓰기는 Python Backend만 수행한다.
- RabbitMQ는 수집 작업·결과 전달에 사용하고 Redis를 두 번째 작업 Queue로 사용하지 않는다.
- Redis는 판매처별 속도 제한, 중복 방지, 짧은 진행 상태와 캐시에 사용한다.
- browser의 Next.js UI와 Codex Plugin은 크롤러나 DB를 직접 호출하지 않는다.
- PoC에서 최종 사용자 질문은 Next.js server의 Codex Gateway를 거쳐 Codex로 전달한다.
- Codex 실행 권한과 인증정보는 browser에 노출하지 않고 server에서만 관리한다.
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

## 코드 주석 규칙

- 새로 만들거나 수정하는 class, struct, interface, type, function, method에는 한국어 주석을 작성한다.
- Go의 exported symbol 주석은 해당 심볼 이름으로 시작하고 책임, 주요 입력·출력, 실패 조건을 설명한다.
- Python은 class/function/method에 한국어 docstring을 작성하고 책임, 인자, 반환값, 발생 가능한 예외를 설명한다.
- TypeScript/React는 component, hook, class, named function에 한국어 TSDoc 또는 바로 위 주석을 작성한다.
- 테스트 함수에도 검증 목적을 설명하는 한국어 주석을 작성한다.
- Go의 모든 `*_test.go`는 `services/collector/tests/unit` 또는 `services/collector/tests/integration` 아래에 둔다.
- `services/collector/internal`에는 실행에 사용되는 실제 코드만 두고, 테스트는 공개 type·function·HTTP route를 통해 검증한다.
- 한 줄짜리 익명 callback, 생성 코드, 외부 vendor 코드는 주석 의무에서 제외한다.
- 코드의 동작을 그대로 번역하는 주석보다 책임, 경계, 실패 계약처럼 코드만으로 알기 어려운 내용을 기록한다.

## 진행 관리와 완료 보고

- 구현 계획은 `docs/planning/Purchase_Research_Agent_TODO.md`의 영역별 체크박스로 관리한다.
- 상위 계획의 세부 구현 상태는 `docs/development/Purchase_Research_Agent_개발_진행_관리.md`의 원자 작업 체크리스트로 관리한다.
- 큰 기능 하나를 한 항목으로 묶지 않고 골격, 정상 경로, 실패 경로, 테스트, 문서, 검증을 독립 체크 항목으로 분리한다.
- 시작한 미완료 항목은 체크하지 않고 항목 끝에 `**(진행 중)**`을 표시한다.
- 코드, 관련 테스트, 문서 또는 계약 갱신이 모두 끝나고 검증 명령이 통과한 경우에만 `[x]`로 변경한다.
- 작업을 완료할 때 `docs/development/Purchase_Research_Agent_개발_진행_관리.md`에 구현 근거를 갱신한다.
- 구현 근거에는 작업명, 상태, 구현 파일, 시작 줄, class/function/method 또는 schema 이름, 검증 명령을 기록한다.
- 줄 번호만 기록하지 않고 `경로:줄 + 심볼 이름`을 함께 남긴다. 이후 코드 이동으로 줄 번호가 바뀌면 같은 작업에서 문서도 갱신한다.
- 개발 중 문제가 발생하면 증상, 원인, 해결 방법, 재발 방지 또는 남은 위험을 문제 기록에 남긴다.
- 에이전트의 최종 작업 보고에는 진행상황, 발생 문제와 해결, 변경 파일과 핵심 시작 줄, 실행한 검증과 결과를 포함한다.

## 실행과 검증

PostgreSQL, Redis, RabbitMQ는 루트 `compose.yaml`로 실행할 수 있다. 실제
RabbitMQ producer/consumer, Redis application adapter, MCP와 Agent Gateway는 구현
전이다.

예정 검증 계층:

- Go unit/contract test: parser, rate limit, 저장된 HTML fixture
- Python unit/integration test: 정규화, DB 적재, review signal, MCP/API 계약
- E2E: 구매 질문 → 실제 수집 → 근거 비교 → 재검증
- 실제 판매처 smoke test는 기본 CI에서 제외하고 opt-in으로 실행

## 문서

- 시스템 구조: `docs/architecture/Purchase_Research_Agent_시스템_구조.md`
- 구현 계획: `docs/planning/Purchase_Research_Agent_TODO.md`
- 개발 진행·구현 근거·문제 기록: `docs/development/Purchase_Research_Agent_개발_진행_관리.md`
- 대회 제출 전 규정 확인: `docs/planning/오픈소스_개발자대회_규정_대응_체크리스트.md`
- 날짜는 `YYYY-MM-DD` 형식을 사용한다.
