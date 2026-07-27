# Purchase Research Agent

사용자의 자연어 구매 요청을 구체화하고, 실제 판매처의 공개 상품·리뷰 정보를 수집해 근거 기반으로 비교한 뒤 선택 상품을 다시 검증하는 구매 조사 Agent PoC이다.

현재 상태는 **ABC마트·29CM 상품 검색, RabbitMQ 검색 작업 Worker와 PostgreSQL 적재까지 구현 완료, Redis application adapter·MCP·Web 기능 개발 예정**이다.

## 핵심 구성

```text
Next.js Web           최종 사용자가 보는 구매 조사 챗봇 화면
Codex Plugin          구매 질문 처리 순서와 MCP 도구 사용 방법
Go Collector          판매처 검색·상세·옵션·리뷰 수집과 접근 통제
Python Backend        MCP·FastAPI·정규화·DB 적재·리뷰 분석·상품 비교
Contracts             Go와 Python이 주고받는 JSON 데이터 규격
PostgreSQL            상품·offer·review signal·snapshot·evidence 저장
RabbitMQ              검색 페이지·상품 상세·리뷰·재검증 수집 작업 전달
Redis                 판매처별 속도 제한·중복 방지·진행 상태·짧은 캐시
```

## 목표 흐름

```text
백그라운드 수집:
수집 작업 생성 → RabbitMQ → Go Collector Worker → Python 검증·PostgreSQL 저장

사용자 질문:
Next.js → Codex/Claude Code → MCP → PostgreSQL 검색 → 근거가 연결된 상품 비교

구매 전 재검증:
MCP → 우선순위 재검증 작업 → RabbitMQ → Go Collector → 최신 snapshot 비교
```

## Repository

```text
apps/                             # Next.js 사용자 채팅·관리자 수집 화면 scaffold
plugins/                          # Codex가 MCP 기능을 사용하는 방법
contracts/                        # Go와 Python 사이의 JSON 요청·응답 규격
services/                         # 실제 기능을 실행하는 Go·Python 서버
docs/                             # 시스템 구조, 구현 계획, 개발 기록
├── architecture/                 # 최신 시스템 구조와 데이터 수집 설계
├── planning/                     # 구현 TODO와 제출 전 체크리스트
├── development/                  # 구현 위치, 검증 결과, 문제 해결 기록
└── reports/                      # 날짜별 협업·업무 기록
```

개발 전에는 [시스템 구조](docs/architecture/Purchase_Research_Agent_시스템_구조.md)를 먼저 보고, 다음 작업은 [구현 TODO](docs/planning/Purchase_Research_Agent_TODO.md)에서 확인한다. 대회 라이선스와 제출 조건은 당장 개발을 막지 않고 [대회 규정 대응 체크리스트](docs/planning/오픈소스_개발자대회_규정_대응_체크리스트.md)에서 별도로 관리한다.

## 로컬 인프라 실행

Docker Compose로 PostgreSQL, Redis, RabbitMQ를 실행하기 전에 루트 환경변수
예제를 복사한다.

```bash
cp .env.example .env
```

`compose.yaml`은 루트 `.env`의 PostgreSQL, Redis, RabbitMQ 설정을 자동으로
읽는다. `.env`가 없으면 `compose.yaml`에 적힌 로컬 개발 기본값을 사용한다.

설정값이 적용된 최종 Compose 구성을 확인할 수 있다. 이 출력에는 비밀번호가
포함될 수 있으므로 외부에 공유하지 않는다.

```bash
docker compose config
```

이후 로컬 인프라를 실행하고 Alembic migration을 적용한다.

```bash
docker compose up -d postgres redis rabbitmq
docker compose run --rm migrate
```

기본 로컬 접속 정보는 다음과 같다.

| 서비스 | 호스트 주소 | 용도 |
|---|---|---|
| PostgreSQL | `localhost:35432` | 상품·snapshot·근거 영구 저장 |
| Redis | `localhost:36379` | 속도 제한·중복 방지·진행 상태 |
| RabbitMQ AMQP | `localhost:35672` | 수집 작업과 결과 전달 |
| RabbitMQ 관리 화면 | `http://localhost:35673` | Queue·연결·처리 상태 확인 |

컨테이너 사이에서는 각각 `postgres:5432`, `redis:6379`, `rabbitmq:5672`를
사용한다. 기본 로컬 계정과 비밀번호는 `.env.example`에 있으며 운영 환경에서는
반드시 변경한다.

실행 상태는 다음 명령으로 확인한다.

```bash
docker compose ps
```

## 루트 개발 명령

자주 사용하는 명령은 루트 `Makefile`에 모아 두었다. 저장소 루트에서 다음 명령으로
전체 목록을 볼 수 있다.

```bash
make help
```

처음 실행할 때는 환경변수 파일과 로컬 인프라를 준비하고 migration을 적용한다.

```bash
make env
make infra-up
make migrate
```

Go Collector와 Next.js 개발 서버는 각각 별도 터미널에서 실행한다.

```bash
make collector-run
make web-dev
```

Next.js의 기본 포트는 `3000`이며 실행할 때 다른 포트를 지정할 수 있다.

```bash
make web-dev WEB_PORT=2500
```

Collector 서버가 실행 중일 때 실제 검색 결과를 PostgreSQL에 저장할 수 있다.

```bash
make collect MERCHANT=abcmart QUERY=구두 LIMIT=3
make collect MERCHANT=29cm QUERY=가방 LIMIT=5
```

RabbitMQ 백그라운드 수집은 터미널 세 개에서 실행한다. 이 방식은 Go HTTP 서버를
별도로 켜지 않아도 Collector Worker가 기존 판매처 Adapter를 직접 사용한다.

```text
터미널 1: make result-worker
터미널 2: make collector-worker
터미널 3: make enqueue MERCHANT=abcmart QUERY=구두 LIMIT=3
```

한 건만 직접 확인하려면 `make result-worker-once`,
`make collector-worker-once`를 사용할 수 있다. 현재 Queue 검색은 1페이지만
지원하며 일시 오류는 5초 뒤 한 번 재시도한다.

커밋 전 기본 검증은 `make test`, Compose 설정과 production build를 포함한 전체
검증은 `make check`를 사용한다.

```bash
make test
make check
```

자세한 Python 실행 방법은
[`services/research-backend/README.md`](services/research-backend/README.md)를
참고한다.

주의: PostgreSQL 공식 이미지는 DB를 처음 만드는 시점에만 DB 이름과 계정 정보를
적용한다. 이미 생성된 Docker Volume을 유지한 채 `.env`의 사용자·비밀번호·DB 이름만
바꾸면 기존 DB에는 자동 반영되지 않는다.
