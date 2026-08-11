# 2026-08-09 QUEUE-001 Python Collection Queue Worker

- 기능 ID: `QUEUE-001`
- 구현 commit: `32d6446`
- 기록 상태: 구현 기록

## 배경과 범위

정우님 Python 크롤러의 별도 `crawl_jobs` 메시지는 Spring Boot가 발행하는 Queue v1과
호환되지 않았고 실패 메시지도 정상 종료 시 ACK됐다. Python 전환 runtime이 기존
CollectionTask/CollectionResult v1을 그대로 사용하도록 계약 검증, 작업 처리,
RabbitMQ topology와 확인 처리 및 실행 명령을 구현했다.

## 구현 내용

- `purchase-research-agent/app/messaging/contracts.py:27` `CollectionTask`: 공유 JSON Schema,
  timezone, attempt와 maxAttempts 의미를 검증하고 재시도 작업을 생성
- `purchase-research-agent/app/messaging/processor.py:38` `CollectionTaskProcessor`: page를
  한 페이지씩 전달하고 가격/category/사이즈/색상/재고 필터와 timeout 및 실패 분류 적용
- `purchase-research-agent/app/messaging/rabbitmq.py:88` `RabbitCollectionWorker`: prefetch 1,
  persistent 발행, publisher confirm 뒤 ACK, 5초 retry와 DLQ 처리
- `purchase-research-agent/app/messaging/rabbitmq.py:177` `declare_topology`: Go/Spring과 같은
  durable exchange, search/result/retry/DLQ Queue와 routing key 선언
- `purchase-research-agent/app/crawlers/abcmart/json_fetcher.py:27` `AbcJsonFetcher`: category,
  SOLD_OUT과 양수 재고 사이즈만 Python 원본 상품에 보존
- `purchase-research-agent/app/crawlers/cm29/crawler.py:29` `Cm29Crawler`: page 시작점과 listing의
  displayPrice/category/isSoldOut 보존
- `purchase-research-agent/app/services/collector_result_adapter.py:33`
  `build_collector_result`: 실제 적용 filter, category, 상품 재고, 독립 사이즈/색상 옵션과
  `python-collector-v1` provenance 생성
- `purchase-research-agent/scripts/collection_worker.py:23` `run_worker`: Queue Worker CLI 진입점
- `purchase-research-agent/tests/test_collection_queue.py:164`
  `CollectionTaskContractTests/CollectionTaskProcessorTests/RabbitDecisionTests`: 계약, page,
  필터, timeout, retry, confirm 뒤 ACK와 발행 실패 requeue 검증
- `purchase-research-agent/scripts/check_collection_worker_rabbitmq.py:112`
  `check_success/check_retry/check_invalid_dlq`: 격리된 실제 broker의 세 경로 검증

## 발생 문제와 원인

- 첫 통합 실행은 `python scripts/check_collection_worker_rabbitmq.py`가 script 디렉터리만
  import 경로로 사용해 `app` module을 찾지 못했다.
- 결과 최상위 collectorVersion만 새 값으로 바뀌고 상품 provenance에는 이전 version이
  남아 계약 안의 출처 version이 서로 달랐다.
- 기존 검색 변환은 ABC마트 품절 사이즈를 포함하고 category/재고를 버렸으며, 사이즈가
  있으면 색상 옵션을 저장하지 않았다. 필터를 적용했다고 기록해도 결과에서 같은 근거를
  확인할 수 없는 상태였다.

## 해결

실행 명령을 `python -m scripts...`로 통일하고 collector version을 단일 상수로 만들었다.
필터 전 한 페이지에서 최대 50개를 읽고 filter 후 요청 limit을 적용했다. ABC마트와
29CM 검색 원본에서 확인된 category/재고를 보존하고 ABC마트 SIZE_LIST의 양수 수량만
판매 가능 사이즈로 변환했다. 결과 발행이 확인되기 전에는 ACK하지 않으며 발행 실패는
원본 작업을 requeue한다.

## 검증

- `make python-crawler-test`: 27개 통과
- `uv run --frozen python -m compileall -q app scripts tests`: 통과
- `make python-crawler-rabbitmq-test TEST_RABBITMQ_URL=<격리 vhost URL>`: 실제 RabbitMQ
  success/retry/DLQ 통과
- `make test`: Go/Python 비교기/Python runtime/Spring Boot/MCP/Next.js test와 lint 통과
- `make docs-check`: 통과
- `git diff --check`: 통과

## 남은 작업

- 실제 판매처 요청과 Spring Boot 결과 소비 및 PostgreSQL 저장을 연결한 opt-in Queue E2E
- Worker 비정상 종료 뒤 broker의 미확인 작업 재전달 검증
- `attributes` 필터의 판매처별 의미 정의와 지원
