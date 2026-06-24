# AgentPay Guard DB 협업 정책

작성일: 2026-06-24  
상태: active

## 목적

이 문서는 AgentPay Guard를 여러 명이 개발할 때 PostgreSQL DB 상태를 어떻게 맞출지 정한다.

핵심 원칙은 다음과 같다.

```text
DB volume 자체는 공유하지 않는다.
DB schema와 seed 데이터를 코드로 공유한다.
각 개발자는 로컬 Docker PostgreSQL을 실행한다.
Flyway migration으로 같은 DB 상태를 재현한다.
```

## 용어

### Docker image

PostgreSQL 실행 환경이다.

현재는 공식 이미지를 사용한다.

```text
postgres:16
```

Docker Hub에서 pull하는 대상은 기본적으로 image이다. 이 image에는 로컬에서 작업한 DB 데이터가 들어 있지 않다.

### Docker volume

실제 PostgreSQL 데이터 파일이 저장되는 로컬 저장소이다.

현재 compose 설정:

```yaml
agentpay_guard_postgres_data:/var/lib/postgresql/data
```

volume에는 다음이 저장된다.

- database
- role/user
- table
- index
- Flyway migration 적용 이력
- 개발 중 입력한 데이터

volume은 개발자 개인 로컬 상태로 본다. Git이나 Docker Hub로 공유하지 않는다.

### Flyway migration

DB schema 변경을 코드로 관리하는 방식이다.

planned 위치:

```text
agentpay-guard-api-server/src/main/resources/db/migration/
```

예시:

```text
V1__init_schema.sql
V2__add_payment_requests.sql
V3__add_audit_events.sql
```

Spring Boot가 실행될 때 Flyway가 migration 파일을 DB에 적용하고, 적용 이력을 `flyway_schema_history` 테이블에 저장한다.

## 현재 DB 구성

PostgreSQL은 monorepo 루트의 `docker-compose.yml`로 실행한다.

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose up -d postgres
```

개발 DB:

```text
database: agentpay_guard
```

관리자 계정:

```text
username: postgres
password: postgres
```

개발자 계정:

```text
username: agentpay_guard_seoin
password: agentpay_guard

username: agentpay_guard_jeongwoo
password: agentpay_guard
```

Spring Boot 기본 접속 계정:

```text
username: agentpay_guard_seoin
password: agentpay_guard
```

## init SQL 정책

PostgreSQL 최초 초기화 SQL은 다음 디렉토리에 둔다.

```text
docker/postgres/init/
```

현재 파일:

```text
docker/postgres/init/01-create-agentpay-users.sql
```

이 파일은 PostgreSQL volume이 처음 생성될 때만 실행된다.

```text
새 volume으로 처음 실행 -> init SQL 실행
이미 초기화된 volume이 있음 -> init SQL 실행 안 됨
```

init SQL의 책임:

- 개발 DB role 생성
- 개발 DB 접속 권한 부여
- schema 사용 권한 부여

init SQL에 넣지 않을 것:

- 일반 application table 생성
- 기능별 schema 변경
- 대량 seed 데이터
- 개인 테스트 데이터

application table 생성과 schema 변경은 Flyway migration으로 관리한다.

## schema 변경 정책

DB schema를 바꿀 때는 반드시 Flyway migration 파일을 작성한다.

금지:

```text
psql 또는 DB GUI에서 ALTER TABLE만 실행하고 끝내기
로컬 DB에서만 table을 만들고 코드에 남기지 않기
기존 migration 파일을 이미 공유된 뒤 수정하기
```

허용:

```text
새 migration 파일 추가
새 migration 파일을 PR/commit에 포함
migration 적용 후 앱 실행 확인
```

예시:

```text
agentpay-guard-api-server/src/main/resources/db/migration/V1__init_schema.sql
```

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
```

컬럼 추가 예시:

```text
agentpay-guard-api-server/src/main/resources/db/migration/V2__add_reason_to_payment_requests.sql
```

```sql
ALTER TABLE payment_requests
ADD COLUMN reason TEXT;
```

## migration 작성 규칙

- 파일명은 `V번호__설명.sql` 형식을 사용한다.
- 번호는 증가시킨다.
- 이미 main 또는 공유 브랜치에 올라간 migration은 수정하지 않는다.
- 수정이 필요하면 새 migration을 추가한다.
- DDL은 가능하면 명시적으로 작성한다.
- timestamp는 `TIMESTAMPTZ`를 우선 사용한다.
- 금액은 floating point가 아니라 `NUMERIC(18, 6)` 또는 문자열 정책 중 하나로 통일한다.
- 상태값은 초기에는 `VARCHAR`로 두고 enum은 application enum으로 관리한다.

## seed 데이터 정책

데모와 테스트에 필요한 기본 데이터는 seed로 관리한다.

초기 PoC에서는 다음 중 하나를 선택한다.

1. Flyway migration에 demo seed 포함
2. dev profile에서만 seed 실행
3. 별도 seed SQL을 수동 실행

초기 추천:

```text
V100__seed_demo_data.sql
```

seed에 넣을 수 있는 것:

- demo user
- demo agent
- demo payment intent
- demo merchant/resource
- demo policy scenario

seed에 넣지 않을 것:

- 실제 개인정보
- 실제 API key
- 실제 결제 정보
- 개인 로컬 테스트 쓰레기 데이터

## 개발자 작업 흐름

### 처음 세팅

```bash
git pull
docker compose up -d postgres
cd agentpay-guard-api-server
./gradlew bootRun
```

Spring Boot 실행 시 Flyway가 migration을 자동 적용한다.

### DB가 꼬였을 때

개발 DB 데이터를 날려도 되는 경우:

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose down -v
docker compose up -d postgres
cd agentpay-guard-api-server
./gradlew bootRun
```

이 흐름은 다음을 다시 수행한다.

```text
1. PostgreSQL volume 삭제
2. 새 volume 생성
3. init SQL로 role 생성
4. Spring Boot 실행
5. Flyway migration 적용
6. seed 데이터 적용
```

### DB role 확인

```bash
docker exec -it agentpay-guard-postgres \
  psql -U postgres -d agentpay_guard -c "\du"
```

### 앱 접속 계정 확인

```bash
PGPASSWORD=agentpay_guard psql \
  -h 127.0.0.1 \
  -p 5432 \
  -U agentpay_guard_seoin \
  -d agentpay_guard \
  -c "select current_user, current_database();"
```

## Docker Hub 사용 정책

현재 단계에서는 private Docker Hub에 PostgreSQL 이미지를 올리지 않는다.

이유:

- 공식 `postgres:16` image로 충분하다.
- DB 데이터는 Docker image가 아니라 volume에 저장된다.
- image를 공유해도 개발자가 입력한 DB 데이터가 공유되지는 않는다.
- private Docker Hub 권한 관리가 추가된다.

필요해질 때만 custom image를 검토한다.

custom image가 필요한 경우:

- PostgreSQL extension 사전 설치
- 운영과 유사한 DB 설정 bake
- init script를 image에 포함해야 하는 조직 정책

현재 협업 기준:

```text
Docker image: postgres:16 공식 이미지 사용
DB schema: Flyway migration으로 공유
demo data: seed SQL로 공유
개인 DB volume: 공유하지 않음
```

## dump 사용 정책

DB dump는 예외적으로만 사용한다.

사용 가능한 경우:

- 버그 재현을 위해 특정 DB 상태가 꼭 필요한 경우
- 발표 직전 데모 snapshot이 필요한 경우
- migration/seed로 표현하기 어려운 임시 데이터가 필요한 경우

dump 생성 예시:

```bash
mkdir -p docker/postgres/dumps

docker exec -t agentpay-guard-postgres \
  pg_dump -U postgres -d agentpay_guard \
  > docker/postgres/dumps/agentpay_guard_snapshot.sql
```

주의:

- dump에는 민감정보가 섞일 수 있다.
- dump 파일은 커밋 전에 반드시 검토한다.
- 기본 협업 수단은 dump가 아니라 migration/seed이다.

## 커밋 기준

DB 관련 변경 커밋에는 다음 중 필요한 파일을 포함한다.

- `docker-compose.yml`
- `docker/postgres/init/*.sql`
- `agentpay-guard-api-server/src/main/resources/db/migration/*.sql`
- seed SQL
- 관련 문서

커밋하지 않을 것:

- Docker volume 데이터
- `.env`
- 실제 secret
- DB dump 파일
- `build/`, `.gradle/`

## 현재 남은 작업

아직 구현되지 않은 것:

- `db/migration` 디렉토리
- `V1__init_schema.sql`
- demo seed 데이터
- 테스트용 profile

다음 작업:

```text
agentpay-guard-api-server/src/main/resources/db/migration/V1__init_schema.sql
```

최소 테이블부터 Flyway migration으로 생성한다.
