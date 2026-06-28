# AgentPay Guard

AgentPay Guard는 AI Agent가 유료 API, 구독형 서비스, 크레딧 기반 서비스, 사용량 기반 외부 리소스를 사용하기 전에 사용자 intent, 예산, 허용 서비스, 위험 요소를 검증하고 감사 가능한 기록을 남기는 보안 게이트웨이 PoC이다.

현재 단계는 PoC 초기 구현이다. 실제 결제, 카드, 계좌, PG, 메인넷 자산 이동은 구현하지 않는다.

## 현재 상태

구현됨:

- Spring Boot API server skeleton
- Docker Compose 기반 PostgreSQL 개발 DB
- PostgreSQL 개발 role init SQL
- Flyway 초기 schema migration
- React dashboard / Python sample agent / Hardhat audit anchor 디렉토리 placeholder
- 프로젝트 문서와 DB 협업 정책

planned:

- Intent / Agent / Payment Request API
- 규칙 기반 Policy Engine
- Mock Merchant API
- Payment Simulator
- Approval Flow
- Audit eventHash 생성
- AuditAnchor 컨트랙트 연동
- React + TypeScript Dashboard
- Python Sample Agent

## 프로젝트 구조

```text
opensource-competition/
  agentpay-guard-api-server/       # Spring Boot backend
  agentpay-guard-dashboard/        # React + TypeScript dashboard, planned
  agentpay-guard-sample-agent/     # Python sample agent, planned
  agentpay-guard-audit-anchor/     # Hardhat + Solidity contract, planned
  docker/
    postgres/
      init/                        # PostgreSQL 최초 초기화 SQL
  docs/                            # 기획, 아키텍처, 작업 계획
  docker-compose.yml
  AGENTS.md
```

## 빠른 시작

### 1. PostgreSQL 실행

루트 디렉토리에서 실행한다.

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose up -d postgres
docker compose ps
```

PostgreSQL container:

```text
agentpay-guard-postgres
```

개발 DB:

```text
database: agentpay_guard
```

개발 계정:

```text
agentpay_guard_seoin / agentpay_guard
agentpay_guard_jeongwoo / agentpay_guard
```

role 확인:

```bash
docker exec -it agentpay-guard-postgres \
  psql -U postgres -d agentpay_guard -c "\du"
```

Spring Boot 접속 계정 확인:

```bash
PGPASSWORD=agentpay_guard psql \
  -h 127.0.0.1 \
  -p 5432 \
  -U agentpay_guard_seoin \
  -d agentpay_guard \
  -c "select current_user, current_database();"
```

### 2. API server 실행

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-api-server
./gradlew bootRun
```

앱 기본 주소:

```text
http://localhost:8080
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## DB 협업 원칙

DB volume 자체는 공유하지 않는다. 각 개발자는 로컬 Docker PostgreSQL을 실행한다.

공유 대상:

- `docker-compose.yml`
- `docker/postgres/init/*.sql`
- `agentpay-guard-api-server/src/main/resources/db/migration/*.sql`
- seed SQL

공유하지 않는 대상:

- Docker volume 데이터
- 개인 로컬 DB 상태
- 실제 secret
- DB dump 파일

schema 변경은 직접 DB에서만 처리하지 않고 Flyway migration으로 남긴다.

```text
agentpay-guard-api-server/src/main/resources/db/migration/
```

현재 초기 migration:

```text
V1__init_schema.sql
```

DB가 꼬였고 개발 데이터를 지워도 되는 경우:

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose down -v
docker compose up -d postgres
cd agentpay-guard-api-server
./gradlew bootRun
```

자세한 정책:

- [DB 협업 정책](docs/policies/AgentPay_Guard_DB_협업_정책.md)
- [PostgreSQL init scripts](docker/postgres/init/README.md)

## 자주 쓰는 명령

API server 명령:

- [agentpay-guard-api-server/command.md](agentpay-guard-api-server/command.md)

의존성 확인:

```bash
cd agentpay-guard-api-server
./gradlew dependencyInsight --dependency springdoc --configuration runtimeClasspath
```

컴파일:

```bash
./gradlew compileJava
```

테스트:

```bash
./gradlew test
```

## 주요 문서

- [AGENTS.md](AGENTS.md)
- [문서 인덱스](docs/README.md)
- [구성요소 쉬운 설명](docs/overview/AgentPay_Guard_구성요소_쉬운설명.md)
- [시스템 아키텍처](docs/architecture/AgentPay_Guard_시스템_아키텍처.md)
- [초기 프로젝트 골격](docs/architecture/AgentPay_Guard_초기_프로젝트_골격.md)
- [디렉토리별 개발 계획](docs/planning/AgentPay_Guard_디렉토리별_개발계획.md)
- [디렉토리별 ToDo](docs/planning/AgentPay_Guard_디렉토리별_TODO.md)
- [작업 목록](docs/planning/AgentPay_Guard_작업목록.md)
- [PoC 범위](docs/overview/AgentPay_Guard_PoC_범위.md)
- [DB 협업 정책](docs/policies/AgentPay_Guard_DB_협업_정책.md)

## 개발 주의사항

- 실제 결제 기능은 구현하지 않는다.
- API key, private key, RPC secret, 지갑 mnemonic 등 민감 정보는 커밋하지 않는다.
- 블록체인에는 원문 데이터나 개인정보를 올리지 않는다. eventHash만 기록한다.
- PostgreSQL 개발 비밀번호는 PoC 로컬 개발용이다. 운영/배포용 secret으로 사용하지 않는다.
- 이미 공유된 Flyway migration은 수정하지 않는다. 변경이 필요하면 새 migration을 추가한다.
