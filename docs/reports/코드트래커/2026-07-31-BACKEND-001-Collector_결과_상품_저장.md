# 2026-07-31 BACKEND-001 Collector 결과와 상품 snapshot 저장

- 기능 ID: `BACKEND-001`
- 구현 commit: `3b59cd7fedae9e175e89030e8f94646d27baa476`
- 기록 상태: 구현 기록

## 배경과 범위

Go Collector가 반환한 공통 JSON을 Spring Boot가 검증하고 PostgreSQL에 상품,
판매처 상품, 가격/재고 snapshot, 옵션과 출처 근거로 저장하는 경로를 구현했다.
같은 판매처 상품은 갱신하고 수집 시점별 snapshot은 이력으로 추가하며, 최신
snapshot을 조회하는 내부 상품 API와 Swagger 수동 적재 API까지 포함한다.

## 구현 내용

- `services/product-backend/src/main/resources/db/migration/V1__initial_product_collection.sql:1`
  `V1 initial product collection`: 상품, 판매처 상품, offer snapshot, 옵션과 근거
  테이블 및 인덱스를 생성한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/dto/CollectorResult.java:36`
  `CollectorResult`: Go Collector v1 결과를 Java DTO로 변환하고 필수값과 저장 가능
  상태를 검증한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:75`
  `CollectorResultStoreService.store`: 판매처 상품 upsert와 snapshot/옵션/근거 저장을
  하나의 transaction으로 처리한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionResultController.java:35`
  `CollectionResultController`: 실제 Collector JSON을 수동 적재하고 저장 개수를
  반환하는 내부 API를 제공한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductQueryService.java:59`
  `ProductQueryService.search`: 조건과 일치하는 판매처 상품에 최신 snapshot과 옵션을
  결합하고 `totalCount`와 `hasNext`를 반환한다.
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:79`
  `storesCollectorResultThroughHttpEndpoint`: HTTP 요청부터 실제 Testcontainers
  PostgreSQL 저장까지 검증한다.

## 발생 문제와 원인

- 최초 package가 기술 계층 기준으로 나뉘어 팀의 도메인 중심 구조와 맞지 않았다.
- Flyway의 `CHAR(3)` 통화 열과 JPA의 `VARCHAR(3)` 판단이 달라 Hibernate schema
  검증이 실패했다.
- 로컬 PostgreSQL Volume에 이전 Alembic schema가 남아 Flyway가 서버 시작을
  중단했다.

## 해결

- `collection`, `product`, `evidence` 도메인 중심 package로 재구성했다.
- 통화 열을 `VARCHAR(3)`으로 통일해 Flyway와 JPA schema 검증을 맞췄다.
- 로컬 schema를 정리하고 Flyway V1을 적용해 `flyway_schema_history` 기준으로
  전환했다.

## 검증

- `services/product-backend`에서 `./gradlew test`: 통과. Flyway 재적용, 정상/실패
  저장, 상품 중복 방지, snapshot 추가와 최신 상품 조회를 검증했다.
- Swagger `POST /internal/v1/collection-results`: ABC마트 `manual-abc-001` 실제 요청
  성공.
- PostgreSQL SQL 조회: 상품 3개, 판매처 상품 3개, snapshot 3개, 옵션 19개,
  근거 3개 저장 확인.
- `git show 3b59cd7`: 관련 source, migration, fixture와 integration test가 구현
  commit에 포함된 것을 확인했다.

## 남은 작업

- 29CM 실제 Collector 결과의 수동 적재 검증
- RabbitMQ 결과 consumer에서 같은 저장 서비스를 호출하는 Queue E2E
- 동시 최초 저장 시 unique 충돌 처리
- 리뷰와 실측값 저장 및 JSON Schema 직접 검증
