# 현재 수집 데이터 및 DB 적제 workflow

- 작성일: 2026-07-26
- 대상: 프로젝트를 처음 보는 개발자
- 범위: 현재 구현된 ABC마트 / 29CM 검색 수집과 PostgreSQL 저장

## 1. 한 문장으로 설명

현재 시스템은 ABC마트와 29CM에서 **검색 결과에 공개된 상품 정보**를 가져와 같은
형식으로 정리한 뒤, 그중 현재 DB 테이블이 지원하는 상품/가격/재고/옵션/출처를
PostgreSQL에 저장한다.

```text
ABC마트 또는 29CM 검색 결과
        ↓
Go Collector가 판매처별 JSON 해석
        ↓
우리 프로젝트의 공통 Product 형식으로 변환
        ↓
Python이 데이터 형식 검사
        ↓
PostgreSQL에 상품/가격 스냅샷/옵션/출처 저장
```

여기서 중요한 점은 다음과 같다.

- 현재는 상품 **검색 결과 1페이지**를 수집한다.
- 상품 상세 페이지와 리뷰 본문은 아직 수집하지 않는다.
- Collector가 가져온 모든 필드가 DB에 저장되는 것은 아니다.
- DB에 저장하지 않는 필드도 Collector 응답에는 포함될 수 있다.

## 2. 지금 어떤 데이터를 가져오는가

### 2.1 두 판매처에서 공통으로 가져오는 데이터

| 데이터                | 예시                        | ABC마트 | 29CM |
| --------------------- | --------------------------- | ------: | ---: |
| 판매처 상품번호       | `1010110646`                |    수집 | 수집 |
| 상품명                | `이브 웨지 3`               |    수집 | 수집 |
| 브랜드                | `에이비씨 셀렉트`           |    수집 | 수집 |
| 카테고리 경로         | `신발 > 구두 > 웨지`        |    수집 | 수집 |
| 상품 페이지 URL       | 실제 상품 주소              |    수집 | 수집 |
| 대표 이미지 URL       | 썸네일 주소                 |    수집 | 수집 |
| 현재 표시 가격        | `19000 KRW`                 |    수집 | 수집 |
| 상품 전체 재고 상태   | 판매 가능/품절              |    수집 | 수집 |
| 리뷰 수               | `11`                        |    수집 | 수집 |
| 검색 결과 전체 개수   | `1650`                      |    수집 | 수집 |
| 다음 페이지 존재 여부 | `true`                      |    수집 | 수집 |
| 수집 출처 URL         | 검색 결과 주소              |    수집 | 수집 |
| 수집 시각             | `2026-07-26T17:09:35+09:00` |    수집 | 수집 |
| Collector 버전        | `abcmart-search-v2`         |    수집 | 수집 |

### 2.2 판매처별 차이

ABC마트 검색 응답에는 사이즈별 공개 재고가 포함되어 있어 현재 검색 단계에서도
옵션을 만들 수 있다.

```text
상품: 이브 웨지 3
├── 225 / BLACK → 재고 있음
├── 230 / BLACK → 재고 있음
├── 235 / BLACK → 재고 있음
└── 250 / BLACK → 품절
```

따라서 ABC마트는 현재 다음 데이터도 수집한다.

- 사이즈
- 검색 응답의 색상 코드
- 사이즈별 재고 있음/품절
- 옵션별 가격
- 옵션 외부번호

29CM는 현재 사용하는 검색 응답에 상세 옵션 목록이 없다. 따라서 상품 기본정보,
평점과 리뷰 수는 수집하지만 사이즈/색상별 옵션은 아직 비어 있다. 옵션은 이후 상품
상세 수집 기능에서 추가해야 한다.

| 추가 데이터         |                          ABC마트 |        29CM |
| ------------------- | -------------------------------: | ----------: |
| 평점                | 검색 응답에서 제공하지 않아 없음 |        수집 |
| 사이즈 및 색상 옵션 |          검색 응답 범위에서 수집 | 아직 미수집 |
| 옵션별 재고         |          검색 응답 범위에서 수집 | 아직 미수집 |

### 2.3 아직 수집하지 않는 데이터

다음 정보는 현재 검색 Collector의 구현 범위가 아니다.

- 상품 상세 설명
- 정확한 배송비와 배송 예정일
- 상품 상세 페이지의 전체 옵션
- 사이즈표와 실측값
- 리뷰 본문
- 리뷰의 구매 옵션
- 리뷰 사진 존재 여부

Go 응답 구조에는 배송, 실측, 리뷰 자리가 미리 있지만 현재 값은 비어 있다. 빈
자리가 있다는 것이 실제 데이터를 수집했다는 뜻은 아니다.

## 3. 판매처마다 필드 이름이 다른데 어떻게 합치는가

판매처 원본 JSON의 필드 이름은 서로 다르다.

```text
ABC마트 원본
PRDT_NO, PRDT_NAME, PRDT_DC_PRICE

29CM 원본
itemId, productName, displayPrice
```

Go의 판매처별 Adapter가 이 값을 우리 프로젝트의 공통 이름으로 바꾼다.

```text
PRDT_NO      ─┐
              ├→ externalId
itemId       ─┘

PRDT_NAME    ─┐
              ├→ name
productName  ─┘

PRDT_DC_PRICE ─┐
                ├→ price.amount
displayPrice  ─┘
```

그래서 Python과 DB는 ABC마트의 `PRDT_NAME`이나 29CM의 `productName`을 직접 알
필요가 없다. 두 판매처 모두 `name`, `price`, `stockStatus` 같은 공통 형식으로
받는다.

핵심 변환 코드는 다음 위치에 있다.

- ABC마트: `services/collector/internal/merchants/abcmart/search.go`의 `toProduct`
- 29CM: `services/collector/internal/merchants/twentyninecm/search.go`의 `toProduct`
- 공통 결과: `services/collector/internal/collector/search.go`의 `Product`

## 4. 데이터는 어떤 경로로 Python까지 오는가

현재 두 가지 실행 경로가 있으며 마지막 저장 코드는 같다.

### 4.1 HTTP 개발 경로

```text
make collector-run
        ↓
make collect MERCHANT=abcmart QUERY=구두 LIMIT=3
        ↓
Python이 Go HTTP API 호출
        ↓
Python이 결과를 검증하고 DB 저장
```

이 방식은 요청과 결과를 바로 확인하기 쉬워 단건 개발과 디버깅에 사용한다.

### 4.2 RabbitMQ 백그라운드 경로

```text
Python enqueue
        ↓
RabbitMQ 검색 작업 Queue
        ↓
Go Collector Worker
        ↓
RabbitMQ 결과 Queue
        ↓
Python 결과 Worker
        ↓
PostgreSQL
```

이 방식은 앞으로 검색어와 상품 수가 많아졌을 때 Worker를 늘리기 위한 경로다.
현재는 Worker 1개와 검색 1페이지만 사용한다.

두 경로 모두 최종적으로 다음 Python 저장 코드를 사용한다.

```text
StoreCollectedSearchResult
        ↓
SqlAlchemySearchResultRepository.save
```

따라서 HTTP로 수집했는지 RabbitMQ로 수집했는지에 따라 DB 저장 형식이 달라지지
않는다.

## 5. Python은 저장 전에 무엇을 검사하는가

Python은 Go가 보낸 JSON을 바로 DB에 넣지 않는다. 먼저 Pydantic 모델로 다음을
검사한다.

- 필수 필드가 존재하는가
- 가격이 0 이상의 숫자인가
- 통화 코드가 `KRW` 같은 3자리 형식인가
- 재고 상태가 정해진 값 중 하나인가
- 수집 시각에 timezone이 있는가
- 상품과 옵션에 출처가 있는가
- 계약에 없는 알 수 없는 필드가 들어오지 않았는가

검색 결과 상태가 `success` 또는 `partial`일 때만 상품을 저장한다. `blocked`,
`unsupported`, `temporarily_unavailable` 결과는 정상 상품처럼 저장하지 않는다.

Queue 경로에서는 한 번 더 `CollectionResultEnvelope`를 검사한다. 계약이 깨진
결과 메시지는 DB에 넣지 않고 RabbitMQ 결과 DLQ로 이동한다.

## 6. PostgreSQL에는 어떻게 나눠서 저장하는가

현재 사용하는 테이블은 다섯 개다.

```text
products
  └── merchant_products
        ├── offer_snapshots
        │     └── product_options
        └── evidence
```

### 6.1 `products`

판매처와 관계없는 최소 상품 기본정보다.

| 저장 필드    | 의미                     |
| ------------ | ------------------------ |
| `id`         | DB 내부 UUID             |
| `name`       | 상품명                   |
| `brand`      | 브랜드                   |
| `created_at` | 처음 상품 행을 만든 시각 |

현재는 서로 다른 판매처의 같은 실제 상품을 자동으로 하나로 합치지 않는다.

### 6.2 `merchant_products`

특정 판매처의 상품을 구분한다.

| 저장 필드     | 의미                        |
| ------------- | --------------------------- |
| `merchant`    | `abcmart` 또는 `29cm`       |
| `external_id` | 판매처 상품번호             |
| `product_url` | 상품 페이지 URL             |
| `created_at`  | 처음 발견한 시각            |
| `updated_at`  | 마지막으로 다시 확인한 시각 |

중복 판단 기준은 다음 조합이다.

```text
merchant + external_id
```

예를 들어 `abcmart + 1010110646`은 같은 판매처 상품으로 판단한다. URL은 바뀔 수
있으므로 URL만으로 상품을 구분하지 않는다.

### 6.3 `offer_snapshots`

상품을 수집한 시점의 가격과 재고 기록이다.

| 저장 필드           | 의미                            |
| ------------------- | ------------------------------- |
| `amount`            | 수집 당시 가격                  |
| `currency`          | 통화 코드                       |
| `stock_status`      | 수집 당시 재고 상태             |
| `collected_at`      | 실제 수집 시각                  |
| `collector_version` | 사용한 Collector 버전           |
| `source_url`        | 가격 및 재고를 확인한 공개 주소 |

같은 상품을 다시 수집해도 이전 행을 수정하지 않고 새 스냅샷을 추가한다.

```text
2026-07-26 17:00 → 19,000원 / 재고 있음
2026-07-27 10:00 → 29,000원 / 재고 있음
2026-07-28 15:00 → 29,000원 / 품절
```

이 기록을 이용하면 추천 당시와 구매 직전의 가격 및 재고를 비교할 수 있다.

가격이 없는 상품은 `offer_snapshots`을 만들지 않는다. 상품과 출처는 저장할 수
있지만 가격 이력은 생성하지 않는다.

### 6.4 `product_options`

하나의 가격 스냅샷에서 확인한 옵션을 저장한다.

| 저장 필드            | 의미                    |
| -------------------- | ----------------------- |
| `external_id`        | 판매처 옵션번호         |
| `label`              | 화면에 표시할 옵션 이름 |
| `size`               | 사이즈                  |
| `color`              | 색상                    |
| `stock_status`       | 옵션별 재고 상태        |
| `amount`, `currency` | 옵션 가격               |

현재는 ABC마트 검색 결과의 사이즈 옵션이 저장된다. 29CM 검색 결과는 옵션이 없어서
이 테이블에 저장되는 옵션도 없다.

옵션은 `merchant_products`가 아니라 `offer_snapshots`에 연결된다. 옵션 재고도
시간에 따라 바뀌기 때문에 “그 시점의 옵션 상태”로 기록하기 위해서다.

### 6.5 `evidence`

상품 데이터의 출처를 별도로 기록한다.

| 저장 필드           | 의미                         |
| ------------------- | ---------------------------- |
| `evidence_type`     | 현재는 `product`             |
| `source_url`        | 데이터를 확인한 공개 주소    |
| `collected_at`      | 확인 시각                    |
| `collector_version` | 데이터를 만든 Collector 버전 |

나중에 사용자가 “이 가격과 상품 정보는 어디서 확인했어?”라고 물었을 때 이
출처를 답변 근거로 사용한다.

## 7. 상품을 다시 수집하면 어떻게 되는가

ABC마트의 같은 상품을 두 번 수집했다고 가정한다.

```text
첫 번째 수집
products           1개
merchant_products  1개
offer_snapshots    1개
evidence           1개

두 번째 수집
products           여전히 1개
merchant_products  여전히 1개
offer_snapshots    2개
evidence           2개
```

다시 수집할 때 동작은 다음과 같다.

1. `merchant + external_id`로 기존 판매처 상품을 찾는다.
2. 기존 상품이면 상품명, 브랜드와 URL을 최신 값으로 갱신한다.
3. 가격 및 재고 스냅샷은 새 행으로 추가한다.
4. 새 스냅샷에 현재 옵션들을 다시 추가한다.
5. 이번 수집 출처를 `evidence`에 추가한다.

저장 중 하나라도 실패하면 transaction 전체를 rollback한다. 상품만 저장되고 가격이
빠지는 식의 반쪽 저장을 막기 위해서다.

## 8. Collector가 가져오지만 현재 DB에는 저장하지 않는 데이터

이 부분이 현재 구현에서 가장 중요하다.

| Collector 데이터            |          현재 DB 저장 | 설명                                       |
| --------------------------- | --------------------: | ------------------------------------------ |
| 상품명 및 브랜드            |                  저장 | `products`                                 |
| 판매처/상품번호/상품 URL    |                  저장 | `merchant_products`                        |
| 가격/통화/전체 재고         |                  저장 | `offer_snapshots`                          |
| 사이즈/색상/옵션 재고       |                  저장 | `product_options`, 현재 ABC마트 중심       |
| 출처 URL/수집 시각/버전     |                  저장 | `evidence`, `offer_snapshots`              |
| 카테고리 경로               |                미저장 | DB 컬럼 추가 필요                          |
| 대표 이미지 URL             |                미저장 | 이미지 URL 테이블 또는 컬럼 필요           |
| 평점                        |                미저장 | 리뷰 요약 테이블 필요                      |
| 리뷰 수                     |                미저장 | 리뷰 요약 테이블 필요                      |
| 검색 전체 개수 `totalCount` |                미저장 | 작업 결과 및 통계 테이블 필요              |
| 다음 페이지 `hasNext`       |                미저장 | pagination 작업 상태에 사용 예정           |
| 검색어와 filters            |                미저장 | `collection_jobs`, `collection_tasks` 필요 |
| Queue `jobId`, `taskId`     |                미저장 | 작업 상태 테이블 필요                      |
| warning과 error             |                미저장 | 작업 실패 기록 테이블 필요                 |
| 배송비 및 배송 설명         | 실제 값 미수집/미저장 | 상세 수집 필요                             |
| 리뷰 본문                   | 실제 값 미수집/미저장 | 리뷰 수집 및 저장 구조 필요                |

따라서 현재 DB만 조회하면 이미지, 카테고리, 평점과 “어떤 검색어로 발견했는지”는
알 수 없다. 이 값들은 다음 DB migration에서 추가해야 한다.

## 9. 실제로 확인하는 방법

### 9.1 RabbitMQ 방식으로 수집

터미널 세 개에서 실행한다.

```text
터미널 1: make result-worker
터미널 2: make collector-worker
터미널 3: make enqueue MERCHANT=abcmart QUERY=구두 LIMIT=3
```

### 9.2 DB에 저장된 상품 확인

```bash
docker compose exec postgres sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT mp.merchant, mp.external_id, p.name
   FROM merchant_products mp
   JOIN products p ON p.id = mp.product_id;"'
```

### 9.3 최신 가격 스냅샷 확인

```bash
docker compose exec postgres sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT mp.merchant, mp.external_id, p.name,
          os.amount, os.currency, os.stock_status, os.collected_at
   FROM offer_snapshots os
   JOIN merchant_products mp ON mp.id = os.merchant_product_id
   JOIN products p ON p.id = mp.product_id
   ORDER BY os.collected_at DESC
   LIMIT 10;"'
```

## 10. 지금 상태와 다음 개발

현재 완성된 범위:

- ABC마트 및 29CM 검색 1페이지
- 판매처별 원본 JSON을 공통 Product로 변환
- HTTP 또는 RabbitMQ로 Python에 결과 전달
- Pydantic 계약 검증
- 상품/가격/재고/ABC마트 옵션/출처 저장
- 같은 판매처 상품 중복 방지와 스냅샷 이력 추가

다음 우선순위:

1. `collection_jobs`, `collection_tasks`를 추가해 검색어/작업 상태/실패를 저장한다.
2. 이미지 URL, 카테고리, 평점과 리뷰 수를 DB에 저장한다.
3. 검색 2페이지 이상을 작업 단위로 나눈다.
4. Redis로 같은 검색 조건과 페이지의 중복 등록을 막는다.
5. 상품 상세/배송/전체 옵션과 공개 리뷰를 별도 작업으로 수집한다.

현재 구조를 한 문장으로 다시 정리하면 다음과 같다.

> 검색 결과를 가져오는 부분은 구현됐고, 구매 판단에 필요한 상세 데이터와 검색 작업
> 기록을 DB에 더 저장하는 단계가 다음 개발 범위다.
