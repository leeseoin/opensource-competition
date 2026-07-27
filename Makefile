.DEFAULT_GOAL := help

-include .env

COLLECTOR_DIR := services/collector
BACKEND_DIR := services/research-backend
WEB_DIR := apps/purchase-web

MERCHANT ?= abcmart
QUERY ?= 구두
LIMIT ?= 3
WEB_PORT ?= 3000
RABBITMQ_URL ?= $(if $(PURCHASE_RESEARCH_RABBITMQ_URL),$(PURCHASE_RESEARCH_RABBITMQ_URL),amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research)

.PHONY: help env infra-up infra-down infra-status infra-logs migrate db-shell \
	collector-run collector-worker collector-worker-once collector-test \
	backend-sync backend-test collect enqueue result-worker result-worker-once \
	web-install web-dev web-lint web-build test check

help: ## 사용할 수 있는 명령을 보여준다.
	@printf '%s\n' \
		'Purchase Research Agent 개발 명령' \
		'' \
		'  make env             루트 .env가 없으면 .env.example을 복사' \
		'  make infra-up        PostgreSQL, Redis, RabbitMQ 실행' \
		'  make infra-down      컨테이너 중지(데이터 볼륨 보존)' \
		'  make infra-status    로컬 인프라 상태 확인' \
		'  make infra-logs      로컬 인프라 로그 확인' \
		'  make migrate         Alembic DB migration 적용' \
		'  make db-shell        PostgreSQL 터미널 접속' \
		'  make collector-run   Go Collector 서버 실행' \
		'  make collect         HTTP 방식으로 검색 후 PostgreSQL 저장' \
		'  make enqueue         RabbitMQ에 검색 작업 등록' \
		'  make collector-worker  RabbitMQ 검색 작업 처리 Worker 실행' \
		'  make result-worker   RabbitMQ 결과 DB 저장 Worker 실행' \
		'  make web-dev         Next.js 개발 서버 실행' \
		'  make test            Go, Python, Next.js lint 일괄 검증' \
		'  make check           Compose 설정과 전체 코드 검증' \
		'' \
		'수집 예시:' \
		'  make collect MERCHANT=abcmart QUERY=구두 LIMIT=3' \
		'  make enqueue MERCHANT=abcmart QUERY=구두 LIMIT=3' \
		'  make collect MERCHANT=29cm QUERY=가방 LIMIT=5' \
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

migrate: ## Alembic migration을 최신 버전까지 적용한다.
	docker compose run --rm migrate

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

backend-sync: ## uv로 Python Backend 개발 의존성을 준비한다.
	cd $(BACKEND_DIR) && uv sync

backend-test: ## Python Backend 테스트를 실행한다.
	cd $(BACKEND_DIR) && uv run pytest

collect: ## 지정한 판매처 검색 결과를 수집해 PostgreSQL에 저장한다.
	$(BACKEND_DIR)/.venv/bin/purchase-research-collect \
		--merchant "$(MERCHANT)" \
		--query "$(QUERY)" \
		--limit "$(LIMIT)"

enqueue: ## 지정한 판매처 검색 작업을 RabbitMQ에 등록한다.
	@PURCHASE_RESEARCH_RABBITMQ_URL="$(RABBITMQ_URL)" \
		$(BACKEND_DIR)/.venv/bin/purchase-research-enqueue \
		--merchant "$(MERCHANT)" \
		--query "$(QUERY)" \
		--limit "$(LIMIT)"

result-worker: ## RabbitMQ 결과를 계속 검증해 PostgreSQL에 저장한다.
	@PURCHASE_RESEARCH_RABBITMQ_URL="$(RABBITMQ_URL)" \
		$(BACKEND_DIR)/.venv/bin/purchase-research-result-worker

result-worker-once: ## RabbitMQ 결과 하나를 DB에 저장한 뒤 Worker를 종료한다.
	@PURCHASE_RESEARCH_RABBITMQ_URL="$(RABBITMQ_URL)" \
		$(BACKEND_DIR)/.venv/bin/purchase-research-result-worker --once

web-install: ## package-lock.json 기준으로 Next.js 의존성을 설치한다.
	cd $(WEB_DIR) && npm ci

web-dev: ## Next.js 개발 서버를 실행한다.
	cd $(WEB_DIR) && npm run dev -- --port $(WEB_PORT)

web-lint: ## Next.js ESLint 검사를 실행한다.
	cd $(WEB_DIR) && npm run lint

web-build: ## Next.js production build를 실행한다.
	cd $(WEB_DIR) && npm run build

test: collector-test backend-test web-lint ## 네트워크 없이 기본 코드 검증을 실행한다.

check: ## Compose 설정, 테스트, Next.js production build를 모두 검증한다.
	docker compose config --quiet
	$(MAKE) test
	$(MAKE) web-build
