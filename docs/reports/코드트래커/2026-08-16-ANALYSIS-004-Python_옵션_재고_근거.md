# 2026-08-16 ANALYSIS-004 Python 옵션 재고 근거

- 기능 ID: `ANALYSIS-004`
- 구현 commit: `ccda7c8`
- 기록 상태: 구현 기록

## 배경과 범위

RabbitMQ 작업이 Python 크롤러의 검색 결과만 저장해 ABC마트와 29CM의 상세 옵션 및 옵션별
재고 근거가 추천 검색에 연결되지 않았다. 이번 구현은 두 판매처의 공개 옵션을 Queue 수집
경로에서 보강하고, 확인된 구매 가능 옵션만 필수 사이즈와 색상 조건을 통과시키는 범위다.

## 구현 내용

- `purchase-research-agent/app/crawlers/abcmart/detail_fetcher.py:183` `DetailFetcher.attach_options`: 리뷰 요청 없이 ABC마트 공개 옵션 API를 조회하고 목록 응답의 구매 가능 사이즈를 보존한다.
- `purchase-research-agent/app/crawlers/cm29/detail_fetcher.py:45` `_parse_options`: 29CM 상세 HTML의 옵션 값과 `isSoldOut`/`isVisible` 공개 상태를 같은 옵션 객체에서 읽는다.
- `purchase-research-agent/app/crawlers/cm29/detail_fetcher.py:173` `Cm29DetailFetcher.attach_options`: 현재 상세 URL에서 옵션만 제한된 동시성으로 수집한다.
- `purchase-research-agent/app/messaging/processor.py:236` `_option_enrichment_limit`: 일반 검색은 반환 상한만 보강하고 사이즈/색상 필터가 있으면 후보 pool을 먼저 보강한다.
- `purchase-research-agent/app/messaging/processor.py:296` `_option_filter_values`: 구매 가능으로 확인된 옵션만 필수 사이즈와 색상 필터에 사용한다.
- `purchase-research-agent/app/services/collector_result_adapter.py:205` `_convert_options`: ABC마트 구매 가능 사이즈와 대표 색상을 공통 옵션 재고 및 provenance로 변환한다.
- `purchase-research-agent/app/services/collector_result_adapter.py:249` `_convert_29cm_options`: 29CM 옵션 조합과 재고 상태를 분리하지 않고 공통 옵션 계약에 보존한다.
- `contracts/collector/v1-abcmart/abcmart-crawl-item.schema.json:173` `options`: `available_sizes` 계약을 추가했다.
- `contracts/collector/v1-29cm/29cm-crawl-item.schema.json:113` `options`: 옵션별 `stock_status` 계약을 추가했다.

## 발생 문제와 원인

- Queue 처리기가 `detail_limit=0`으로만 검색해 실제 Worker에서 상세 옵션을 요청하지 않았다.
- 29CM 옵션은 목록 형태였지만 공통 Adapter는 객체 형태만 처리해 옵션 전체를 버렸다.
- 변환된 옵션 재고가 항상 `unknown`이라 필수 사이즈와 색상 조건을 만족할 수 없었다.
- 29CM 구 상세 URL은 redirect를 반환하지만 안전 정책상 자동 redirect를 따르지 않았다.
- 상품 대표 색상을 옵션 재고와 무관하게 필터에 사용하면 품절 색상이 필수 조건을 우회할 수 있었다.

## 해결

리뷰 없는 옵션 전용 수집 경로를 판매처 Adapter에 추가했다. ABC마트는 목록 JSON의 양수
재고 사이즈와 옵션 API 값을 함께 사용하고, 29CM는 redirect 없는 현재 URL과 상세 HTML의
옵션별 판매 상태를 사용한다. Queue 필터와 공통 CollectorResult 변환은 확인된 구매 가능
상태만 사용하고 출처 URL/수집 시각/수집기 version을 옵션마다 보존한다.

## 검증

- `make python-crawler-test`: 117개 통과
- `cd services/product-backend && ./gradlew test`: 전체 통과
- `make docs-check`: 통과
- 실제 ABC마트 Queue E2E: `230/WHITE` 조건 상품 1개 수집/검증 일치 1개/PostgreSQL 저장/추천 옵션 `available` 조회
- 실제 29CM Queue E2E: 백팩 3개 수집/검증 일치 3개/PostgreSQL 저장/`ONESIZE`, `블랙`, `Brown` 옵션 `available` 조회

## 남은 작업

- 용량/소재/규격/저장 용량 같은 범용 `attributes`와 판매처 상세 옵션 연결
- 의류/가방/가구/전자제품 고정 fixture의 오병합/중복 카드/옵션 출처 품질 평가
- 판매처 상위 상품 ID 또는 모델 코드 기반 확정 묶음
