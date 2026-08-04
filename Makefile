.DEFAULT_GOAL := help

-include .env

# .env에서 읽은 인프라 설정을 Gradle과 Go 하위 프로세스에도 전달한다.
export POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
export REDIS_PORT REDIS_PASSWORD
export RABBITMQ_AMQP_PORT RABBITMQ_MANAGEMENT_PORT RABBITMQ_USER RABBITMQ_PASSWORD RABBITMQ_VHOST
export PURCHASE_RESEARCH_RABBITMQ_URL

COLLECTOR_DIR := services/collector
PRODUCT_BACKEND_DIR := services/product-backend
MCP_SERVER_DIR := services/mcp-server
WEB_DIR := frontend/purchase-web

MERCHANT ?= abcmart
QUERY ?= 구두
LIMIT ?= 3
MAX_ITEMS ?= 100
REQUEST_BUDGET ?= 10
OUTPUT_DIR ?= tmp/go-collector/$(MERCHANT)-$(MAX_ITEMS)
WEB_PORT ?= 3000
RABBITMQ_URL ?= $(if $(PURCHASE_RESEARCH_RABBITMQ_URL),$(PURCHASE_RESEARCH_RABBITMQ_URL),amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research)

.PHONY: help env infra-up infra-down infra-status infra-logs db-shell \
	collector-run collector-worker collector-worker-once collector-batch collector-test \
	product-backend-run product-backend-test \
	web-install web-dev web-lint web-build docs-check test check

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
		'  make collector-batch  Go 비교 Collector 단계별 수집 실행' \
		'  make product-backend-run  Spring Boot 상품 서버 실행' \
		'  make product-backend-test Spring Boot 테스트 실행' \
		'  make web-dev         Next.js 개발 서버 실행' \
		'  make docs-check      의존성/AI 설정과 공개 문서 동기화 검사' \
		'  make test            Go, Spring Boot, Next.js lint 일괄 검증' \
		'  make check           Compose 설정과 전체 코드 검증' \
		'' \
		'실행 예시:' \
		'  make collector-run' \
		'  make collector-batch MERCHANT=abcmart QUERY=구두 MAX_ITEMS=100 REQUEST_BUDGET=10' \
		'  make product-backend-run' \
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

collector-batch: ## 비교용 공개 상품을 gzip NDJSON과 checkpoint로 단계별 수집한다.
	cd $(COLLECTOR_DIR) && go run ./cmd/batch \
		-merchant "$(MERCHANT)" -query "$(QUERY)" -max-items "$(MAX_ITEMS)" \
		-request-budget "$(REQUEST_BUDGET)" -output-dir "../../$(OUTPUT_DIR)"

collector-test: ## Go Collector 전체 테스트를 실행한다.
	cd $(COLLECTOR_DIR) && go test ./...

product-backend-run: ## Spring Boot 상품 서버를 로컬에서 실행한다.
	cd $(PRODUCT_BACKEND_DIR) && ./gradlew bootRun

product-backend-test: ## Spring Boot와 Testcontainers 테스트를 실행한다.
	cd $(PRODUCT_BACKEND_DIR) && ./gradlew test

web-install: ## package-lock.json 기준으로 Next.js 의존성을 설치한다.
	cd $(WEB_DIR) && npm ci

web-dev: ## Next.js 개발 서버를 실행한다.
	cd $(WEB_DIR) && npm run dev -- --port $(WEB_PORT)

web-lint: ## Next.js ESLint 검사를 실행한다.
	cd $(WEB_DIR) && npm run lint

web-build: ## Next.js production build를 실행한다.
	cd $(WEB_DIR) && npm run build

docs-check: ## 의존성/AI 설정 변경에 공개 문서 갱신이 포함됐는지 확인한다.
	./scripts/check-document-sync.sh

test: collector-test product-backend-test web-lint ## Go, Spring Boot, Next.js를 검증한다.

check: docs-check ## 문서 동기화, Compose 설정, 테스트, Next.js production build를 모두 검증한다.
	docker compose config --quiet
	$(MAKE) test
	$(MAKE) web-build
