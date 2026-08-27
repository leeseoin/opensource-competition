# Purchase Research Agent

## 준비물
필수: Claude Code Max Plan 계정 / Codex Max Plan 

## 처음 실행

처음 저장소를 받은 사용자는 아래 순서대로 환경 파일, AI CLI, 인프라와 애플리케이션
의존성을 준비한다. Docker Desktop, Java 21, Node.js/npm, Python 3.12와 uv가 먼저 설치돼
있어야 한다.

### 1. 환경 파일과 AI CLI 준비

루트 `.env`가 없을 때 `.env.example`을 복사하고 Codex CLI 또는 Claude Code CLI의 설치와
로그인 상태를 확인한다. 두 CLI를 모두 설치할 필요는 없지만 하나 이상 `READY`여야 한다.

```bash
make env
make ai-runtime-check
```

Codex는 `codex`를 실행하고 `Sign in with ChatGPT`를 선택한다. Claude Code는 `claude`를
실행한 뒤 `/login`으로 인증한다. 인증정보는 browser나 저장소에 저장하지 않고 Web server를
실행한 로컬 계정의 CLI 인증을 사용한다.

### 2. PostgreSQL/Redis/RabbitMQ 실행

```bash
make infra-up
make infra-status
```

`make infra-status`에서 PostgreSQL, Redis와 RabbitMQ가 실행 중인지 확인한다. 기존 Docker
volume은 보존되며 `.env`의 DB 계정 값을 변경해도 이미 생성된 DB에는 자동 적용되지 않는다.

### 3. Python Collector와 Web 의존성 설치

Python Collector의 uv 환경과 Chromium을 준비한 뒤 MCP Server와 Next.js package를 설치한다.

```bash
make python-crawler-setup
make web-install
```

### 4. Backend/Python API/Worker 실행

첫 번째 터미널에서 다음 명령을 실행한다.

```bash
make python-crawler-swagger
```

이 명령은 인프라 상태를 확인하고 Spring Boot Product Backend, Python Collector API와
RabbitMQ Worker를 함께 실행한다. 최초 Gradle/uv 실행은 의존성 준비 때문에 시간이 걸릴 수 있다.

확인 주소:

- Product Backend health: `http://localhost:8080/actuator/health`
- Product Backend Swagger: `http://localhost:8080/swagger-ui.html`
- Python Collector Swagger: `http://localhost:8012/docs`

### 5. MCP Server와 Web 실행

두 번째 터미널에서 실행한다.

```bash
make web-dev WEB_PORT=2500
```

확인 주소:

- 랜딩 화면: `http://localhost:2500`
- 구매 질문: `http://localhost:2500/chat`
- 수집 관리: `http://localhost:2500/admin/collections`

로그인 후에도 Web에서 AI 인증 오류가 나오면 Web server를 종료하고 `make ai-runtime-check`가
`READY`로 표시되는 동일한 일반 터미널에서 `make web-dev WEB_PORT=2500`을 다시 실행한다.

## 프로젝트 소개

사용자의 자연어 구매 요청을 구체화하고 실제 판매처의 공개 상품과 리뷰 정보를 수집해 근거
기반으로 비교한 뒤, 선택 상품의 최신 가격과 재고를 다시 검증하는 구매 조사 Agent PoC다.

현재 Python ABC마트/29CM Collector와 RabbitMQ Worker, Spring Boot Product Backend,
MCP Server, Codex/Claude Code Agent Gateway, 구매 질문과 수집 관리 Web 화면이 연결돼 있다.
PostgreSQL DB 우선 검색, 필요한 경우 수집 작업 요청, 상품 후보 비교와 선택 상품 재검증 경로를
제공한다. 실제 추천 품질은 수집 범위와 상품 근거에 영향을 받으므로 평가 data와 수동 검토를
통해 계속 개선한다.

## 한눈에 보는 구조

```text
사용자
  ↓
Next.js 화면
  ↓
Codex/Claude Code와 Purchase Research Plugin
  ↓ MCP
MCP Server
  ↓ REST API
Spring Boot Product Backend
  ├── PostgreSQL: 상품과 수집 근거 저장
  ├── RabbitMQ: 수집 작업과 결과 전달
  └── Redis: 속도 제한, 중복 방지, 짧은 진행 상태
              ↓
        Python Collector Worker
        (Go 비교 runtime 유지)
              ↓
        ABC마트/29CM
```

각 구성요소의 책임은 다음과 같다.

| 구성요소 | 책임 | 현재 상태 |
|---|---|---|
| Next.js Web | 사용자 채팅과 관리자 수집 화면 | Landing/Chat/Compare, 조건 확인, Agent Run 진행과 DB 후보 표시 부분 구현 |
| Codex Plugin | 구매 질문 처리 순서와 MCP 도구 사용 방법 | manifest/MCP 설정/구매 조사 skill 기본 구조 |
| MCP Server | AI 도구 요청을 Product Backend REST API로 연결 | 조사 세션/검색/상세/근거/비교/수집/재검증/Agent Run 도구 구현 |
| Product Backend | 상품 API, 작업 orchestration, 결과 검증과 PostgreSQL 저장 | 작업 상태 DB/결과 저장/FTS와 trigram 검색/비교와 재검증 API 구현 |
| Python Collector | 판매처 검색, parsing, 접근 제한과 RabbitMQ 작업 처리 | ABC마트/29CM 검색/상세와 Queue ACK/retry/DLQ 구현 |
| Go Collector | Python 전환 검증을 위한 계약/운영 비교 기준 | ABC마트/29CM/무신사 Adapter와 Queue Worker 유지 |
| Contracts | 서비스 사이의 JSON 요청과 응답 규격 | v1 초안 |

외부 판매처에는 Collector Worker만 접근한다. 현재 기본 전환 runtime은 Python이며 Go 구현은
비교와 복구 기준으로 유지한다. PostgreSQL의 최종 쓰기는 Product Backend만 담당하며 MCP
Server는 DB나 RabbitMQ에 직접 접근하지 않는다.

## 목표 흐름

```text
백그라운드 수집:
Product Backend → RabbitMQ → Python Collector Worker → RabbitMQ → Product Backend → PostgreSQL

사용자 질문:
Next.js → Codex/Claude Code → MCP Server → Product Backend → PostgreSQL 검색

DB 정보가 부족한 질문:
Product Backend → 제한된 추가 수집 요청 → 최신 결과 저장 → 근거가 있는 답변

구매 전 재검증:
MCP Server → Product Backend → 우선순위 재검증 작업 → Python Collector → 최신 snapshot 비교
```

## Repository

```text
frontend/
└── purchase-web/                   # Next.js 사용자 채팅과 관리자 화면
services/
├── collector/                      # Go 판매처 Collector와 RabbitMQ Worker
├── product-backend/                # Spring Boot 상품 및 수집 관리 서버
└── mcp-server/                     # Codex/Claude Code용 MCP 연결 서버
plugins/
└── purchase-research-agent/        # Codex 구매 조사 workflow
contracts/                          # 서비스 사이의 JSON 계약
docs/
├── architecture/                   # 최신 시스템 구조와 데이터 수집 설계
├── planning/                       # 구현 TODO와 제출 전 체크리스트
├── development/                    # 구현 위치, 검증 결과, 문제 해결 기록
└── reports/                        # 날짜별 협업과 업무 기록
```

개발 전에는 [시스템 구조](docs/architecture/Purchase_Research_Agent_시스템_구조.md)를 먼저 보고, [기능 목록](docs/planning/Purchase_Research_Agent_기능_목록.md)에서 기능 ID와 완료 기준을 확인한 뒤 [구현 TODO](docs/planning/Purchase_Research_Agent_TODO.md)에서 세부 작업을 확인한다. 기능 ID를 코드와 진행상황에 연결하는 방법은 [개발 추적 프로세스](docs/development/기능_ID_기반_개발_추적_프로세스.md), 개인 실험과 협업 코드를 분리하는 방법은 [Git 브랜치 작업 방식](docs/development/Git_브랜치_작업_방식.md)을 따른다.

외부 라이브러리와 container image의 출처 및 license는 [Third-Party Notices](THIRD_PARTY_NOTICES.md), Codex/Claude Code 사용 범위와 사람의 검토 방법은 [AI Usage](AI_USAGE.md)에 공개한다. 대회 운영규정이 이 파일명을 직접 요구한 것은 아니며, 저장소에서 관련 근거를 지속적으로 공개하기 위해 프로젝트가 선택한 관리 방식이다.

## 로컬 인프라 실행

루트 환경변수 예제를 복사하고 PostgreSQL, Redis, RabbitMQ를 실행한다.

```bash
cp .env.example .env
docker compose up -d postgres redis rabbitmq
docker compose ps
```

기본 로컬 접속 정보는 다음과 같다.

| 서비스 | 호스트 주소 | 용도 |
|---|---|---|
| PostgreSQL | `localhost:35432` | 상품, snapshot, 근거 영구 저장 |
| Redis | `localhost:36379` | 속도 제한, 중복 방지, 진행 상태 |
| RabbitMQ AMQP | `localhost:35672` | 수집 작업과 결과 전달 |
| RabbitMQ 관리 화면 | `http://localhost:35673` | Queue와 처리 상태 확인 |

`compose.yaml`은 루트 `.env` 값을 읽고, 값이 없으면 `.env.example`과 같은 로컬 기본값을 사용한다. 운영 환경에서는 계정과 비밀번호를 반드시 변경한다.

Product Backend를 실행하면 Flyway가 상품, 판매처 상품, 가격/재고 snapshot,
옵션 및 근거 테이블을 자동 생성한다. Collector JSON 수동 적재와 상품 조회뿐 아니라
RabbitMQ 검색 작업 발행 및 결과 자동 저장 경로도 구현돼 있다. 작업별 진행 상태를
PostgreSQL에 저장하고 Dashboard와 Agent Run에서 조회하는 경로도 구현돼 있다.

## 루트 개발 명령

저장소 루트에서 `make help`로 전체 명령을 확인할 수 있다.

```bash
make env
make infra-up
make collector-run
make product-backend-run
make web-dev
```

각 서버는 계속 실행되므로 별도 터미널에서 실행한다. Next.js 포트를 바꾸려면 다음처럼 지정한다.

Product Backend의 내부 API는 실행 후 Swagger UI에서 직접 확인할 수 있다.

```text
http://localhost:8080/swagger-ui.html
```

```bash
make web-dev WEB_PORT=2500
```

Go Collector의 RabbitMQ Worker는 다음 명령으로 실행한다.

```bash
make collector-worker
```

Product Backend의 RabbitMQ 작업 발행과 결과 저장 코드는 구현됐다. 실제 판매처 전체
흐름은 Product Backend와 Go Worker를 함께 실행한 opt-in 수동 검증이 남아 있다.

기본 검증은 다음 명령으로 실행한다.

```bash
make test
make check
```

서비스별 설명은 [Go Collector](services/collector/README.md), [Product Backend](services/product-backend/README.md), [MCP Server](services/mcp-server/README.md)에서 확인한다.

주의: PostgreSQL 공식 이미지는 DB를 처음 만드는 시점에만 DB 이름과 계정 정보를 적용한다. 이미 생성된 Docker Volume을 유지한 채 `.env`의 사용자, 비밀번호, DB 이름만 바꾸면 기존 DB에는 자동 반영되지 않는다.

## 라이선스

이 프로젝트의 직접 작성 코드는 [MIT License](LICENSE)로 배포한다. 외부 구성요소와 자료의
라이선스 및 권리 고지는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)에서 확인한다.
