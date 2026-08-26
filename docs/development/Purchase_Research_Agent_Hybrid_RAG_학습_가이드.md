# Purchase Research Agent Hybrid RAG 학습 가이드

- 작성일: 2026-08-08
- 대상: Hybrid RAG, 임베딩과 vector 검색을 처음 구분하는 개발자
- 기준 사례: `15만 원 이하 검정 운동화 265, 운동용`

## 1. 먼저 기억할 한 문장

이 프로젝트의 검색은 한 기술이 모든 것을 판단하는 구조가 아니다.

```text
Wiki는 무엇을 검색할지 넓힌다.
FTS는 같은 단어를 찾는다.
임베딩은 문장을 숫자 좌표로 바꾼다.
pgvector 또는 FAISS는 가까운 좌표를 찾는다.
PostgreSQL은 가격/재고/사이즈 사실을 확인한다.
```

## 2. 다섯 구성요소의 역할

| 구성요소 | 입력 | 출력 | 하지 않는 일 |
|---|---|---|---|
| 검토형 LLM Wiki | `운동화` | `러닝화/워킹화`와 검토 근거 | 현재 가격/재고 생성 |
| FTS/trigram | 검색 단어 | 글자가 같거나 비슷한 상품 | 문장의 깊은 의미 판단 |
| 임베딩 모델 | 상품 문서 또는 질문 | 고정 차원의 숫자 vector | 가까운 상품 검색과 재고 필터 |
| pgvector | 질문 vector/상품 vector/SQL 조건 | PostgreSQL 안의 유사 상품 | vector 생성 |
| FAISS | 질문 vector/상품 vector index | 가까운 vector ID | 가격/재고/transaction 관리 |

## 3. 이번 실패가 발생한 이유

DB에는 다음과 같은 상품이 충분히 있었다.

```text
신발 / 스포츠 / 러닝화
신발 / 스포츠 / 워킹화
```

그러나 사용자는 `운동화`라고 입력했다. 기존 검색은 이 문자열을 그대로 상품명/category/
수집 검색어에서 찾았다. `운동화`와 `러닝화`는 글자가 다르므로 FTS만으로는 실제 상품이
있어도 0건이 될 수 있다.

이 문제는 상품 수를 늘리는 것으로 해결되지 않는다. 같은 분류의 상품을 더 수집하면
`러닝화` data만 늘고 `운동화` 원문 검색은 계속 실패할 수 있다.

## 4. 현재 실제 요청 흐름

```text
사용자 질문
  ↓
Codex 조건 구조화
  상품 종류=운동화(required)
  가격<=150000(required)
  사이즈=265(required)
  색상=검정(preferred)
  용도=운동용(preferred)
  ↓
사용자 조건 확인
  ↓
PUBLISHED Wiki 조회
  운동화 → 러닝화, confidence 0.90
  운동화 → 워킹화, confidence 0.85
  ↓
Hybrid 후보 회수
  원문 FTS/trigram
  러닝화 FTS/trigram
  워킹화 FTS/trigram
  선택적 pgvector 검색
  ↓
PostgreSQL 구조화 필터
  판매 중/150000원 이하/265 재고
  ↓
확장어별 후보 순환 병합
  ↓
Wiki 하위 개념 다양성을 보존한 상품군 최대 5개
```

2026-08-08 실제 E2E에서는 동일 질문이 0건에서 총 232건 회수와 상품군 5개 반환으로
바뀌었다. 최종 5개에는 러닝화 4개와 워킹화 1개가 포함됐고 대표 후보는 모두 BLACK/265/
15만 원 이하 조건에 일치했다.

## 5. LLM Wiki가 필요한 이유

LLM Wiki는 LLM이 매번 자유롭게 추측하는 사전이 아니다. LLM은 원본 근거에서 관계 초안을
만들 수 있지만 운영 검색은 사람이 검토한 PUBLISHED claim만 사용한다.

```text
공개 source snapshot
  ↓
LLM 또는 개발자가 DRAFT 관계 작성
  ↓
schema/source/confidence 검사
  ↓
사람 검토
  ↓
PUBLISHED
  ↓
Product Backend PostgreSQL Wiki index
```

Wiki에 넣을 값은 비교적 오래 유지되는 관계다.

```text
운동화 → 러닝화
운동화 → 워킹화
구두 → 로퍼
검정 ↔ BLACK
```

Wiki에 넣지 않을 값은 자주 바뀌는 판매처 사실이다.

```text
현재 가격
현재 재고
265 사이즈 재고
배송비
현재 상품 URL 상태
```

## 6. 임베딩 모델이 하는 일

임베딩 모델은 문장을 의미 공간의 숫자 좌표로 바꾼다.

```text
"장거리 달리기에 편한 쿠션화"
→ [0.12, -0.07, 0.81, ...]

"오래 뛰어도 충격이 적은 신발"
→ [0.15, -0.04, 0.78, ...]
```

두 문장은 단어가 정확히 같지 않아도 vector가 가까울 수 있다. BGE-M3는 이 변환을 맡을
수 있는 임베딩 모델 후보다. BGE-M3 자체가 상품 DB나 검색 index는 아니다.

## 7. pgvector와 FAISS의 차이

둘 다 이미 생성된 vector에서 가까운 결과를 찾는다.

### pgvector

PostgreSQL 확장 기능이다. 상품 fact와 vector를 같은 DB에서 조회할 수 있다.

```text
vector 유사도 상위
+ 가격 15만 원 이하
+ 265 재고
+ 판매 중
```

우리 프로젝트는 상품/가격/재고/옵션의 기준 저장소가 PostgreSQL이므로 운영 경로에는
pgvector가 자연스럽다.

### FAISS

고성능 vector 유사도 검색 library다. 대규모 고정 vector index, 로컬 실험과 GPU 검색에
강하지만 가격/재고/상품 transaction은 관리하지 않는다.

FAISS를 운영에 넣으면 별도 동기화가 필요하다.

```text
FAISS vector ID
  ↕ 동기화
PostgreSQL merchant_product ID
```

따라서 현재 결정은 다음과 같다.

```text
운영 후보 검색: PostgreSQL + pgvector
로컬 모델/검색 실험: 필요하면 FAISS 비교
```

## 8. 왜 Hybrid RAG인가

각 검색 방식은 서로 다른 실패를 보완한다.

| 방식 | 잘하는 것 | 대표 실패 |
|---|---|---|
| 구조화 SQL | 가격/재고/사이즈 정확성 | 의미 검색 불가 |
| FTS/trigram | 상품명/브랜드/오타 | 다른 개념 표현 누락 |
| Wiki | 검토된 동의어/상하위 관계 | 작성되지 않은 관계 누락 |
| vector | 표현이 다른 의미 유사성 | 필수 조건 보장 불가/설명 약함 |

그래서 한 방식으로 교체하지 않고 결과를 결합한다. 필수 조건은 항상 PostgreSQL 사실로
검사하고, Wiki/vector는 후보를 넓히는 데 사용한다.

## 9. 현재 구현과 다음 단계

현재 구현된 범위:

- PUBLISHED Wiki PostgreSQL index
- `운동화 → 러닝화/워킹화` 직접 의미 확장
- FTS/trigram과 Wiki 확장 결과 병합
- pgvector 선택적 경로와 embedding 실패 fallback
- 가격/재고/사이즈 필수 필터
- Wiki 점수/관계 근거 반환
- 하위 개념 다양성을 보존한 최대 5개 상품군

남은 범위:

- `운동용` 적합성을 판단할 공개 상세/리뷰 근거
- 구두/색상/용도 Wiki 사람 검토
- 실제 임베딩 모델 기반 offline 품질 비교
- transitive Wiki 관계와 충돌/version 전이
- Recall/nDCG/0건 반환율 기반 가중치 결정

## 10. 스스로 확인할 세 문제

1. BGE-M3가 상품 가격을 15만 원 이하로 필터링하는가?
   - 아니다. 임베딩 vector를 생성하고 가격은 PostgreSQL이 검사한다.
2. FAISS를 사용하면 임베딩 모델이 필요 없는가?
   - 아니다. FAISS는 이미 생성된 vector를 검색한다.
3. 상품을 더 수집하면 `운동화 → 러닝화` 문제가 자동으로 해결되는가?
   - 아니다. Wiki/vector/검색어 확장 중 하나가 사용자 표현과 판매처 표현을 연결해야 한다.
