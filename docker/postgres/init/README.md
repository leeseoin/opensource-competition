# PostgreSQL init scripts

이 디렉토리는 AgentPay Guard 개발용 PostgreSQL 컨테이너가 처음 초기화될 때 실행할 SQL 파일을 보관한다.

`docker-compose.yml`에서 이 디렉토리는 컨테이너의 `/docker-entrypoint-initdb.d`로 마운트된다.

```yaml
volumes:
  - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
```

## 실행 시점

PostgreSQL 공식 Docker image는 `/var/lib/postgresql/data`가 비어 있는 최초 초기화 시점에만 `/docker-entrypoint-initdb.d` 안의 SQL 파일을 실행한다.

즉:

```text
새 volume으로 처음 실행 -> SQL 실행됨
이미 초기화된 volume이 있음 -> SQL 실행 안 됨
```

현재 init SQL:

```text
01-create-agentpay-users.sql
```

이 파일은 개발용 role을 만든다.

```text
agentpay_guard_seoin
agentpay_guard_jeongwoo
```

개발용 공통 비밀번호:

```text
agentpay_guard
```

## init SQL을 다시 실행하고 싶을 때

개발 DB 데이터를 지워도 되는 경우:

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose down -v
docker compose up -d postgres
```

`down -v`는 PostgreSQL volume을 삭제한다. 기존 DB, table, migration 기록, 테스트 데이터가 모두 삭제된다.

DB 데이터를 유지해야 하는 경우에는 컨테이너 안에서 직접 SQL을 실행한다.

```bash
docker exec -it agentpay-guard-postgres \
  psql -U postgres -d agentpay_guard \
  -f /docker-entrypoint-initdb.d/01-create-agentpay-users.sql
```

단, 현재 SQL은 같은 role이 이미 있으면 `CREATE ROLE`에서 실패할 수 있다. 반복 실행이 필요해지면 `IF NOT EXISTS` 방식의 idempotent SQL로 바꾼다.

## role 확인

```bash
docker exec -it agentpay-guard-postgres \
  psql -U postgres -d agentpay_guard -c "\du"
```

## Spring Boot 접속 확인

host에서 아래 명령으로 Docker PostgreSQL에 접속되는지 확인한다.

```bash
PGPASSWORD=agentpay_guard psql \
  -h 127.0.0.1 \
  -p 5432 \
  -U agentpay_guard_seoin \
  -d agentpay_guard \
  -c "select current_user, current_database();"
```
