# Database migrations

Python Research Backend가 소유하는 PostgreSQL schema 변경 이력이다.

```bash
uv run alembic upgrade head
uv run alembic downgrade -1
uv run alembic revision --autogenerate -m "변경 설명"
```

자동 생성된 migration은 그대로 적용하지 않고 제약조건, index, downgrade 내용을 검토한다.
