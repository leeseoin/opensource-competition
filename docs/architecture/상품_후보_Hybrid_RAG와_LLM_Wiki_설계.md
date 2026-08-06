# 상품 후보 Hybrid RAG와 검토형 LLM Wiki 설계

- 작성일: 2026-08-06
- 상태: planned
- 적용 범위: 상품 후보 검색/구매 도메인 지식/검색 품질 평가

## 1. 목적

현재 Product Backend의 후보 검색은 상품 종류, 가격, 통화, 재고, 사이즈와 색상을
하나의 SQL `AND` 조건으로 적용한다. 이 방식은 저장된 값과 사용자의 표현이 정확히
일치할 때는 재현하기 쉽지만 다음 질문에서 실제 후보가 있어도 결과가 비기 쉽다.

```text
면접용 갈색 구두 265, 10만 원 이하
```

- 상품명이 `구두`가 아니라 `더비슈즈`, `옥스퍼드` 또는 `로퍼`일 수 있다.
- 색상이 `brown`이 아니라 `브라운`, `다크브라운` 또는 `코냑`일 수 있다.
- 판매처에 따라 사이즈 옵션이 검색 단계에 없어서 일치 여부가 `unknown`일 수 있다.
- `면접`과 상품 종류의 관계는 현재 상품/옵션 테이블만으로 표현하지 못한다.

이 설계는 정확한 판매처 사실을 유지하면서 의미상 관련 있는 상품을 넓게 찾고,
후보 3개의 선정 이유와 완화 조건을 재현 가능하게 설명하는 방법을 정의한다.

## 2. 설계 결론

상품 후보 검색은 다음 네 계층을 결합한다.

```text
구매 조건 구조화
  ↓
검토형 LLM Wiki 기반 의미 확장
  ↓
PostgreSQL 구조화 필터 + 전문 검색 + 벡터 검색
  ↓
설명 가능한 재정렬 + 구매 전 최신 snapshot 재검증
```

LLM Wiki는 상품 원장이나 검색엔진 전체를 대체하지 않는다. 구매 목적, 상품군,
동의어와 판매처 카테고리 관계를 제공하는 지식 계층으로만 사용한다. 가격, 재고,
옵션과 판매처 사실은 계속 Collector가 수집한 PostgreSQL snapshot을 기준으로 한다.

## 3. 책임 경계

### 3.1 Go Collector

- 공개 판매처의 상품/가격/재고/옵션/리뷰를 수집한다.
- 검색어 확장, embedding 생성, Wiki 작성과 최종 후보 점수 계산을 하지 않는다.
- 모든 판매처 사실에 `sourceUrl`, `collectedAt`, `collectorVersion`을 포함한다.

### 3.2 Spring Boot Product Backend

- 구매 조건의 필수/선호 강도를 검증하고 저장한다.
- 상품 검색 문서와 embedding metadata를 관리한다.
- 구조화 필터, 전문 검색, 벡터 검색, 결과 통합과 결정론적 재정렬을 수행한다.
- 검토가 끝난 Wiki만 검색에 사용하고 PostgreSQL 최종 쓰기를 담당한다.
- 후보별 점수 구성, 일치 상태, 완화 조건과 근거를 반환한다.

### 3.3 MCP Server

- Product Backend의 조건 확인, 의미 확장, 후보 검색과 재검증 REST API를 MCP 도구로
  변환한다.
- Wiki 파일, PostgreSQL과 embedding provider에 직접 접근하지 않는다.

### 3.4 Codex Plugin과 Next.js Web

- 사용자가 조건별 `required`와 `preferred`를 확인하고 수정하게 한다.
- Wiki가 확장한 개념과 후보별 일치/불일치/확인 불가 상태를 표시한다.
- 상품 사실을 생성하거나 검색 점수를 임의로 변경하지 않는다.

## 4. 구매 조건 계약

단순 문자열 배열만으로는 필수 조건과 선호 조건을 구분할 수 없다. 후보 검색용
조건에는 다음 강도를 사용한다.

| 강도 | 의미 | 기본 처리 |
|---|---|---|
| `required` | 위반하면 구매할 수 없는 조건 | 불일치 후보 제외 |
| `preferred` | 가능하면 만족해야 하는 조건 | 점수와 설명에 반영 |

예시는 다음과 같다.

```json
{
  "productType": {"value": "구두", "priority": "required"},
  "usage": [{"value": "면접", "priority": "preferred"}],
  "colors": [{"value": "brown", "priority": "preferred"}],
  "sizes": [{"value": "265", "priority": "required"}],
  "price": {
    "max": 100000,
    "currency": "KRW",
    "priority": "required"
  }
}
```

기존 `PurchaseCondition` 변경은 Web/MCP/Product Backend가 함께 사용하는 별도 계약
변경으로 수행한다. 사용자가 강도를 확인하기 전에는 검색을 실행하지 않는다.

## 5. 후보 검색 흐름

### 5.1 의미 확장

검토된 Wiki에서 구매 목적과 표현을 검색 개념으로 확장한다.

```text
면접 → 포멀/정장화/더비슈즈/옥스퍼드/단정한 로퍼
brown → 브라운/갈색/다크브라운/코냑
```

확장 결과에는 Wiki page ID, relation type, `derived`, confidence와 source ID를
포함한다. 출처 없는 확장어는 운영 검색에 사용하지 않는다.

### 5.2 구조화 필터

다음 값은 최신 snapshot과 옵션을 사용한다.

- 판매처
- 가격 상한/하한과 통화
- 상품 판매 재고 상태
- 사용자가 `required`로 확인한 옵션

사이즈와 색상은 `MATCH`, `MISMATCH`, `UNKNOWN` 세 상태로 계산한다. `required` 조건의
`MISMATCH`는 제외한다. `UNKNOWN`은 일치 후보가 부족할 때 제한적으로 포함할 수 있지만
반드시 확인 불가 상태와 완화 이유를 표시한다.

### 5.3 Hybrid Retrieval

구조화 필터를 통과할 수 있는 상품을 대상으로 다음 결과를 합친다.

1. PostgreSQL 전문 검색 또는 trigram 검색
2. 상품 검색 문서의 embedding 유사도 검색
3. Wiki가 제공한 검토된 확장어/관계 검색

상품 검색 문서는 현재 수집된 사실만 사용한다.

```text
상품명 / 브랜드 / 카테고리 / 수집 당시 검색어 / 공개 상세 설명 /
비식별 리뷰 신호 / 판매처
```

현재 수집하지 않은 상세 설명과 리뷰 신호는 빈 값으로 유지하며 LLM이 채우지 않는다.
키워드 검색과 벡터 검색 결과는 순위 결합 방식으로 통합하고, embedding 실패 시 전문
검색만으로 정상 동작해야 한다.

### 5.4 재정렬

최종 후보 점수는 분해 가능한 신호로 계산한다.

```text
최종 점수
= 키워드 검색 순위
+ 벡터 검색 순위
+ Wiki 개념 일치
+ 옵션 일치
+ 최신성
+ 근거 완전성
```

LLM reranker는 초기 필수 기능이 아니다. 결정론적 점수보다 평가 결과가 개선되고,
schema validation/timeout/fallback이 준비된 경우에만 상위 후보의 선택적 단계로
도입한다.

### 5.5 후보 응답

후보 응답은 합계 점수뿐 아니라 다음 항목을 포함한다.

```json
{
  "candidateId": 123,
  "keywordScore": 0.71,
  "semanticScore": 0.88,
  "wikiConceptScore": null,
  "freshnessScore": 0.8,
  "evidenceCompletenessScore": 1.0,
  "sizeStatus": "MATCH",
  "colorStatus": "MATCH",
  "matchReasons": [
    "면접용 정장화 상품군과 의미적으로 일치",
    "265 재고 확인"
  ],
  "relaxedConditions": []
}
```

점수의 절대값과 가중치는 평가 데이터로 확정한다. 설계 단계에서 임의의 가중치를
완료 기준으로 고정하지 않는다.

2026-08-06 구현은 exact/FTS/trigram의 최대 keyword 점수, 같은 model의 cosine
semantic 점수, 수집 경과 시간 구간의 최신성 점수와 최신 가격/재고/provenance 필드의
근거 완전성 점수를 반환한다. 검토 Wiki가 운영 미연결이면 `wikiConceptScore`는 null이며,
DRAFT relevance로 임의 가중치를 고정하지 않기 위해 합계 점수와 가중 재정렬은 아직
반환하지 않는다.

## 6. 검토형 LLM Wiki

### 6.1 적용 대상

- 구매 목적과 상품군 관계
- 상품 종류의 상위/하위 관계
- 한국어/영어/판매처 표현의 동의어
- 색상 계열과 소재/스타일 관계
- 판매처별 카테고리 대응
- 출처가 연결된 비식별 리뷰 기반 경향

### 6.2 제외 대상

- 현재 가격/재고/옵션
- 사용자 개인정보
- 원문 리뷰 작성자 식별정보
- 출처 없는 패션 규칙
- LLM이 추측한 판매처 상품 사실

### 6.3 상태와 갱신 흐름

```text
immutable source
  ↓ LLM structured extraction
DRAFT Wiki page
  ↓ schema/lint/source 검사
사람 검토
  ↓
PUBLISHED
  ↓ 새 근거 또는 반박 근거
SUPERSEDED 또는 새 version
```

PoC에서는 LLM이 작성한 page를 자동으로 `PUBLISHED`로 전환하지 않는다. Git에서
검토한 Markdown과 metadata를 Product Backend가 읽고 PostgreSQL 검색 index에
적재한다.

권장 repository 구조는 다음과 같다.

```text
knowledge/
├── raw/       # 원본 파일 대신 출처 metadata와 허용된 원본 참조
├── wiki/      # 검토된 Markdown page
├── schema/    # page/relation JSON Schema
└── eval/      # 검색 평가 질문과 판정 data
```

Published claim에는 최소한 다음 항목을 둔다.

- claim text
- `derived`
- confidence
- source IDs
- review status
- version/supersedes

## 7. 검색 품질 평가

검색 품질은 실제 상품 snapshot을 고정한 offline 평가와 E2E를 분리한다. 초기 평가
데이터는 질문 60개로 시작한다.

| 질문 유형 | 개수 |
|---|---:|
| 정확한 상품명/브랜드 | 15 |
| 용도 중심 의미 검색 | 20 |
| 여러 조건과 조건 완화 | 10 |
| 정답이 없어야 하는 질문 | 10 |
| 오래된 정보와 재검증 | 5 |

각 질문에는 필수 조건, 관련 상품의 `0`부터 `3`까지 relevance, 허용 가능한 완화와
정답 없음 여부를 사람이 기록한다.

### 7.1 초기 품질 기준

| 지표 | 초기 목표 |
|---|---:|
| 필수 조건 위반율 | 0% |
| 판매처 사실의 출처 연결률 | 100% |
| 완화 조건 표시율 | 100% |
| Recall@20 | 90% 이상 |
| nDCG@3 | 0.80 이상 |
| 적합 상품이 있는데 0건을 반환한 비율 | 5% 미만 |
| 10,000개 snapshot 로컬 검색 p95 | 1초 이하 |
| 출처 없는 Published Wiki claim | 0건 |

초기 수치는 설계 목표다. 실제 baseline이 현저히 낮거나 판정 data가 부족하면 값을
숨기지 않고 baseline과 조정 근거를 문서화한다.

### 7.2 단계별 비교

```text
A. 현재 SQL AND 검색
B. 전문 검색 + 동의어
C. 전문 검색 + 벡터 검색
D. 전문 검색 + 벡터 검색 + 검토형 Wiki
E. D + 선택적 LLM reranker
```

각 단계를 같은 snapshot/질문/판정 data로 비교한다. Wiki와 LLM reranker는 이전
단계보다 의미 검색 Recall@20 또는 nDCG@3을 개선하지 못하거나 출처 정확성을
낮추면 운영 경로에서 보류한다.

## 8. 저장과 실행 계획

PostgreSQL 검색 구조는 Flyway V6/V7에서 단계적으로 구현한다.

- 상품별 검색 문서/content hash/index version
- embedding vector/model/provider/version/generatedAt
- Wiki source/page/relation/version/review status
- 검색 실행의 query/확장어/필터/후보 점수/완화 기록

embedding provider port를 두고 기본값은 비활성화했다. 선택적 로컬 구현은 Ollama의
`bge-m3:567m`을 사용하며 1024차원 벡터를 pgvector 0.8.2에 저장한다. 모델과
PostgreSQL extension 정보는 `THIRD_PARTY_NOTICES.md`, `AI_USAGE.md`와 대회 규정 대응
체크리스트에 동기화했다. 현재 개발 환경에는 BGE-M3가 설치되어 있지 않아 실제 모델을
사용한 60개 품질 수치는 아직 측정하지 않았다.

## 9. 구현 단계

2026-08-06 기준 단계 0의 SQL baseline smoke test와 필수/선호 조건 계약을 구현했다.
단계 1에서는 PostgreSQL `simple` 전문 검색과 `pg_trgm` 기반 상품명/브랜드/수집 검색어
오타 fallback을 먼저 구현했다. 후보별 옵션 `MATCH`/`MISMATCH`/`UNKNOWN`과 완화/확인
필요 설명을 추가했으며 pgvector 0.8.2와 선택적 로컬 BGE-M3/Ollama adapter를 연결했다.
embedding은 기본 비활성화이며 model 실패 시 전문 검색을 사용한다. 20개 고정 상품과
60개 DRAFT 질문을 사용한 PostgreSQL 통합 평가는 구현했다. 신발 상품군/한영 표현
Wiki Lite도 immutable source와 DRAFT page로 작성하고 source 해시/claim 출처/Published
사람 검토 계약을 lint한다. 다만 DRAFT Wiki는 운영 검색에 연결하지 않았다.

### 2026-08-06 DRAFT 평가 결과

| 검색 단계 | Recall@20 | nDCG@3 | false zero | 정답 없음 정확도 | 운영 판단 |
|---|---:|---:|---:|---:|---|
| SQL baseline | 0.7333 | 0.6671 | 0.1600 | 1.0000 | 비교 기준 |
| PostgreSQL FTS/trigram | 0.7533 | 0.6871 | 0.1400 | 1.0000 | baseline보다 개선됐지만 목표 미달 |
| FTS + DRAFT Wiki 모의 확장 | 0.7667 | 0.6848 | 0.1400 | 1.0000 | Recall은 개선됐지만 nDCG@3 하락으로 비활성화 |

목표 Recall@20 0.90, nDCG@3 0.80과 false zero 0.05 미만을 아직 충족하지 못했다.
평가 data 자체도 사람 relevance 검토 전인 DRAFT이므로 수치는 구현 단계 비교용이다.
Wiki 모의 평가는 직접 `narrower`/`synonym` 관계와 reciprocal rank fusion을 사용했으며
Product Backend의 운영 검색 경로에는 포함되지 않는다.

10,000개 합성 최신 snapshot을 적재한 로컬 PostgreSQL 16/pgvector image에서 구조화
가격/사이즈/색상 조건과 FTS/trigram 오타 검색을 5회 warm-up 후 30회 측정했다. p50은
325.129ms, p95는 336.220ms, max는 364.050ms로 1초 목표를 통과했다. 이 값은
Testcontainers 로컬 DB 검색 시간이며 판매처 수집, 네트워크와 embedding 생성 시간은
포함하지 않는다.

### 단계 0. 계약과 평가 기반

- 현재 SQL 검색 baseline 측정
- 조건 강도와 후보 응답 계약 작성
- 고정 상품 snapshot과 평가 질문 작성

### 단계 1. 전문 검색 기반 후보 회수

- PostgreSQL 전문 검색/trigram index
- `required`/`preferred` 필터
- `MATCH`/`MISMATCH`/`UNKNOWN` 옵션 판정
- 완화 조건과 검색 근거 응답

### 단계 2. 벡터 검색 결합

- embedding provider port와 실패 fallback
- 검색 문서 content hash/version
- 비동기 embedding 생성과 재생성
- 전문 검색/벡터 검색 순위 결합

### 단계 3. 검토형 Wiki Lite

- 신발 용도/상품군/색상/판매처 category seed
- source/page/relation schema
- DRAFT/PUBLISHED/SUPERSEDED와 사람 승인
- 의미 확장 API와 MCP 연결

### 단계 4. 재정렬과 설명

- 결정론적 후보 점수
- 후보별 match reason와 완화 조건
- 선택적 LLM reranker 비교와 fallback

### 단계 5. E2E와 재검증

- `/chat` 조건 강도 확인과 확장 내역 표시
- 후보 3개 비교와 근거 표시
- 선택 상품의 최신 snapshot 재검증
- embedding/Wiki/LLM 실패 경로 테스트

## 10. 완료 조건

- 동일한 고정 snapshot과 평가 질문으로 A부터 D까지 재현 가능하게 비교한다.
- 필수 조건 위반, 출처 누락과 완화 조건 비표시가 품질 기준을 충족한다.
- embedding 또는 Wiki가 실패해도 전문 검색 fallback으로 근거 있는 후보를 반환한다.
- Wiki claim은 immutable source와 검토 상태를 추적할 수 있다.
- 가격/재고/옵션은 Wiki가 아니라 최신 Collector snapshot으로 반환한다.
- 구매 직전 재검증이 추천 snapshot과 최신 snapshot의 차이를 표시한다.

## 11. 설계 참고

- [Karpathy LLM Wiki idea file](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)
- [Retrieval as Reasoning: Self-Evolving Agent-Native Retrieval via LLM-Wiki](https://arxiv.org/abs/2605.25480)
- [pgvector](https://github.com/pgvector/pgvector)

외부 설계는 방향을 참고하기 위한 자료다. 이 프로젝트의 완료 기준과 판매처 접근,
개인정보, provenance 및 사람 승인 규칙은 이 저장소 문서를 우선한다.
