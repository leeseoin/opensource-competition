FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml uv.lock README.md alembic.ini ./
COPY src ./src
COPY migrations ./migrations

RUN pip install --no-cache-dir uv==0.8.11 \
    && uv sync --frozen --no-dev

ENV PATH="/app/.venv/bin:$PATH"

CMD ["alembic", "upgrade", "head"]
