# 2026-08-06 ANALYSIS-003 검토형 구매 도메인 Wiki

- 기능 ID: `ANALYSIS-003`
- 구현 commit: `0cf1761`
- 기록 상태: 구현 기록

## 배경과 범위

LLM Wiki 방식을 상품 원장 대체가 아니라 출처와 사람 검토 상태를 가진 구매 도메인
의미 확장으로 제한했다. 구두 상품군/한영 표현 DRAFT page를 만들고 같은 60개 질문으로
FTS 단독 대비 품질을 비교했다.

## 구현 내용

- `knowledge/schema/wiki-source.schema.json:1` `WikiSource`: immutable source metadata와
  snapshot SHA-256 계약
- `knowledge/schema/wiki-page.schema.json:1` `WikiPage`: DRAFT/PUBLISHED/SUPERSEDED,
  claim/relation/derived/confidence/source 계약
- `knowledge/raw/source-unified-shoes-20260803.json:1` `unified-shoes-20260803`: 고정 판매처
  snapshot provenance
- `knowledge/wiki/shoes-taxonomy-v1.json:1` `shoes-taxonomy`: 구두 하위 상품군과 한영 표현
  DRAFT claim
- `scripts/check-wiki.sh:1`: source hash, 중복 ID, claim 출처, evidence pointer와 Published
  사람 검토 정보 검사
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/RetrievalEvaluationIntegrationTests.java:52`
  `comparesSqlBaselineAndFullTextRetrievalOnDraftDataset`: 직접 narrower/synonym 확장과
  reciprocal rank fusion의 offline 비교

## 발생 문제와 원인

DRAFT Wiki 모의 확장은 Recall@20을 높였지만 상위 후보 순서를 바꾸면서 nDCG@3을 FTS
단독보다 낮췄다. 또한 평가 relevance와 Wiki claim 모두 사람 검토 전 DRAFT다.

## 해결

Wiki를 Product Backend/MCP 운영 검색에 연결하지 않고 DRAFT로 유지했다. 사람 검토 없는
Published 상태와 출처 없는 claim을 lint에서 차단하고, 운영 검색은 FTS/vector fallback만
사용하도록 했다.

## 검증

- 루트 `make wiki-check`: source SHA-256/claim 출처/Published 검토 계약 통과
- `RetrievalEvaluationIntegrationTests`: FTS Recall@20 0.7533/nDCG@3 0.6871,
  DRAFT Wiki Recall@20 0.7667/nDCG@3 0.6848로 Recall 개선과 순위 품질 하락 확인
- 루트 `make check`: 전체 test/lint/build와 문서/평가/Wiki 검사 통과

## 남은 작업

- DRAFT claim과 60개 relevance 사람 검토
- PUBLISHED 상태 전이와 Product Backend index/API/MCP 연결
- 오래됨/충돌/superseded/orphan lint와 fallback 통합 테스트
- FTS 대비 Recall@20 또는 nDCG@3을 저하하지 않는 확장과 순위 결합
