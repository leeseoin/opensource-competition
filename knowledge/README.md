# 구매 도메인 지식과 검색 평가

`knowledge`는 상품 원장이 아니라 검색 품질을 검토하는 평가 data와 사람 승인을 마친
구매 도메인 지식을 관리한다.

## 현재 상태

- `eval/retrieval-v1.json`: 고정된 구두 20개 snapshot을 사용하는 60개 검색 질문 초안
- `eval/reports/retrieval-ab-v1-summary.json`: 도입 전 strict AND와 현재 FTS의 DRAFT 자동 지표
- `eval/reviews/retrieval-ab-v1-first10-human-review.md`: 설명과 상품 정보를 함께 보는 첫 10개 사람 검토표
- `eval/reviews/retrieval-ab-v1-human-review.csv`: 전체 60개 결과를 나중에 집계하기 위한 원자료
- `schema/retrieval-evaluation.schema.json`: 평가 질문과 relevance 판정 계약
- `raw/source-unified-shoes-20260803.json`: Wiki claim이 참조하는 immutable snapshot metadata
- `wiki/shoes-taxonomy-v1.json`: 구두 상품군/한영 표현의 DRAFT page
- `schema/wiki-source.schema.json`, `schema/wiki-page.schema.json`: source/page/claim 검증 계약
- 전체 평가 상태: `DRAFT`
- 사람 검토 필요: true

`DRAFT` relevance와 허용 완화는 품질 수치 초안을 만드는 데만 사용한다. 팀원이 각 질문의
필수/선호 강도, 관련 상품과 relevance를 확인하고 `REVIEWED`로 전환하기 전에는 Wiki,
embedding model 또는 reranker의 운영 활성화 근거로 사용할 수 없다.

평가 data 불변식은 다음 명령으로 확인한다.

```bash
make retrieval-eval-check
make retrieval-ab-report
make wiki-check
```

Wiki 검사는 source snapshot SHA-256, source/claim ID, claim 출처와 evidence pointer,
`derived`/confidence 및 PUBLISHED 사람 검토 정보를 확인한다. 현재 page는 DRAFT이며
운영 Product Backend와 MCP 검색에 연결되지 않는다. 60개 DRAFT 질문의 모의 확장에서
Recall@20은 0.7533에서 0.7667로 올랐지만 nDCG@3은 0.6871에서 0.6848로 내려가 운영
활성화 기준을 통과하지 못했다.

가격/재고/옵션의 현재 사실은 이 폴더에 복사하지 않는다. 평가 snapshot과 실제 추천
snapshot은 분리하고 구매 전에는 Collector를 통한 최신 재검증이 필요하다.
