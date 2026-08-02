# Collection Queue Contract v1

작성일: 2026-07-26
최종 수정일: 2026-07-30

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

현재 Go Worker와 Spring Boot 결과 Consumer 및 PostgreSQL 저장은 구현되어 있다.
Spring Boot의 작업 발행 API와 작업 상태 저장은 아직 구현되지 않았다.

## 현재 Contract 범위

- operation: `search`
- 판매처: ABC마트와 29CM
- 페이지: 계약은 `page`를 포함하지만 현재 Worker는 `page=1`만 처리
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

Product Backend는 판매처, operation, 검색 payload를 정렬한 JSON으로 만든 뒤 SHA-256을 계산할 예정이다.

```text
collection:v1:{sha256}
```

현재 메시지에는 키를 기록하지만 실제 중복 등록 차단은 Redis adapter 구현 후 활성화한다.

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
