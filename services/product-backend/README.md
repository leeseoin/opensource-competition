# Product Backend

Spring Boot 기반 상품 데이터 서버다.

## 현재 상태

- Spring Boot 4.1.0 기본 프로젝트 생성
- Java 21과 Gradle Wrapper 구성
- Spring Web MVC, Validation, JPA, Flyway, PostgreSQL, RabbitMQ, Actuator 의존성 추가
- PostgreSQL과 RabbitMQ Testcontainers 구성
- 환경변수 기반 PostgreSQL/RabbitMQ 연결
- Flyway 상품 수집 초기 schema와 도메인별 JPA entity/repository 구현
- CollectorResult DTO 검증과 동일 판매처 상품 upsert/snapshot 추가 서비스 구현
- Collector JSON을 직접 저장하는 내부 수동 적재 API 구현
- 저장된 최신 상품 검색 API 구현
- OpenAPI JSON과 Swagger UI 기반 내부 API 수동 검증
- RabbitMQ 작업 발행과 결과 consumer는 아직 구현 전

## Package 구조

기술 계층 전체를 먼저 나누지 않고 업무 도메인을 먼저 찾을 수 있도록 구성한다.

```text
com.purchasesearch.product_backend
├── collection/
│   ├── controller/                  # 수동 적재 내부 API
│   ├── dto/                         # Go Collector와 Queue 결과 계약
│   ├── exception/                   # 저장할 수 없는 결과 상태
│   └── service/                     # 수집 결과 검증과 DB 저장
├── product/
│   ├── controller/                  # 상품 REST API
│   ├── dto/                         # 상품 API 요청과 응답
│   ├── entity/                      # 상품, 판매처 상품, snapshot, 옵션
│   ├── repository/                  # 상품 JPA repository
│   └── service/                     # 상품 조회 use case
├── evidence/
│   ├── entity/                      # 공개 출처 근거
│   └── repository/                  # 근거 JPA repository
└── common/                          # 향후 공통 오류와 설정
    └── config/                      # OpenAPI 공통 정보
```

새 기능은 먼저 `product`, `collection`, `evidence`처럼 도메인을 만들고 그 안을
`controller`, `dto`, `entity`, `repository`, `service`, `exception`으로 나눈다.

## 책임

- PostgreSQL의 유일한 최종 쓰기 서버
- 상품, 가격 이력, 옵션, 리뷰 및 근거 저장
- 검색과 상품 상세 내부 REST API 제공
- 수집 작업 생성과 상태 관리
- RabbitMQ 작업 발행과 Collector 결과 소비
- Contract 검증과 transaction 관리

MCP 프로토콜 처리와 판매처 접근은 담당하지 않는다.

## 실행

루트 PostgreSQL과 RabbitMQ를 실행한 뒤 Product Backend를 시작한다. Flyway는
애플리케이션 시작 시 자동으로 migration을 적용한다.

```bash
make infra-up
make product-backend-run
```

저장된 상품 조회 예시는 다음과 같다.

```bash
curl 'http://localhost:8080/internal/v1/products?merchant=abcmart&query=로퍼&limit=10'
```

## Swagger UI

Product Backend 실행 후 browser에서 아래 주소를 연다.

```text
http://localhost:8080/swagger-ui.html
```

Swagger UI에서 수동 적재는 다음 순서로 실행한다.

1. `Collection Results` 구역을 연다.
2. `POST /internal/v1/collection-results`를 선택한다.
3. `Try it out`을 누른다.
4. `tmp/crawling-json-results/abcmart.json`의 전체 내용을 request body에 붙여 넣는다.
5. `Execute`를 누르고 응답 코드 `200`과 저장 개수를 확인한다.
6. `Products` 구역의 `GET /internal/v1/products`로 저장 결과를 조회한다.

OpenAPI JSON 원문은 다음 주소에서 확인한다.

```text
http://localhost:8080/v3/api-docs
```

운영 profile에서는 Swagger UI와 OpenAPI JSON을 기본적으로 비활성화한다. 운영에서
불가피하게 활성화할 경우 내부 접근 통제와 인증을 먼저 구성한 뒤 아래 환경변수를
사용한다.

```bash
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
```

## 실제 크롤링 결과 수동 적재

RabbitMQ 결과 consumer를 구현하기 전에는 Go Collector의 JSON 파일을 내부 API로
직접 전달해 PostgreSQL 저장 흐름을 확인한다.

먼저 Collector 결과를 저장한다.

```bash
curl -s -X POST 'http://localhost:8090/internal/v1/collect/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "requestId": "manual-abc-001",
    "merchant": "abcmart",
    "query": "구두",
    "requestedAt": "2026-07-31T17:00:00+09:00",
    "limit": 3
  }' | jq > tmp/crawling-json-results/abcmart.json
```

저장한 JSON을 Product Backend에 전달한다.

```bash
curl -s -X POST \
  'http://localhost:8080/internal/v1/collection-results' \
  -H 'Content-Type: application/json' \
  --data-binary '@tmp/crawling-json-results/abcmart.json' | jq
```

응답의 `productCount`, `snapshotCount`, `optionCount`, `evidenceCount`는 이번 요청에서
처리하거나 추가한 행 개수다. 같은 상품을 다시 전송하면 공통 상품과 판매처 상품은
중복 생성하지 않고 새로운 snapshot, 옵션과 근거 이력을 추가한다.

저장 결과는 상품 조회 API로 확인한다.

```bash
curl -s \
  'http://localhost:8080/internal/v1/products?merchant=abcmart&query=구두&limit=10' \
  | jq
```

이 API는 로컬 수동 검증을 위한 내부 경로다. 운영 환경에서는 인증과 접근 제한을
추가해야 하며, 최종 자동 적재 경로는 RabbitMQ 결과 consumer가 같은 저장 서비스를
호출하도록 구현한다.

Testcontainers 기반 검증은 다음 명령으로 실행한다.

```bash
make product-backend-test
```

운영 환경은 `SPRING_PROFILES_ACTIVE=prod`를 지정하고 PostgreSQL/RabbitMQ 환경변수를
반드시 제공한다.
