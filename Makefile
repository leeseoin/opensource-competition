.DEFAULT_GOAL := help

-include .env

# .env에서 읽은 인프라 설정을 Gradle, Go와 Python 하위 프로세스에도 전달한다.
export POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
export REDIS_PORT REDIS_PASSWORD
export RABBITMQ_AMQP_PORT RABBITMQ_MANAGEMENT_PORT RABBITMQ_USER RABBITMQ_PASSWORD RABBITMQ_VHOST
export PURCHASE_RESEARCH_RABBITMQ_URL
export COLLECTOR_HTTP_ADDRESS COLLECTOR_READ_TIMEOUT COLLECTOR_WRITE_TIMEOUT
export COLLECTOR_IDLE_TIMEOUT COLLECTOR_SHUTDOWN_TIMEOUT COLLECTOR_WORKER_TIMEOUT COLLECTOR_CHROME_BIN
export PRODUCT_BACKEND_BASE_URL PRODUCT_BACKEND_REQUEST_TIMEOUT_MS
export CODEX_CLI_PATH CODEX_GATEWAY_TIMEOUT_MS PURCHASE_RESEARCH_REPO_ROOT PURCHASE_RESEARCH_MCP_ENTRY

COLLECTOR_DIR := services/collector
PRODUCT_BACKEND_DIR := services/product-backend
MCP_SERVER_DIR := services/mcp-server
PYTHON_COLLECTOR_DIR := services/python-collector
PYTHON_CRAWLER_DIR := purchase-research-agent
WEB_DIR := frontend/purchase-web

MERCHANT ?= abcmart
QUERY ?= 구두
LIMIT ?= 3
WEB_PORT ?= 3000
PYTHON_CRAWLER_PORT ?= 8012
RABBITMQ_URL ?= $(if $(PURCHASE_RESEARCH_RABBITMQ_URL),$(PURCHASE_RESEARCH_RABBITMQ_URL),amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research)

.PHONY: help env infra-up infra-down infra-status infra-logs db-shell \
	collector-run collector-worker collector-worker-once collector-test \
	python-collector-sync python-collector-test \
	python-crawler-env python-crawler-setup python-crawler-run python-crawler-test \
	product-backend-run product-backend-test \
	mcp-server-install mcp-server-build mcp-server-test \
	web-install web-dev web-test web-lint web-build docs-check test check

help: ## 사용할 수 있는 명령을 보여준다.
	@printf '%s\n' \
		'Purchase Research Agent 개발 명령' \
		'' \
		'  make env             루트 .env가 없으면 .env.example을 복사' \
		'  make infra-up        PostgreSQL, Redis, RabbitMQ 실행' \
		'  make infra-down      컨테이너 중지(데이터 볼륨 보존)' \
		'  make infra-status    로컬 인프라 상태 확인' \
		'  make infra-logs      로컬 인프라 로그 확인' \
		'  make db-shell        PostgreSQL 터미널 접속' \
		'  make collector-run   Go Collector 서버 실행' \
		'  make collector-worker  RabbitMQ 검색 작업 처리 Worker 실행' \
		'  make python-collector-test Python 비교 Collector 테스트 실행' \
		'  make python-crawler-setup 정우님 Python 크롤러 환경과 Chromium 준비' \
		'  make python-crawler-run 정우님 Python 크롤러와 DB 적재 API 실행' \
		'  make python-crawler-test 정우님 Python 변환 Adapter 테스트 실행' \
		'  make product-backend-run  Spring Boot 상품 서버 실행' \
		'  make product-backend-test Spring Boot 테스트 실행' \
		'  make mcp-server-test MCP Server 빌드와 계약 테스트' \
		'  make web-dev         Next.js 개발 서버 실행' \
		'  make docs-check      의존성/AI 설정과 공개 문서 동기화 검사' \
		'  make test            Go, Spring Boot, Next.js lint 일괄 검증' \
		'  make check           Compose 설정과 전체 코드 검증' \
		'' \
		'실행 예시:' \
		'  make collector-run' \
		'  make product-backend-run' \
		'  make python-crawler-run' \
		'  make web-dev WEB_PORT=2500'

env: ## 루트 .env가 없을 때만 예제 설정을 복사한다.
	@test -f .env || cp .env.example .env
	@printf '%s\n' '.env 준비 완료'

infra-up: ## PostgreSQL, Redis, RabbitMQ를 백그라운드로 실행한다.
	docker compose up -d postgres redis rabbitmq

infra-down: ## 컨테이너를 중지하되 데이터 볼륨은 보존한다.
	docker compose down

infra-status: ## 로컬 인프라 컨테이너 상태를 확인한다.
	docker compose ps

infra-logs: ## 로컬 인프라의 최근 로그를 계속 출력한다.
	docker compose logs --tail=100 -f postgres redis rabbitmq

db-shell: ## 실행 중인 PostgreSQL의 psql에 접속한다.
	docker compose exec postgres sh -lc 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

collector-run: ## Go Collector HTTP 서버를 실행한다.
	cd $(COLLECTOR_DIR) && go run ./cmd/server

collector-worker: ## RabbitMQ 검색 작업을 계속 처리하는 Go Worker를 실행한다.
	@cd $(COLLECTOR_DIR) && PURCHASE_RESEARCH_RABBITMQ_URL="$(RABBITMQ_URL)" go run ./cmd/worker

collector-worker-once: ## RabbitMQ 검색 작업 하나를 처리한 뒤 Go Worker를 종료한다.
	@cd $(COLLECTOR_DIR) && PURCHASE_RESEARCH_RABBITMQ_URL="$(RABBITMQ_URL)" go run ./cmd/worker --once

collector-test: ## Go Collector 전체 테스트를 실행한다.
	cd $(COLLECTOR_DIR) && go test ./...

python-collector-sync: ## uv.lock 기준으로 Python 비교 Collector 환경을 준비한다.
	cd $(PYTHON_COLLECTOR_DIR) && uv sync --frozen

python-collector-test: python-collector-sync ## Python Adapter, Contract와 checkpoint 테스트를 실행한다.
	cd $(PYTHON_COLLECTOR_DIR) && uv run --frozen python -m unittest discover -s tests -v

python-crawler-env: ## 정우님 Python 크롤러의 로컬 .env를 없을 때만 생성한다.
	@test -f $(PYTHON_CRAWLER_DIR)/.env || cp $(PYTHON_CRAWLER_DIR)/.env.example $(PYTHON_CRAWLER_DIR)/.env
	@printf '%s\n' 'Python 크롤러 .env 준비 완료'

python-crawler-setup: python-crawler-env ## 정우님 Python 크롤러 가상환경, 의존성과 Chromium을 준비한다.
	@test -x $(PYTHON_CRAWLER_DIR)/.venv/bin/python || python3 -m venv $(PYTHON_CRAWLER_DIR)/.venv
	$(PYTHON_CRAWLER_DIR)/.venv/bin/python -m pip install -r $(PYTHON_CRAWLER_DIR)/requirements.txt
	$(PYTHON_CRAWLER_DIR)/.venv/bin/python -m playwright install chromium

python-crawler-run: python-crawler-env ## 정우님 Python 크롤러와 Spring Boot DB 적재 연결 API를 실행한다.
	@test -x $(PYTHON_CRAWLER_DIR)/.venv/bin/uvicorn || (printf '%s\n' '먼저 make python-crawler-setup을 실행하세요.'; exit 1)
	cd $(PYTHON_CRAWLER_DIR) && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port $(PYTHON_CRAWLER_PORT) --env-file .env

python-crawler-test: ## 정우님 Python 결과의 CollectorResult 변환 단위 테스트를 실행한다.
	@test -x $(PYTHON_CRAWLER_DIR)/.venv/bin/python || (printf '%s\n' '먼저 make python-crawler-setup을 실행하세요.'; exit 1)
	cd $(PYTHON_CRAWLER_DIR) && PYTHONPYCACHEPREFIX=/private/tmp/purchase-research-python-cache .venv/bin/python -m unittest discover -s tests -v

product-backend-run: ## Spring Boot 상품 서버를 로컬에서 실행한다.
	cd $(PRODUCT_BACKEND_DIR) && ./gradlew bootRun

product-backend-test: ## Spring Boot와 Testcontainers 테스트를 실행한다.
	cd $(PRODUCT_BACKEND_DIR) && ./gradlew test


mcp-server-install: ## package-lock.json 기준으로 MCP Server 의존성을 설치한다.
	cd $(MCP_SERVER_DIR) && npm ci

mcp-server-build: ## Next.js Agent Gateway가 실행할 MCP Server를 빌드한다.
	cd $(MCP_SERVER_DIR) && npm run build

mcp-server-test: ## MCP REST client와 실제 stdio 도구 목록을 검증한다.
	cd $(MCP_SERVER_DIR) && npm test

web-install: mcp-server-install ## package-lock.json 기준으로 MCP Server와 Next.js 의존성을 설치한다.
	cd $(WEB_DIR) && npm ci

web-dev: mcp-server-build ## MCP Server를 빌드한 뒤 Next.js 개발 서버를 실행한다.
	cd $(WEB_DIR) && npm run dev -- --port $(WEB_PORT)

web-test: ## Codex Adapter와 Next.js server route 단위 테스트를 실행한다.
	cd $(WEB_DIR) && npm test

web-lint: ## Next.js ESLint 검사를 실행한다.
	cd $(WEB_DIR) && npm run lint

web-build: mcp-server-build ## MCP Server와 Next.js production bundle을 빌드한다.
	cd $(WEB_DIR) && npm run build

docs-check: ## 의존성/AI 설정 변경에 공개 문서 갱신이 포함됐는지 확인한다.
	./scripts/check-document-sync.sh

test: collector-test python-collector-test product-backend-test mcp-server-test web-test web-lint ## Go, Python, Spring Boot, MCP, Next.js를 검증한다.

check: docs-check ## 문서 동기화, Compose 설정, 테스트, Next.js production build를 모두 검증한다.
	docker compose config --quiet
	$(MAKE) test
	$(MAKE) web-build
