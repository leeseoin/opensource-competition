# Research Backend

Python 기반 구매 조사 application 서비스다.

예정 책임:

- Codex용 MCP server
- Next.js와 Codex Gateway용 FastAPI와 SSE
- 장기 서비스 전환 시 OpenAI API Agent orchestration
- Go Collector client
- 수집 결과 검증·정규화
- PostgreSQL 적재
- 리뷰 신호 추출
- 상품 비교, evidence, 최신 정보 변경 검증

외부 판매처에 직접 접근하지 않으며 최종 PostgreSQL 쓰기를 소유한다.

## 현재 구현된 DB 기반

- SQLAlchemy 모델: `products`, `merchant_products`, `offer_snapshots`, `product_options`, `evidence`
- Alembic 첫 migration
- Docker Compose PostgreSQL과 migration 실행 서비스
- Go Collector 검색 HTTP client와 Pydantic v1 응답 검증
- 판매처 상품 upsert, 가격·재고 snapshot, 옵션과 근거 transaction 저장
- 실제 검색·저장 개발용 CLI

조사 세션, 리뷰 저장·분석, MCP와 FastAPI API는 아직 구현 전이다.

## 로컬 DB 실행

저장소 루트의 `.env.example`을 `.env`로 복사한 뒤 필요한 값을 수정한다.

```bash
cp .env.example .env
```

`compose.yaml`은 `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` 값을 루트 `.env`에서 읽는다. 실제 `.env`는 커밋하지 않는다.

저장소 루트에서 PostgreSQL을 실행하고 migration을 적용한다.

```bash
docker compose up -d postgres
docker compose run --rm migrate
```

호스트에서는 기존 PostgreSQL과 충돌을 피하기 위해 기본적으로 `localhost:35432`에
연결한다. 컨테이너 사이에서는 `postgres:5432`를 사용한다. 호스트 포트는 루트
`.env`의 `POSTGRES_PORT`로 변경할 수 있다.

상태와 로그는 다음 명령으로 확인한다.

```bash
docker compose ps
docker compose logs postgres
```

DB를 중지할 때는 `docker compose down`을 사용한다. 데이터 volume까지 삭제하는
`docker compose down -v`는 로컬 데이터를 모두 지워도 될 때만 사용한다.

## Python 개발 환경과 테스트

```bash
cd services/research-backend
uv sync
uv run pytest
uv run alembic current
```

새 DB 구조는 SQLAlchemy 모델을 먼저 수정한 뒤 migration을 생성하고 내용을 검토한다.

```bash
uv run alembic revision --autogenerate -m "변경 설명"
```

## 실제 Collector 검색 결과 저장

첫 번째 터미널에서 Go Collector를 실행한다.

```bash
cd services/collector
go run ./cmd/server
```

두 번째 터미널의 저장소 루트에서 PostgreSQL과 migration을 준비한다.

```bash
docker compose up -d postgres
docker compose run --rm migrate
```

Python CLI로 실제 공개 검색 결과를 수집하고 저장한다.

```bash
services/research-backend/.venv/bin/purchase-research-collect \
  --merchant abcmart \
  --query "구두" \
  --limit 2

services/research-backend/.venv/bin/purchase-research-collect \
  --merchant 29cm \
  --query "구두" \
  --limit 2
```

결과는 저장한 판매처 상품, 가격 snapshot, 옵션과 근거 개수를 JSON으로 출력한다.
같은 판매처 상품을 다시 수집하면 `merchant_products`는 재사용하고
`offer_snapshots`과 `evidence`는 새 수집 시각의 기록으로 추가한다.

DB 저장 결과는 다음처럼 확인한다.

```bash
docker compose exec postgres sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT mp.merchant, mp.external_id, p.name FROM merchant_products mp JOIN products p ON p.id = mp.product_id;"'
```

실제 PostgreSQL repository 통합 테스트는 명시적으로 활성화한다.

```bash
RUN_POSTGRES_INTEGRATION=1 uv run pytest tests/integration/test_postgres_search_result.py
```
