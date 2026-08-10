#!/bin/sh

set -eu

# Swagger 단계 테스트에 필요한 Spring Boot와 Python API/Worker를 한 번에 실행한다.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BACKEND_URL=${BACKEND_BASE_URL:-http://127.0.0.1:8080}
PYTHON_PORT=${PYTHON_CRAWLER_PORT:-8012}
BACKEND_PID=""

# 이 스크립트가 시작한 Spring Boot만 종료하고 기존 프로세스는 건드리지 않는다.
cleanup() {
    if [ -n "$BACKEND_PID" ]; then
        kill "$BACKEND_PID" 2>/dev/null || true
        wait "$BACKEND_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT INT TERM

if curl --silent --fail "$BACKEND_URL/actuator/health" >/dev/null 2>&1; then
    printf '%s\n' '이미 실행 중인 Product Backend를 사용합니다.'
else
    printf '%s\n' 'Product Backend를 시작합니다. 최초 실행은 잠시 걸릴 수 있습니다.'
    (
        cd "$REPO_ROOT/services/product-backend"
        exec ./gradlew bootRun
    ) &
    BACKEND_PID=$!

    backend_ready=false
    attempt=0
    while [ "$attempt" -lt 90 ]; do
        if curl --silent --fail "$BACKEND_URL/actuator/health" >/dev/null 2>&1; then
            backend_ready=true
            break
        fi
        if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
            printf '%s\n' 'Product Backend가 준비 전에 종료됐습니다.' >&2
            exit 1
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    if [ "$backend_ready" != true ]; then
        printf '%s\n' '90초 안에 Product Backend가 준비되지 않았습니다.' >&2
        exit 1
    fi
fi

printf '\n%s\n%s\n\n' \
    'Python Collector Swagger가 준비됐습니다.' \
    "브라우저에서 http://localhost:$PYTHON_PORT/docs 를 열고 00부터 03까지 실행하세요."

cd "$REPO_ROOT/purchase-research-agent"
PYTHON_COLLECTION_WORKER_ENABLED=true \
PURCHASE_RESEARCH_RABBITMQ_URL=${PURCHASE_RESEARCH_RABBITMQ_URL:-amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research} \
uv run --frozen uvicorn app.main:app --host 0.0.0.0 --port "$PYTHON_PORT" --env-file .env
