# Collection Queue Contract v1

작성일: 2026-07-26
최종 수정일: 2026-08-04

## 목적

Spring Boot Product Backend와 Go Collector Worker가 RabbitMQ에서 주고받는 작업과 결과의 공통 JSON 형식을 정의한다.

```text
Product Backend
  └─ CollectionTask 발행
          ↓ RabbitMQ
Go Collector Worker
  └─ 판매처 검색 후 CollectionResult 발행
          ↓ RabbitMQ
Product Backend Result Consumer
  └─ 계약 검증 후 PostgreSQL 저장
```

현재 Spring Boot 작업 발행 API, Go Worker, Spring Boot 결과 Consumer 및 PostgreSQL
저장은 구현되어 있다. 작업 상태의 PostgreSQL 영구 저장은 아직 구현되지 않았다.

## 현재 Contract 범위

- operation: `search`
- 판매처: ABC마트와 29CM
- 페이지: 1부터 200까지 지원하며 한 Queue 메시지는 한 페이지만 처리
- 기본 시도 횟수: 최초 실행을 포함해 최대 2회
- 임의 URL: 작업에 받지 않으며 Go 판매처 Adapter가 공개 수집 URL 생성

`maxAttempts=2`는 최초 처리 한 번과 일시 오류 재시도 한 번을 뜻한다. 로그인, 접근 차단, 지원하지 않는 판매처와 잘못된 계약은 재시도하지 않는다.

## RabbitMQ 이름

| 종류 | 이름 |
|---|---|
| exchange | `purchase-research.collection.v1` |
| dead-letter exchange | `purchase-research.collection.dlx.v1` |
| 검색 작업 Queue | `purchase-research.collection.search.v1` |
| 5초 retry Queue | `purchase-research.collection.search.retry.v1` |
| 검색 DLQ | `purchase-research.collection.search.dlq.v1` |
| 결과 Queue | `purchase-research.collection.result.v1` |
| 결과 DLQ | `purchase-research.collection.result.dlq.v1` |

## 멱등성 키

Product Backend는 판매처, operation, 검색 payload를 정렬한 JSON으로 만든 뒤 SHA-256을 계산한다.

```text
collection:v1:{sha256}
```

페이지 값도 검색 payload에 포함되므로 페이지별 멱등성 키는 서로 다르다. 현재 메시지에는
키를 기록하지만 실제 중복 등록 차단은 Redis adapter 구현 후 활성화한다.

## 여러 페이지 작업 등록

Product Backend의 `POST /internal/v1/collection-tasks/pages`는 같은 검색 조건을
페이지별 작업으로 나눈다. 각 작업은 서로 다른 `taskId`를 사용하고 전체 작업은 같은
`jobId`를 공유한다. `startPage + pageCount - 1`은 200을 넘을 수 없고 페이지당
상품 수는 최대 50개이므로 한 번의 요청은 최대 10,000개 범위를 표현할 수 있다.

RabbitMQ 발행 도중 실패하면 앞에서 확인된 작업은 이미 Queue에 남을 수 있다. 현재는
응답 오류에 접수된 작업 수를 표시하며, 작업 상태 DB와 Redis 중복 차단은 후속 구현 범위다.

## 검증

```bash
cd contracts/collection/v1

uvx check-jsonschema \
  --schemafile collection-task.schema.json \
  examples/collection-task.search.json

uvx check-jsonschema \
  --schemafile collection-result.schema.json \
  examples/collection-result.success.json \
  examples/collection-result.failed.json
```
