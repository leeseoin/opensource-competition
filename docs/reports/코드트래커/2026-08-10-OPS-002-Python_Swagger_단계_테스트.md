# 2026-08-10 OPS-002 Python Swagger 단계 테스트

- 기능 ID: `OPS-002`
- 구현 commit: `7852165`
- 기록 상태: 구현 기록

## 배경과 범위

Python Collector의 Queue 전체 경로를 손으로 확인하려면 기존에는 인프라, Spring Boot,
Python Worker와 API를 따로 실행하고 HTTP 요청도 직접 구성해야 했다. 이번 변경은 한 명령으로
로컬 구성요소를 준비하고 Python Swagger에서 준비 확인, 작업 등록, 진행 조회와 저장 상품
조회를 번호 순서대로 실행할 수 있는 수동 검증 기반을 추가했다.

## 구현 내용

- `purchase-research-agent/app/api/endpoints/manual_test.py:17` `ManualSearchFilters`:
  가격, 옵션과 재고 조건을 Swagger 입력 계약으로 제공
- `purchase-research-agent/app/api/endpoints/manual_test.py:103` `readiness`: Product Backend와
  Python Queue Worker 준비 상태 및 다음 단계를 반환
- `purchase-research-agent/app/api/endpoints/manual_test.py:146` `create_collection_task`:
  소량 수집 요청을 Product Backend 작업 등록 API로 전달
- `purchase-research-agent/app/api/endpoints/manual_test.py:175` `get_collection_job`:
  job 진행 상태와 다음 확인 단계를 반환
- `purchase-research-agent/app/api/endpoints/manual_test.py:209` `search_stored_products`:
  PostgreSQL에 저장된 최신 상품을 Product Backend를 통해 조회
- `purchase-research-agent/app/main.py:34` `lifespan`: 통합 실행 모드에서 Python Queue
  Worker를 API lifecycle과 함께 시작하고 종료
- `purchase-research-agent/app/services/backend_store_service.py:49`
  `BackendStoreService.health/create_collection_task/get_collection_job/search_products`:
  Python API가 Product Backend REST API만 호출하도록 기존 경계를 유지
- `scripts/run-python-crawler-swagger.sh:13` `cleanup`: 통합 명령이 시작한 Spring Boot만
  종료하고 기존 process와 data container는 보존
- `Makefile:132` `python-crawler-swagger`: 인프라, Backend, Python API와 Worker를 한 번에 실행
- `purchase-research-agent/tests/test_manual_test_api.py:14` `ManualTestApiTests`: 번호가 붙은
  OpenAPI 경로, 기본 입력, Backend 전달, 다음 단계 안내와 실패 응답을 검증

## 발생 문제와 원인

- 증상: 기존 수동 검증은 여러 terminal과 직접 작성한 HTTP 요청이 필요해 단계와 입력을
  놓치기 쉬웠다.
- 원인: Python Swagger는 직접 수집 및 저장 API만 제공했고 Queue 등록, job 상태와 DB 결과
  조회를 하나의 검증 흐름으로 연결하지 않았다.

## 해결

Python FastAPI에 00부터 03까지 번호가 붙은 endpoint와 안전한 기본값을 추가했다. 통합 실행
명령은 data container의 health를 기다리고 Product Backend 및 Python Worker를 함께 시작한다.
최초 성공 여부를 보기 위한 기본 요청에는 정확 조건을 넣지 않고 상품 3개만 요청하며, 조건은
사용자가 다음 실행에서 하나씩 추가할 수 있게 했다.

## 검증

- `make python-crawler-test`: 39개 테스트 통과
- `make python-crawler-safety-check`: 통과
- `sh -n scripts/run-python-crawler-swagger.sh`: 통과
- `make -n python-crawler-swagger`: 통합 실행 명령 구성 확인
- `make python-crawler-swagger`: PostgreSQL/Redis/RabbitMQ health, Spring Boot, Python API와
  내장 Worker 실제 기동 성공
- `GET /api/v1/manual-test/00-readiness`: `ready: true`, Backend `UP`, Worker 실행 상태 확인
- `GET /openapi.json`: 00/01/02/03 tag와 네 단계 경로 확인
- `Ctrl+C` 종료 검증: 이 명령이 시작한 Spring Boot와 Python은 종료되고 data container는
  healthy 상태로 유지
- `git diff --check`: 통과

## 남은 작업

- Swagger 1단계에서 실제 판매처 작업을 실행하고 2단계 최종 상태 및 3단계 저장 상품을 사람이
  확인하는 opt-in Queue E2E
- 실제 판매처 smoke test는 요청 제한을 지키며 기본 CI 밖에서 유지
