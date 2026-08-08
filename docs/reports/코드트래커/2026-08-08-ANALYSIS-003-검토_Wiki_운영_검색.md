# 2026-08-08 ANALYSIS-003 검토 Wiki 운영 검색

- 기능 ID: `ANALYSIS-003`
- 구현 commit: `e8dc28d`
- 기록 상태: 구현 기록

## 배경과 범위

상품 DB에 `운동화` 문자열이 없어도 사람이 검토한 상품 분류 관계를 사용해 러닝화와
워킹화 후보를 찾도록 PUBLISHED Wiki를 운영 검색에 연결했다. 직접 관계만 지원하며
독립 REST API와 MCP 도구는 아직 범위 밖이다.

## 구현 내용

- `services/product-backend/src/main/resources/db/migration/V8__add_published_wiki_index.sql:1`
  `wiki_pages/wiki_claims`: 검토 상태, version과 출처를 보존하는 Wiki index
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/knowledge/service/WikiConceptIndexService.java:52`
  `indexReviewedPage`: PUBLISHED page만 검증해 적재
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/knowledge/service/WikiConceptIndexService.java:106`
  `expand`: 최신 검토 관계의 직접 확장어 반환
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductQueryService.java:193`
  `interleaveCandidates`: 원문과 확장어 후보의 공정한 순환 병합
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductQueryService.java:217`
  `mergeRetrievalSignals`: Wiki confidence와 관계 근거 보존
- `knowledge/wiki/sports-shoes-taxonomy-v1.json:1` `sports-shoes-taxonomy`: 검토된 운동화 하위 상품군 관계

## 발생 문제와 원인

실제 DB에 러닝화 후보가 있어도 `운동화` 원문 검색은 0건을 반환했고 첫 확장어 결과가
후보 pool을 독점했다. 상품군 전체 행 확장에서는 Wiki 근거도 사라졌다.

## 해결

검토된 직접 관계로 검색어를 확장하고 확장어별 결과를 순환 병합했다. 상품군을 다시
판정할 때 대표 후보의 Wiki 근거만 명시적으로 상속하며 적재와 조회 실패 시 기존 검색으로
fallback하도록 했다.

## 검증

- Product Backend `./gradlew test`: 통과
- `ProductStorageIntegrationTests.expandsConfirmedProductTypeWithPublishedWikiClaim`: 운동화 관계 확장과 필수 사이즈 검증 통과
- `ProductStorageIntegrationTests.fallsBackToExistingRetrievalWithoutPublishedWiki`: Wiki 없음 fallback 통과
- `ProductStorageIntegrationTests.rejectsDraftWikiPageFromRuntimeIndex`: DRAFT 차단 통과
- 실제 PostgreSQL E2E: 0건이던 운동화 질문에서 232건 회수와 상위 상품군 5개 반환 확인

## 남은 작업

- 구두, 용도와 색상 Wiki의 사람 검토 및 PUBLISHED 전환
- 독립 의미 확장 REST API와 MCP 도구
- PUBLISHED Wiki 적용 전후의 1,000개 질문 품질 평가
