# 현재 수집 데이터와 DB 저장 흐름

작성일: 2026-07-26
최종 수정일: 2026-08-04
대상: 프로젝트를 처음 보는 개발자

## 1. 먼저 알아야 할 현재 상태

Go Collector는 ABC마트와 29CM 검색 결과를 실제로 가져올 수 있다. Spring Boot에는
Flyway 초기 schema, CollectorResult DTO, JPA 저장 서비스와 상품 검색 API가
구현됐다. ABC마트와 29CM 실제 결과의 수동 적재도 검증했으며, RabbitMQ 결과
Consumer가 같은 저장 서비스를 호출하는 자동 저장 경로도 통합 테스트를 완료했다.
Spring Boot의 단일 페이지 및 여러 페이지 수집 작업 발행 API도 구현돼 현재 작업
생성부터 결과 저장까지의 코드 경로가 연결됐다. ABC마트 실제 Queue E2E는 검증했고,
29CM 실제 Queue E2E와 작업 상태 영구 저장은 남아 있다.

검색 상품은 JSON을 기본값으로 사용한다. `COLLECTOR-006`은 같은 상품을 화면에서
확인 가능한 HTML 또는 상세 페이지의 JSON-LD와 다시 비교한다. 상품별 검증 상태와
차이 필드는 CollectorResult에 포함되며 Spring Boot가 `product_verifications`에 저장한다.
최상위 `verificationSummary`는 일치/불일치/실패 개수를 바로 보여준다.

```text
현재 가능:
판매처 검색 → Go Collector → 공통 CollectorResult JSON
공통 CollectorResult JSON → Spring Boot 수동 적재 API → PostgreSQL 저장
수집 당시 query와 filters → requestId로 snapshot 연결 → 상품 조회
RabbitMQ CollectionResult → Spring Boot Consumer → PostgreSQL 자동 저장
Spring Boot 수집 요청 API → RabbitMQ CollectionTask 발행
연속 페이지 요청 → 페이지별 Queue 작업 → 결과별 PostgreSQL 누적 저장
JSON 기본값 ↔ HTML/JSON-LD 표시값 비교 → 상품별 검증 결과 저장

현재 미구현:
수집 작업의 PostgreSQL 상태 저장과 Redis 진행 상태
여러 검색어를 한 번에 받는 batch 작업
29CM 실제 전체 Queue E2E 검증
```

Product Backend와 Go Worker를 함께 실행하면 Swagger에서 만든 작업 결과가 자동으로
DB에 저장된다. 이전 Python 구현에서 DB 적재를 검증한 기록은
[개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md)에 과거 작업으로
남겨 두었다.

루트 Makefile은 `.env`에서 읽은 PostgreSQL과 RabbitMQ 설정을 Spring Boot 및 Go Worker
하위 프로세스에도 전달한다. 따라서 포트, 사용자 또는 비밀번호를 변경했을 때 Compose와
애플리케이션이 같은 설정을 사용한다.

## 2. 지금 수집하는 데이터

현재 검색 Collector는 작업에 지정된 검색 결과 페이지에서 다음 정보를 공통 형식으로 반환한다.

| 데이터 | ABC마트 | 29CM |
|---|---:|---:|
| 판매처 상품번호 | 수집 | 수집 |
| 상품명 | 수집 | 수집 |
| 브랜드 | 수집 | 수집 |
| 카테고리 경로 | 수집 | 수집 |
| 상품 URL | 수집 | 수집 |
| 대표 이미지 URL | 수집 | 수집 |
| 현재 표시 가격 | 수집 | 수집 |
| 상품 재고 상태 | 수집 | 수집 |
| 평점 | 검색 응답에 없음 | 수집 |
| 리뷰 수 | 수집 | 수집 |
| 검색 결과 전체 개수 `totalCount` | 수집 | 수집 |
| 다음 페이지 여부 `hasNext` | 수집 | 수집 |
| 출처 URL과 수집 시각 | 수집 | 수집 |
| JSON/HTML 교차 검증 | 렌더링 검색 HTML과 비교 | 상세 JSON-LD와 비교 |

ABC마트 검색 응답은 사이즈별 재고를 제공하므로 옵션도 만든다.

```text
상품
├── 250 / BLACK / 품절
├── 255 / BLACK / 재고 있음
└── 270 / BLACK / 재고 있음
```

29CM의 현재 검색 응답에는 상세 옵션 목록이 없어 사이즈와 색상 옵션은 비어 있다.

현재 아직 수집하지 않는 정보는 다음과 같다.

- 상품 상세 설명
- 정확한 배송비와 배송 예정일
- 상세 페이지의 전체 옵션
- 사이즈표와 실측값
- 리뷰 본문과 구매 옵션
- 리뷰 사진 존재 여부

## 3. 판매처마다 다른 필드를 어떻게 합치는가

원본 JSON 필드는 판매처마다 다르다.

```text
ABC마트: PRDT_NO, PRDT_NAME, PRDT_DC_PRICE
29CM:    itemId, productName, sellPrice
```

판매처별 Go Adapter가 이를 공통 필드로 번역한다.

```text
PRDT_NO / itemId                    → externalId
PRDT_NAME / productName             → name
PRDT_DC_PRICE / sellPrice           → price.amount
```

핵심 변환 위치는 다음과 같다.

- ABC마트: `services/collector/internal/merchants/abcmart/search.go`의 `toProduct`
- 29CM: `services/collector/internal/merchants/twentyninecm/search.go`의 `toProduct`
- 공통 결과: `services/collector/internal/collector/search.go`의 `Product`

Product Backend는 판매처 원본 필드 이름을 알 필요 없이 `CollectorResult`만 검증하고 저장하면 된다.

교차 검증은 JSON 값을 다른 값으로 덮어쓰지 않는다. `MATCHED`, `MISMATCH`,
`MISSING_IN_HTML`, `FAILED` 상태와 서로 다른 필드 목록을 별도 근거로 남긴다. 원본은
`output/raw_json/{merchant}`와 `output/raw_html/{merchant}`에 저장해 파서 변경을
재현할 수 있게 한다.

## 4. 현재 구현된 DB 저장 기반과 남은 연결

```text
Product Backend가 CollectionTask 발행
        ↓
RabbitMQ 작업 Queue
        ↓
Go Collector Worker
        ↓
RabbitMQ 결과 Queue
        ↓
Product Backend가 Java DTO와 Bean Validation으로 Contract 검증
        ↓
JPA transaction
        ↓
PostgreSQL
        ├── offer_snapshots / evidence
        └── product_verifications
```

DTO 검증, JPA transaction, RabbitMQ 단일/다중 페이지 작업 발행, 결과 Consumer와
PostgreSQL 저장은 Testcontainers 통합 테스트까지 구현됐다. 다음 작업은 실제 판매처
전체 Queue 흐름과 작업 상태를 추가하는 것이다.

### 여러 페이지는 어떻게 처리하는가

예를 들어 `startPage=1`, `pageCount=3`, `limit=50`을 요청하면 Spring Boot가
RabbitMQ 작업 3개를 만든다.

```text
사용자 요청 job-123
├── task-A / page=1 / 최대 50개
├── task-B / page=2 / 최대 50개
└── task-C / page=3 / 최대 50개
```

- `jobId`는 세 작업이 같은 사용자 요청이라는 뜻이다.
- `taskId`는 페이지 하나를 실행하는 단위라서 서로 다르다.
- Go Worker는 prefetch 1로 한 작업씩 처리한다.
- 각 결과의 `requestId`는 해당 `taskId`와 같아야 한다.
- Spring Boot는 결과 한 건마다 별도 transaction으로 DB에 저장한다.

페이지 하나가 실패하면 이미 성공한 다른 페이지의 DB 데이터는 유지된다. 현재는 작업
상태 테이블이 없어서 `jobId`별 성공 페이지와 실패 페이지를 API로 조회할 수 없다. 이는
`BACKEND-002`에서 구현할 범위다.

### Swagger에서 여러 페이지를 요청하는 예시

`POST /internal/v1/collection-tasks/pages`에 다음 JSON을 보낸다.

```json
{
  "merchant": "abcmart",
  "query": "구두",
  "startPage": 1,
  "pageCount": 3,
  "limit": 50
}
```

현재 범위는 1페이지부터 200페이지이며 페이지당 최대 50개다. 따라서 요청 한 번으로
표현할 수 있는 최대 범위는 10,000개다. 실제 검색 결과가 그보다 적으면 판매처가 빈
페이지 또는 `hasNext=false`를 반환할 수 있지만, 아직 뒤쪽 Queue 작업을 자동 취소하지는
않는다.

### 직접 실행해서 DB 저장을 확인하는 순서

Collector HTTP 서버는 이 흐름에서 필요하지 않다. RabbitMQ 작업을 읽는 Worker만 실행한다.

```text
터미널 1: make infra-up
터미널 2: make product-backend-run
터미널 3: make collector-worker
```

브라우저에서 `http://localhost:8080/swagger-ui.html`을 열고
`POST /internal/v1/collection-tasks/pages`를 실행한다. 응답이 `QUEUED`이면 작업 등록까지
성공한 것이다. Worker 터미널에서 페이지별 수집 결과가 보이고 Spring Boot 터미널에서
결과 저장 오류가 없어야 한다.

상품 조회는 다음 주소로 확인한다.

```text
http://localhost:8080/internal/v1/products?merchant=abcmart&query=구두&limit=10
```

DB 테이블의 전체 행 수는 `make db-shell`로 접속한 뒤 아래 SQL로 확인한다.

```sql
SELECT COUNT(*) FROM collection_search_contexts;
SELECT COUNT(*) FROM products;
SELECT COUNT(*) FROM merchant_products;
SELECT COUNT(*) FROM offer_snapshots;
SELECT COUNT(*) FROM product_options;
SELECT COUNT(*) FROM evidence;
```

`products`와 `merchant_products`는 같은 판매처 상품을 다시 수집하면 기존 행을 사용한다.
반면 가격과 재고 이력인 `offer_snapshots`는 재수집할 때 새 행이 추가되는 것이 정상이다.

현재 테이블 관계는 다음과 같다.

```text
collection_search_contexts
  └── request_id
        ↓ 같은 request_id
products
  └── merchant_products
        ├── offer_snapshots
        │     └── product_options
        └── evidence
```

| 테이블 | 역할 |
|---|---|
| `collection_search_contexts` | 요청별 검색어와 적용 filters를 한 번만 저장 |
| `products` | 판매처와 관계없는 최소 상품 정보 |
| `merchant_products` | `merchant + external_id`로 판매처 상품 식별 |
| `offer_snapshots` | 수집 시점의 가격과 재고 이력 |
| `product_options` | snapshot 시점의 사이즈, 색상, 옵션 재고 |
| `evidence` | 값의 출처 URL, 수집 시각, Collector 버전 |

같은 판매처 상품을 다시 수집하면 상품 행을 무한히 복제하지 않고 기존 `merchant_products`를 연결한다. 가격과 재고는 과거 값을 덮어쓰지 않고 새 snapshot으로 추가한다.

### 검색어는 왜 별도 테이블에 저장하는가

29CM에서 `구두`로 검색해도 상품명은 `BELLA SLINGBACK`처럼 영어일 수 있다. 예전
조회 API는 상품명과 브랜드만 검사했기 때문에 DB에 상품이 있어도 `query=구두`로
찾지 못했다.

이를 다음 흐름으로 해결했다.

```text
1. 사용자가 query=구두와 filters를 Collector에 전달
2. CollectorResult가 query=구두와 실제 적용 filters를 그대로 반환
3. Spring Boot가 requestId별 검색 문맥을 collection_search_contexts에 저장
4. 각 offer_snapshots가 같은 requestId를 저장
5. 상품 조회 SQL이 상품명 / 브랜드 / 수집 당시 검색어를 함께 검사
```

검색어와 filters를 상품마다 복사하지 않은 이유는 한 요청에서 상품 100개를 받아도
검색 조건은 하나이기 때문이다. `collection_search_contexts`에 한 번 저장하고
`offer_snapshots.request_id`로 연결하면 중복을 줄이면서 수집 이유를 추적할 수 있다.

같은 `requestId`를 다른 검색 조건으로 다시 사용하면 기존 snapshot의 의미가 바뀔 수
있으므로 Spring Boot가 저장을 거절한다.

## 5. Spring Boot 구현 상태

1. [완료] `application.yaml`에 PostgreSQL/RabbitMQ 환경변수 연결
2. [부분 구현] CollectorResult Java DTO와 정상/무효 예제 검증
3. [완료] Flyway 초기 schema
4. [완료] 도메인별 JPA entity와 repository
5. [완료] `merchant + externalId` upsert와 snapshot 추가 transaction
6. [완료] 저장된 최신 상품 검색 REST API
7. [완료] RabbitMQ `CollectionTask` producer와 publisher confirm
8. [완료] `CollectionResult` consumer와 계약 위반 결과 DLQ 처리
9. [완료] ABC마트와 29CM 실제 결과 수동 적재 및 PostgreSQL 행 검증
10. [완료] 수집 요청 검색어와 적용 filters를 별도 검색 문맥에 저장하고 상품 조회에 연결
11. [완료] 1부터 200까지 지정 페이지를 처리하는 Go Queue Worker
12. [완료] 같은 jobId로 연속 페이지 작업을 발행하는 Spring Boot API
13. [완료] 서로 다른 페이지 결과가 PostgreSQL에 누적되는 통합 테스트

이 항목들이 완료되고 검증 명령이 통과한 뒤에만 “DB 적재 구현 완료”로 체크한다.

## 6. 왜 snapshot으로 저장하는가

가격과 재고는 계속 바뀐다.

```text
추천 시점: 69,000원 / 270 재고 있음
구매 직전: 59,000원 / 270 품절
```

최신 값만 덮어쓰면 추천 당시 근거를 잃는다. snapshot을 추가하는 방식은 추천 당시 정보와 구매 전 재검증 결과를 비교할 수 있게 한다.

## 7. 다음 구현 순서

ABC마트는 Product Backend와 Go Worker를 직접 실행한 환경에서 구두 2페이지와 상품
6개가 PostgreSQL에 저장되는 흐름을 검증했다. 다음 작업은 같은 방식으로 29CM을
검증한 뒤 작업 상태를 PostgreSQL에 저장하고 Redis 중복 차단을 연결하는 것이다.
