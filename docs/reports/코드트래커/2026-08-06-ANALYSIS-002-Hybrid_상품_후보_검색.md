# 2026-08-06 ANALYSIS-002 Hybrid 상품 후보 검색

- 기능 ID: `ANALYSIS-002`
- 구현 commit: `0cf1761`
- 기록 상태: 구현 기록

## 배경과 범위

면접용 갈색 구두 265와 10만 원 이하처럼 의미 조건과 수집되지 않은 옵션이 함께 있을 때
기존 정확 `AND` 검색이 후보를 모두 제거하는 문제를 기준으로 SQL baseline, 필수/선호
조건, PostgreSQL FTS/trigram, 선택적 pgvector 검색과 fallback을 단계적으로 구현했다.

## 구현 내용

- `contracts/research/v1/purchase-condition.schema.json:1` `PurchaseCondition`: 각 조건의
  `required`/`preferred` 강도 계약
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/dto/PurchaseCondition.java:29`
  `PurchaseCondition`: Java 조건 강도와 validation
- `services/product-backend/src/main/resources/db/migration/V6__add_product_full_text_search.sql:1`
  `pg_trgm/search_text`: 기존 조건 JSON migration과 FTS/trigram index
- `services/product-backend/src/main/resources/db/migration/V7__add_product_embeddings.sql:1`
  `product_embeddings`: pgvector 1024차원 embedding과 HNSW index
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/repository/MerchantProductRepository.java:256`
  `searchCandidates`: 구조화 필터를 유지한 exact/FTS/trigram/vector 후보 결합
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/repository/MerchantProductRepository.java:329`
  `findCandidateRetrievalSignals`: keyword/vector 원시 점수 계산
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/embedding/ProductEmbeddingService.java:21`
  `ProductEmbeddingService`: content hash 기반 갱신, 질문 embedding과 전문 검색 fallback
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/product/service/ProductCandidateService.java:113`
  `assessCandidate`: 옵션 상태, 완화 조건, 검색/최신성/근거 완전성 점수 응답
- `frontend/purchase-web/app/chat/chat-experience.tsx:252` `ChatExperience`: 후보 검색 신호와
  일치/완화/확인 필요 근거 표시
- `knowledge/eval/retrieval-v1.json:1`: 20개 고정 snapshot을 사용하는 60개 DRAFT 평가 질문
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/RetrievalEvaluationIntegrationTests.java:52`
  `comparesSqlBaselineAndFullTextRetrievalOnDraftDataset`: SQL/FTS/DRAFT Wiki 동일 평가
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/RetrievalPerformanceIntegrationTests.java:42`
  `keepsTenThousandSnapshotFullTextSearchBelowOneSecondP95`: 10,000개 p95 opt-in 평가

## 발생 문제와 원인

- 수집 fixture에 색상 값이 없는데 정확 색상 조건을 적용해 baseline이 0건을 반환했다.
- 짧은 한국어 오타는 PostgreSQL 기본 trigram threshold에서 복구되지 않았다.
- 루트 Makefile이 빈 embedding 환경변수를 export해 disabled fallback bean이 생성되지 않았다.
- 로컬 Ollama에 BGE-M3 model이 없어 실제 model 기반 60개 평가는 실행하지 않았다.

## 해결

실제 수집된 옵션만 필수 제외 조건으로 사용하고 선호/unknown 조건은 후보를 제거하지
않도록 분리했다. `WORD_SIMILARITY` 0.3을 query에 명시하고 embedding provider 실패 시
FTS 경로를 유지했다. BGE-M3는 기본 비활성화하고 model을 다운로드하거나 운영
활성화하지 않았다.

## 검증

- 루트 `make check`: Go/Python Collector, Spring Boot, MCP, Next.js test/lint/build와
  문서/평가/Wiki/Compose 검사 통과
- 루트 `make retrieval-perf-test`: 10,000개/30회 측정 p50 325.129ms, p95 336.220ms,
  max 364.050ms로 1초 기준 통과
- `RetrievalEvaluationIntegrationTests`: SQL Recall@20 0.7333/nDCG@3 0.6671,
  FTS Recall@20 0.7533/nDCG@3 0.6871로 개선 확인
- `ProductStorageIntegrationTests`: 필수/선호 가격/색상, 옵션 unknown, 한국어 오타,
  pgvector fixture와 점수 신호 검증 통과

## 남은 작업

- 60개 DRAFT 질문과 relevance 사람 검토
- 실제 BGE-M3의 동일 평가 data 품질 비교
- 목표 Recall@20 0.90/nDCG@3 0.80/false zero 0.05 미만 달성
- 평가로 확정한 합계 가중치와 결정론적 재정렬
- embedding 갱신의 영속 비동기 작업 분리
