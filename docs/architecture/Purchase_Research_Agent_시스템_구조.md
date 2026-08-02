# Purchase Research Agent 시스템 구조

작성일: 2026-07-13
최종 수정일: 2026-07-30
상태: in progress

이 문서는 현재 합의된 전체 구조와 각 폴더의 책임을 설명하는 기준 문서다. 날짜별 보고서는 당시 상황을 보존하고, 구조가 바뀌면 이 문서를 먼저 갱신한다.

## 1. 제품 목표

사용자가 자연어로 구매 목적과 조건을 말하면 부족한 조건을 질문하고, 실제 판매처의 공개 상품과 리뷰를 조사해 근거가 있는 후보를 제시한다. 사용자가 상품을 선택하면 가격, 재고, 옵션, 배송 정보를 다시 확인한다.

첫 PoC의 목표는 다음과 같다.

```text
"면접용 구두 찾아줘"
→ 사이즈, 발볼, 예산 질문
→ PostgreSQL에서 조건에 맞는 상품 검색
→ 근거가 충분하면 후보 3개 비교
→ 정보가 부족하면 제한된 추가 수집
→ 선택 상품의 가격과 270 옵션 재검증
```

## 2. 전체 구조

```text
최종 사용자
  ↓
Next.js 구매 채팅 화면
  ↓ server route
Codex/Claude Code Gateway
  ↓ MCP
MCP Server
  - AI가 사용할 도구 제공
  - 입력과 출력 형식 검사
  - Product Backend REST API 호출
  ↓ REST API
Spring Boot Product Backend
  - 상품 검색과 비교용 데이터 조회
  - 수집 작업 생성과 진행 상태 관리
  - Collector 결과 검증과 정규화
  - PostgreSQL 최종 저장
  - 리뷰 신호와 재검증 결과 관리
  ├── PostgreSQL: 상품, snapshot, 옵션, 근거, 작업 상태
  ├── RabbitMQ: 수집 작업과 결과
  └── Redis: 속도 제한, 중복 방지, 짧은 진행 상태
                      ↓
                Go Collector Worker
                - 판매처별 검색과 parsing
                - timeout, retry, 차단 감지
                - 제한된 병렬 처리
                      ↓
                ABC마트/29CM
```

### 왜 MCP Server와 Product Backend를 나누는가

Spring Boot Product Backend는 일반적인 서비스 규칙과 데이터를 담당한다. 웹 화면, 관리자 기능, 향후 모바일 앱도 같은 REST API를 사용할 수 있다.

MCP Server는 Codex와 Claude Code가 이해하는 MCP 도구를 REST API 호출로 바꾸는 연결 계층이다. MCP 규격이나 AI 실행 환경이 바뀌어도 상품 저장과 수집 로직까지 함께 바꾸지 않도록 분리한다.

```text
일반 웹 기능 → Product Backend REST API
AI 도구 호출 → MCP Server → Product Backend REST API
```

MCP Server는 PostgreSQL, RabbitMQ, 외부 판매처에 직접 접근하지 않는다.

## 3. 사용자 질문과 백그라운드 수집

수집은 사용자 질문마다 판매처 전체를 다시 확인하는 방식으로 운영하지 않는다. 기본적으로 백그라운드 수집 결과를 DB에 쌓고, 사용자 질문은 DB를 먼저 조회한다.

```text
사용자 질문
  ↓
DB에서 조건 검색
  ├── 정보 충분 → 근거가 있는 후보 반환
  └── 정보 부족 → 제한된 추가 수집 요청
                    ├── 빠른 보완: 최대 100개 후보
                    └── 백그라운드 확장: 최대 1000개 후보
```

`100개`와 `1000개`는 판매처가 보장한 허용량이 아니라 시스템 안전 상한의 초기 제안이다. 실제 구현에서는 판매처별 요청 간격, 최대 페이지, 최대 상품, 요청 예산을 함께 적용한다.

## 4. 구성요소 책임

### Go Collector

- 외부 판매처에 접근하는 유일한 구성요소
- 판매처별 공개 JSON, HTML, JSON-LD 중 안정적인 데이터 소스 선택
- 검색, 상세, 옵션, 재고, 공개 리뷰 parsing
- RabbitMQ 작업 소비와 `CollectorResult` 발행
- 판매처별 요청 간격, timeout, 재시도 상한, 차단 감지
- `sourceUrl`, `collectedAt`, `collectorVersion` 포함

Go Collector는 DB에 저장하거나 최종 추천을 판단하지 않는다.

### Spring Boot Product Backend

- 브라우저와 MCP Server가 사용할 REST API 제공
- PostgreSQL 상품 검색과 조건 필터
- RabbitMQ 수집 작업 발행과 결과 소비
- Contract 검증, 판매처 공통 모델 정규화, 중복 처리
- Flyway migration과 JPA repository를 통한 최종 DB 쓰기
- 수집 작업 상태, 리뷰 신호, 상품 비교, 구매 전 재검증 관리

Product Backend는 Collector가 제공하지 않은 판매처 사실을 만들지 않는다.

### MCP Server

- `search_products`, `get_product`, `compare_products`, `verify_offer` 같은 MCP 도구 제공
- Codex/Claude Code 요청을 Product Backend REST API 요청으로 변환
- 도구 입력과 반환 데이터의 형식 검사
- 오류와 근거 부족 상태를 AI가 이해할 수 있는 형태로 반환

MCP Server는 비즈니스 데이터의 원본 저장소가 아니다.

### Next.js Web

- `/chat`에서 구매 질문과 AI 응답 표시
- 상품 비교, 가격, 옵션, 재고, 출처, 마지막 수집 시각 표시
- `/admin/collections`에서 수집 작업 생성과 진행 상태 표시
- Codex/Claude Code 실행 권한과 인증정보를 browser에 노출하지 않는 server route 제공

### Codex Plugin

- 구매 질문을 구체화하는 순서 정의
- MCP 도구 선택과 호출 순서 정의
- 공식 정보, 리뷰 기반 신호, AI 추론을 구분해 설명
- 근거 부족, 수집 실패, 오래된 정보 공개

## 5. 판매처 추가 방법

HTTP handler가 판매처 이름을 직접 분기하지 않는다. Registry가 요청의 `merchant` 값에 맞는 Searcher를 선택한다.

```text
검색 작업
  ↓
판매처 Registry
  ├── abcmart → ABC마트 Searcher
  ├── 29cm → 29CM Searcher
  └── 새 판매처 → 새 Searcher
```

새 판매처를 추가할 때는 공통 `Searcher` 인터페이스를 구현하고 Registry에 등록한다. 판매처 원본 필드가 `price`, `salePrice`, `shoe_prices`처럼 달라도 Adapter가 공통 `Product.price`로 변환한다. Product Backend와 MCP Server는 판매처 원본 이름을 알 필요가 없다.

## 6. Contract 역할

`contracts/`는 Go Collector, Product Backend, MCP Server가 같은 JSON 구조를 사용하도록 정한 서비스 사이의 규격이다. Spring Boot 기준으로 보면 서비스 사이에서 공유하는 요청과 응답 DTO 명세에 가깝다.

```text
ABC마트 PRICE / 29CM salePrice
              ↓ 판매처 Adapter
       CollectorResult.price
              ↓ Contract 검증
       Product Backend 공통 모델
              ↓
          PostgreSQL
```

| Contract | 방향 | 역할 |
|---|---|---|
| `search-request.schema.json` | Product Backend → Go | 판매처, 검색어, 조건 전달 |
| `collector-result.schema.json` | Go → Product Backend | 상품, 옵션, 리뷰, 출처, 실패 정보 |
| `collection-task.schema.json` | Product Backend → RabbitMQ → Go | 비동기 수집 작업 |
| `collection-result.schema.json` | Go → RabbitMQ → Product Backend | 비동기 수집 결과 |
| `verification-result.schema.json` | Go → Product Backend | 구매 전 최신 정보와 변경 내용 |

Contract가 바뀌면 Schema, Go DTO, Java DTO, 테스트를 같은 작업에서 갱신한다.

## 7. Repository 구조

```text
frontend/
└── purchase-web/                   # Next.js와 React

services/
├── collector/                      # Go
│   ├── cmd/server/                 # 개발용 HTTP 서버
│   ├── cmd/worker/                 # RabbitMQ Worker
│   ├── internal/merchants/         # 판매처별 Adapter
│   └── tests/                      # unit와 integration test
├── product-backend/                # Java와 Spring Boot
│   ├── src/main/java/              # collection, product, evidence 도메인별 package
│   └── src/test/java/              # unit와 integration test
└── mcp-server/                     # MCP 연결 서버

plugins/purchase-research-agent/    # Codex workflow
contracts/                          # JSON Schema와 예제
compose.yaml                        # PostgreSQL, Redis, RabbitMQ
docs/                               # 구조, 계획, 진행 기록
```

Product Backend는 업무 도메인을 먼저 찾고 그 안에서 기술 역할을 찾는 package
구조를 사용한다.

```text
com.purchasesearch.product_backend
├── collection/
│   ├── dto/                        # Collector와 Queue 계약
│   └── service/                    # 수집 결과 검증과 저장
├── product/
│   ├── controller/                 # 상품 REST API
│   ├── dto/                        # 상품 API 계약
│   ├── entity/                     # 상품, 판매처 상품, snapshot, 옵션
│   ├── repository/                 # 상품 JPA repository
│   └── service/                    # 상품 조회 use case
├── evidence/
│   ├── entity/                     # 공개 출처 근거
│   └── repository/                 # 근거 JPA repository
└── common/                         # 공통 오류와 설정
```

새 도메인이 추가되면 먼저 도메인 package를 만들고 필요한 `controller`, `dto`,
`entity`, `repository`, `service`, `exception`을 그 아래에 둔다. 도메인을 찾기
위해 여러 기술 계층을 오갈 필요가 없도록 하는 기준이다.

## 8. 현재 구현 상태

| 영역 | 상태 | 설명 |
|---|---|---|
| Go Collector | 부분 구현 | ABC마트/29CM 검색, Registry, RabbitMQ 작업 소비와 결과 발행 |
| Contracts | 초안 | Collector와 Queue v1 Schema 및 예제 |
| Product Backend | 부분 구현 | 환경설정, CollectorResult/Queue DTO, RabbitMQ 결과 Consumer, 도메인별 JPA 구성, 상품 조회 API |
| PostgreSQL 적재 | 부분 구현 | Flyway schema, 수동 적재와 RabbitMQ 결과 기반 upsert/snapshot 저장 검증 |
| MCP Server | 계획 | 디렉토리와 책임 문서만 생성 |
| Next.js Web | 초기화 | `frontend/purchase-web` 기본 scaffold |
| RabbitMQ | 부분 구현 | Go Worker와 Spring 결과 소비/DLQ는 구현됐고 Spring 작업 발행은 미구현 |
| Redis | 실행 기반 | Compose 실행은 가능하고 application adapter는 미구현 |
| Codex Plugin | 기본 구조 | manifest, MCP 설정, 구매 조사 skill 초안 |

## 9. 구현 순서

1. 수집 작업 생성 API와 RabbitMQ producer를 구현한다.
2. Product Backend부터 Go Worker와 PostgreSQL까지 실제 Queue E2E를 검증한다.
3. 수집 작업 상태를 PostgreSQL에 저장한다.
4. MCP Server가 REST API를 호출하도록 연결한다.
5. Next.js 채팅과 관리자 화면을 MCP 및 REST API에 연결한다.

세부 체크박스와 완료 조건은 [구현 계획](../planning/Purchase_Research_Agent_TODO.md)과 [개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md)에서 관리한다.
