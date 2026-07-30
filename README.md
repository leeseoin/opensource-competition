# Purchase Research Agent

사용자의 자연어 구매 요청을 구체화하고 실제 판매처의 공개 상품과 리뷰 정보를 수집해 근거 기반으로 비교한 뒤, 선택 상품을 다시 검증하는 구매 조사 Agent PoC다.

현재 ABC마트와 29CM 검색 Collector 및 RabbitMQ 검색 Worker가 구현되어 있다. Spring Boot Product Backend는 기본 프로젝트만 생성된 상태이며 DB 적재, MCP Server, Next.js 화면 연결은 앞으로 구현한다.

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
        Go Collector Worker
              ↓
        ABC마트/29CM
```

각 구성요소의 책임은 다음과 같다.

| 구성요소 | 책임 | 현재 상태 |
|---|---|---|
| Next.js Web | 사용자 채팅과 관리자 수집 화면 | 기본 scaffold |
| Codex Plugin | 구매 질문 처리 순서와 MCP 도구 사용 방법 | 기본 구조 |
| MCP Server | AI 도구 요청을 Product Backend REST API로 연결 | 폴더와 설명 문서만 생성 |
| Product Backend | 상품 API, 수집 작업 생성, 결과 검증, PostgreSQL 저장 | Spring Boot 4.1.0 기본 프로젝트 |
| Go Collector | 판매처 검색, parsing, 접근 제한, RabbitMQ 작업 처리 | ABC마트/29CM 검색 구현 |
| Contracts | 서비스 사이의 JSON 요청과 응답 규격 | v1 초안 |

외부 판매처에는 Go Collector만 접근한다. PostgreSQL의 최종 쓰기는 Product Backend만 담당하며 MCP Server는 DB나 RabbitMQ에 직접 접근하지 않는다.

## 목표 흐름

```text
백그라운드 수집:
Product Backend → RabbitMQ → Go Collector Worker → RabbitMQ → Product Backend → PostgreSQL

사용자 질문:
Next.js → Codex/Claude Code → MCP Server → Product Backend → PostgreSQL 검색

DB 정보가 부족한 질문:
Product Backend → 제한된 추가 수집 요청 → 최신 결과 저장 → 근거가 있는 답변

구매 전 재검증:
MCP Server → Product Backend → 우선순위 재검증 작업 → Go Collector → 최신 snapshot 비교
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

개발 전에는 [시스템 구조](docs/architecture/Purchase_Research_Agent_시스템_구조.md)를 먼저 보고, 다음 작업은 [구현 TODO](docs/planning/Purchase_Research_Agent_TODO.md)에서 확인한다. 개인 실험과 협업 코드를 분리하는 방법은 [Git 브랜치 작업 방식](docs/development/Git_브랜치_작업_방식.md)을 따른다.

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

현재 Spring Boot용 Flyway migration은 아직 작성되지 않았다. 따라서 인프라 실행만으로 상품 테이블이 생성되지는 않는다.

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

```bash
make web-dev WEB_PORT=2500
```

Go Collector의 RabbitMQ Worker는 다음 명령으로 실행한다.

```bash
make collector-worker
```

현재 Product Backend의 RabbitMQ 작업 발행과 결과 저장 기능은 미구현이다. 따라서 이전 구조에서 가능했던 Queue 전체 흐름과 DB 적재는 Spring Boot로 다시 구현한 뒤 사용할 수 있다.

기본 검증은 다음 명령으로 실행한다.

```bash
make test
make check
```

서비스별 설명은 [Go Collector](services/collector/README.md), [Product Backend](services/product-backend/README.md), [MCP Server](services/mcp-server/README.md)에서 확인한다.

주의: PostgreSQL 공식 이미지는 DB를 처음 만드는 시점에만 DB 이름과 계정 정보를 적용한다. 이미 생성된 Docker Volume을 유지한 채 `.env`의 사용자, 비밀번호, DB 이름만 바꾸면 기존 DB에는 자동 반영되지 않는다.
