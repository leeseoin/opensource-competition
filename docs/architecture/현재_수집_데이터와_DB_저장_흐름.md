# 현재 수집 데이터와 DB 저장 흐름

작성일: 2026-07-26
최종 수정일: 2026-07-30
대상: 프로젝트를 처음 보는 개발자

## 1. 먼저 알아야 할 현재 상태

Go Collector는 ABC마트와 29CM 검색 결과를 실제로 가져올 수 있다. 그러나 기존 Python DB 적재 코드는 Spring Boot 전환 과정에서 제거됐고, Spring Boot의 Flyway/JPA 적재는 아직 구현되지 않았다.

```text
현재 가능:
판매처 검색 → Go Collector → 공통 CollectorResult JSON

현재 미구현:
CollectorResult → Spring Boot 검증 → PostgreSQL 저장
```

따라서 지금 서버를 실행해 검색 JSON을 확인할 수는 있지만, 그 결과가 자동으로 DB에 저장되지는 않는다. 이전 Python 구현에서 DB 적재를 검증한 기록은 [개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md)에 과거 작업으로 남겨 두었다.

## 2. 지금 수집하는 데이터

현재 검색 Collector는 검색 결과 1페이지에서 다음 정보를 공통 형식으로 반환한다.

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
29CM:    itemId, productName, displayPrice
```

판매처별 Go Adapter가 이를 공통 필드로 번역한다.

```text
PRDT_NO / itemId                    → externalId
PRDT_NAME / productName             → name
PRDT_DC_PRICE / displayPrice        → price.amount
```

핵심 변환 위치는 다음과 같다.

- ABC마트: `services/collector/internal/merchants/abcmart/search.go`의 `toProduct`
- 29CM: `services/collector/internal/merchants/twentyninecm/search.go`의 `toProduct`
- 공통 결과: `services/collector/internal/collector/search.go`의 `Product`

Product Backend는 판매처 원본 필드 이름을 알 필요 없이 `CollectorResult`만 검증하고 저장하면 된다.

## 4. 앞으로 구현할 DB 저장 흐름

```text
Product Backend가 CollectionTask 발행
        ↓
RabbitMQ 작업 Queue
        ↓
Go Collector Worker
        ↓
RabbitMQ 결과 Queue
        ↓
Product Backend가 Java Contract 검증
        ↓
JPA transaction
        ↓
PostgreSQL
```

예정 테이블 관계는 다음과 같다.

```text
products
  └── merchant_products
        ├── offer_snapshots
        │     └── product_options
        └── evidence
```

| 테이블 | 역할 |
|---|---|
| `products` | 판매처와 관계없는 최소 상품 정보 |
| `merchant_products` | `merchant + external_id`로 판매처 상품 식별 |
| `offer_snapshots` | 수집 시점의 가격과 재고 이력 |
| `product_options` | snapshot 시점의 사이즈, 색상, 옵션 재고 |
| `evidence` | 값의 출처 URL, 수집 시각, Collector 버전 |

같은 판매처 상품을 다시 수집하면 상품 행을 무한히 복제하지 않고 기존 `merchant_products`를 연결한다. 가격과 재고는 과거 값을 덮어쓰지 않고 새 snapshot으로 추가한다.

## 5. Spring Boot에서 구현해야 할 항목

1. `application.yaml`에 PostgreSQL/RabbitMQ 환경변수 연결
2. Collector와 Queue Contract를 표현하는 Java DTO
3. 정상/무효 JSON 예제를 사용하는 contract test
4. Flyway 초기 schema
5. JPA entity와 repository
6. `merchant + externalId` upsert와 snapshot 추가 transaction
7. RabbitMQ `CollectionTask` producer
8. `CollectionResult` consumer와 실패/DLQ 처리
9. ABC마트와 29CM 실제 결과 적재 통합 테스트

이 항목들이 완료되고 검증 명령이 통과한 뒤에만 “DB 적재 구현 완료”로 체크한다.

## 6. 왜 snapshot으로 저장하는가

가격과 재고는 계속 바뀐다.

```text
추천 시점: 69,000원 / 270 재고 있음
구매 직전: 59,000원 / 270 품절
```

최신 값만 덮어쓰면 추천 당시 근거를 잃는다. snapshot을 추가하는 방식은 추천 당시 정보와 구매 전 재검증 결과를 비교할 수 있게 한다.

## 7. 다음 구현 순서

가장 먼저 Product Backend의 환경설정과 Flyway 초기 schema를 만든다. 그다음 Java Contract와 JPA 저장을 연결하고, 마지막으로 RabbitMQ 결과 consumer를 붙인다.
