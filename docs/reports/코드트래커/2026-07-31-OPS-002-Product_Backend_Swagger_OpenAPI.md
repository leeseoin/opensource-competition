# 2026-07-31 OPS-002 Product Backend Swagger와 OpenAPI

- 기능 ID: `OPS-002`
- 구현 commit: `3b59cd7fedae9e175e89030e8f94646d27baa476`
- 기록 상태: 구현 기록

## 배경과 범위

RabbitMQ 자동 적재를 구현하기 전에 Product Backend의 수동 적재 API와 상품 조회
API를 browser에서 직접 시험할 수 있도록 OpenAPI JSON과 Swagger UI를 추가했다.
이 기록은 `OPS-002` 전체 범위 중 API 문서와 수동 검증 경로만 다룬다.

## 구현 내용

- `services/product-backend/build.gradle:28`
  `springdoc-openapi-starter-webmvc-ui`: Spring Boot 4용 OpenAPI와 Swagger UI
  의존성을 추가한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/common/config/OpenApiConfiguration.java:14`
  `OpenApiConfiguration`: Product Backend 내부 API의 제목, 버전과 설명을 설정한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/controller/CollectionResultController.java:35`
  `CollectionResultController`: 수동 적재 API의 설명과 정상/실패 응답을 문서화한다.
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/controller/ProductQueryController.java:52`
  `ProductQueryController.search`: 저장 상품 조회 입력과 응답을 문서화한다.
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:221`
  `exposesOpenApiDocumentAndSwaggerUi`: OpenAPI 경로와 Swagger UI 진입점이 제공되는지
  검증한다.

## 발생 문제와 원인

없음.

## 해결

로컬 환경에서는 `/v3/api-docs`와 `/swagger-ui.html`을 제공하고, 운영 profile에서는
인증 없는 내부 API 문서가 노출되지 않도록 두 기능을 기본 비활성화했다.

## 검증

- `services/product-backend`에서 `./gradlew test`: 통과. OpenAPI JSON에 수동 적재와
  상품 조회 경로가 포함되고 Swagger UI 주소가 redirect되는 것을 검증했다.
- Swagger UI에서 ABC마트 Collector JSON 수동 적재: HTTP 200과 실제 DB 저장 확인.
- `git show 3b59cd7`: 설정, 의존성, controller annotation과 통합 테스트가 같은
  구현 commit에 포함된 것을 확인했다.

## 남은 작업

- 운영용 인증과 내부 접근 제한
- CI 계약 검사, 구조화 로그, metric 및 보안 점검 등 `OPS-002`의 나머지 범위
