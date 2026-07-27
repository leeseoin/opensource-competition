# Purchase Research Agent 시스템 구조

작성일: 2026-07-13
최종 수정일: 2026-07-26
상태: in progress

이 문서는 개발을 시작하기 전에 전체 구조와 각 폴더의 책임을 확인하는 기준 문서다. 앞으로 구조가 바뀌면 날짜별 보고서보다 이 문서를 먼저 갱신한다.

## 1. 제품 목표

사용자가 자연어로 구매 목적과 조건을 말하면 부족한 조건을 질문하고, 실제 판매처의 공개 상품·옵션·리뷰를 조사해 근거 기반 후보를 제시한다. 사용자가 후보를 선택하면 같은 상세 페이지를 다시 수집해 가격·재고·옵션·배송 변경을 검증한다.

첫 PoC 성공 시나리오는 다음과 같다.

```text
“면접용 구두 찾아줘”
→ 사이즈·발볼·예산 질문
→ 실제 판매처 한 곳에서 후보 수집
→ 상품 상세·옵션·공개 리뷰 분석
→ 근거와 주의사항이 있는 후보 3개 비교
→ 선택 상품 가격·270 옵션 재고 재검증
```

## 2. 범위

### 1차 PoC 포함

- Next.js 챗봇과 Codex Gateway를 통한 구매 조건 구체화
- ABC마트·29CM의 공개 검색과 선택 상품의 상세·옵션·공개 리뷰 수집
- 상품·offer·review signal·evidence PostgreSQL 저장
- 규칙 기반 필수 조건 필터와 설명 가능한 점수
- 공식 정보, 리뷰 집계, Agent 추론 구분
- 선택 상품의 가격·재고·옵션·배송 재검증
- Next.js 화면 연결이 가능한 FastAPI와 진행 상태 API

### 1차 PoC 제외

- 로그인 또는 CAPTCHA 우회
- 실제 결제와 주문 자동화
- 카드·계좌·배송지 정보 저장
- 범용 사이트 자동 selector 생성
- 모든 판매처 동시 지원
- 주문 추적·취소·환불

## 3. 전체 아키텍처

```text
최종 사용자
  ↓
Next.js 챗봇
  ↓ Next.js server route
Agent Gateway
  ├── Codex CLI + Purchase Research Plugin
  └── Claude Code CLI + 공통 MCP 설정
  ↓ MCP
Python Research Backend
                    - 조사 세션과 상태
                    - RabbitMQ 작업 생성·결과 소비
                    - 정규화·중복 제거
                    - 리뷰 신호 추출
                    - 비교·근거·재검증
                    - PostgreSQL repository
       ├── PostgreSQL: 상품·snapshot·근거
       ├── Redis: 진행 상태·중복 방지·속도 제한
       └── RabbitMQ: 수집 작업·결과
                      ↓
                Go Collector Worker
                    - 판매처별 검색·상세
                    - 옵션·리뷰 parsing
                    - rate limit·timeout
                    - retry·blocked 감지
  ↓
공개 판매처 페이지

장기 서비스 전환 경로:

Next.js → Codex / Claude Code / Ollama / llama.cpp / GPU 모델 서버 → 같은 MCP와 Python application use case
```

### 현재 구현 상태

| 영역 | 현재 상태 | 설명 |
|---|---|---|
| Go Collector | 부분 구현 | 판매처 Registry와 ABC마트·29CM 공개 검색 및 opt-in smoke test가 동작하고, 무신사는 검색 PoC만 유지함 |
| Contracts | 초안 작성 | 검색 요청, 수집 결과, 재검증 결과 JSON Schema와 예제가 있음 |
| Python Research Backend | 부분 구현 | HTTP·RabbitMQ 검색 요청, Pydantic 검증과 PostgreSQL 저장이 동작하며 Redis adapter·조사 세션·MCP는 미구현 |
| Codex Plugin | 뼈대만 있음 | manifest, MCP 설정, 구매 조사 skill 초안이 있음 |
| Next.js Web | 초기화 | `apps/purchase-web` Next.js scaffold가 생성됐으며 Astryx 화면과 API 연결은 미구현 |
| PostgreSQL | 부분 구현 | ABC마트·29CM 실제 상품, 가격 snapshot, 옵션과 근거 저장을 검증했으며 리뷰·조사 세션 저장은 미구현 |
| RabbitMQ | 검색 수직 흐름 구현 | durable 작업·결과 Queue, 5초 retry, DLQ와 Python producer·Go consumer·Python 저장 consumer를 ABC마트 실제 검색으로 검증 |
| Redis | 실행 기반 완료 | Docker Compose, 비밀번호, AOF volume, health check를 검증했으며 rate limiter·중복 방지·진행 상태 adapter는 미구현 |

## 4. 언어별 책임

### Go Collector

- 판매처 검색 결과와 공개 JSON/HTML 요청
- 상품 상세, 가격, 배송, 옵션, 재고, 사이즈표 parsing
- 공개 리뷰 페이지네이션과 최소 리뷰 필드 parsing
- RabbitMQ 작업 소비와 결과 발행
- 제한된 병렬 처리, Redis 기반 판매처별 공통 rate limit, timeout, retry 상한
- JavaScript가 필요한 경우에만 browser adapter 사용
- 로그인·CAPTCHA·접근 제한을 `blocked`로 반환
- `sourceUrl`, `collectedAt`, `collectorVersion`, warning을 포함한 `CollectorResult` 반환

Go는 DB에 쓰거나 상품을 추천하지 않는다.

### 판매처 추가 구조

HTTP handler가 `if merchant == "abcmart"`처럼 판매처를 직접 판단하지 않는다. 판매처 Registry가 요청의 `merchant` 값에 맞는 Searcher를 선택한다.

```text
상품 검색 요청
  ↓
판매처 Registry
  ├── 29cm → 29CM Searcher → 공개 검색 상품 응답 수집
  ├── abcmart → ABC마트 Searcher → 공개 검색 수집
  ├── musinsa → 무신사 Searcher → 검색 PoC만 유지
  └── 새 판매처 → 새 Searcher를 등록해 추가
```

새 판매처를 추가할 때는 공통 `Searcher` 인터페이스를 구현하고 Registry에 등록한다. HTTP 요청 검증, 응답 JSON 형식, Python 연결 코드는 판매처마다 다시 만들지 않는다.

### 29CM 데이터 접근 결정

2026-07-20 29CM은 일반 Agent에 공개 검색·상품 경로를 허용하고 로그인·주문·마이페이지 등 일부 경로를 제한하는 것을 확인했다. 공개 검색 화면은 서버 HTML에 상품을 직접 넣지 않고 로그인 없는 별도 상품 응답으로 목록을 구성한다.

현재 29CM Searcher는 상품번호·상품명·브랜드·표시 가격·품절 여부·카테고리·평점·리뷰 수를 공통 계약으로 변환한다. 실제 `구두` 검색 상품 3개를 반환하는 opt-in smoke test까지 확인했다. 상세 옵션, 옵션별 재고와 리뷰 본문은 아직 구현 전이다.

### 무신사 데이터 접근 결정

2026-07-19 일반 User-agent로 공개 검색 페이지를 소량 확인한 결과, 서버 렌더링 HTML의 `__NEXT_DATA__`에서 상품 기본정보를 읽을 수 있었다. 현재 PoC Searcher는 검색 요청 한 번에서 상품번호·상품명·브랜드·가격·품절 여부·평점·리뷰 수를 공통 계약으로 변환한다.

허용된 Agent 이름으로 User-agent만 변경하는 것은 무신사나 다른 서비스로 가장하는 방식이므로 사용하지 않는다. 로그인·cookie·CAPTCHA·Cloudflare 우회도 하지 않는다.

실제 무신사 데이터를 연결하려면 다음 중 하나가 필요하다.

- 무신사가 공개하거나 승인한 상품 API 또는 MCP
- 프로젝트 목적과 요청 빈도를 설명한 뒤 받은 별도 수집 허가
- 상품 사용 권한이 포함된 공식 제휴 Feed

무신사는 [ChatGPT 무신사 앱에 자체 MCP를 적용했다고 발표](https://newsroom.musinsa.com/newsroom-menu/2026-0609-01)했지만, 현재 우리 서버가 직접 사용할 수 있는 공개 MCP endpoint는 확인되지 않았다. 현재 검색 구현은 소량 PoC이며, 장기 운영 전에는 공식 MCP·API·제휴 Feed·별도 허가와 서비스 정책을 다시 확인한다.

### Python Research Backend

- Codex가 호출할 MCP server 제공
- Next.js와 장기 서비스 Agent용 FastAPI/SSE 제공
- 조사 세션과 장기 작업 상태 관리
- RabbitMQ 수집 작업 발행과 CollectorResult 소비
- 기존 단건 개발 경로용 Go Collector 내부 HTTP client
- CollectorResult schema 검증과 공통 domain model 정규화
- 상품 중복 처리와 PostgreSQL transaction
- 리뷰에서 사이즈·발볼·착화감·수축 등 `ReviewSignal` 추출
- 규칙 기반 비교와 evidence 연결
- 추천 snapshot과 최신 verification snapshot 비교

Python만 최종 DB 쓰기를 소유한다.

### Codex Plugin

- PoC에서 Next.js 질문을 받아 MCP 도구 호출 순서를 결정
- 결과를 크게 바꾸는 누락 조건부터 1~3개씩 질문
- Python MCP 도구 호출 순서 결정
- 공식 사실, 리뷰 기반 신호, Agent 추론을 구분해 설명
- 근거 부족·수집 실패·오래된 정보 공개
- 최종 후보 선택 후 `verify_offer` 호출

### Next.js Web

- 구매 조건 대화와 조건 직접 수정
- `/chat`에서 Codex 또는 Claude Code 기반 구매 질문과 응답 표시
- 상품 비교, 점수 구성, 주의사항 표시
- 주장별 출처와 수집 시각 표시
- 선택 상품 재검증 전후 차이 표시
- `/admin/collections`에서 판매처별 수집 작업 생성·중단·진행 상태 표시
- RabbitMQ Queue, 실패 작업, Redis·Worker 상태의 운영용 요약 표시

## 5. Repository 구조

```text
services/
├── collector/                         # Go
│   ├── cmd/server/
│   ├── internal/
│   │   ├── collector/                 # 수집 흐름과 worker 제한
│   │   │   └── registry.go            # 판매처 이름과 Searcher 연결
│   │   ├── config/                    # 실행 설정
│   │   ├── merchants/abcmart/         # ABC마트 구현
│   │   ├── merchants/twentyninecm/    # 29CM 구현
│   │   ├── merchants/musinsa/         # 무신사 공개 검색 Adapter
│   │   └── transport/http/            # Python용 internal API
│   ├── testdata/abcmart/               # 저장 HTML fixture
│   └── tests/                          # unit, integration
│
└── research-backend/                  # Python
    ├── src/research_backend/
    │   ├── domain/
    │   ├── application/
    │   ├── clients/collector/
    │   ├── extractors/
    │   ├── repositories/
    │   └── interfaces/
    │       ├── mcp/
    │       └── api/
    ├── migrations/
    └── tests/

apps/purchase-web/                     # Next.js + React scaffold
plugins/purchase-research-agent/       # PoC Codex workflow
contracts/collector/v1/                # Go ↔ Python JSON Schema와 예제
compose.yaml                            # PostgreSQL·Redis·RabbitMQ 로컬 인프라
docs/
├── architecture/                      # 최신 시스템 구조와 확장 설계
├── planning/                          # 구현 TODO와 제출 전 체크리스트
├── development/                       # 구현 근거, 검증, 문제 해결 기록
└── reports/                           # 날짜별 협업·업무 기록
```

상위 폴더는 다음처럼 이해하면 된다.

| 폴더 | 쉬운 설명 | 현재 상태 |
|---|---|---|
| `apps/` | 사용자 채팅과 관리자 수집 화면 | Next.js scaffold 생성, Astryx 화면 미적용 |
| `plugins/` | Codex가 구매 조사 기능을 사용하는 방법 | Plugin 뼈대만 있음 |
| `contracts/` | Go와 Python이 주고받는 데이터 규격 | v1 Schema 초안 있음 |
| `services/` | 실제 수집·분석·저장을 실행하는 서버 | Go 검색·Queue Worker와 Python HTTP·Queue DB 적재 구현 |
| `docs/` | 구조, 할 일, 구현 기록과 제출 전 확인사항을 관리하는 문서 | 계속 갱신 |

## 6. Go → Python 수집 계약

`contracts/`는 Go Collector와 Python Research Backend가 서로 다른 데이터 형식을 사용하지 않도록 정한 공통 규격이다. Spring Boot 기준으로 보면 서비스 사이에서 공유하는 요청·응답 DTO 명세와 비슷하다.

Go는 raw HTML 전체 대신 판매처별로 parsing한 JSON 결과를 반환한다. Python은 결과를 DB에 저장하기 전에 같은 JSON Schema로 검사하고 공통 domain model로 정규화한다.

### Contract v1 구성

| 파일 | 방향 | 역할 | 현재 상태 |
|---|---|---|---|
| `search-request.schema.json` | Python → Go | 판매처와 검색어, 조건 전달 | 초안 |
| `collector-result.schema.json` | Go → Python | 상품, 옵션, 리뷰, 출처, 실패 정보 반환 | 초안 |
| `verification-result.schema.json` | Go → Python | 구매 전 최신 가격·재고·옵션 변경 반환 | 초안 |
| `examples/` | 공통 | 정상·부분 성공·검증 실패 예제 | 초안 |

실제 파일과 검증 방법은 [Collector Contract v1](../../contracts/collector/v1/README.md)에서 확인한다.

### 검색 요청 예시

```json
{
  "requestId": "my-test-001",
  "merchant": "abcmart",
  "query": "구두",
  "requestedAt": "2026-07-19T10:00:00+09:00",
  "limit": 3
}
```

### 수집 결과의 기본 형태

```json
{
  "requestId": "my-test-001",
  "operation": "search",
  "status": "success",
  "merchant": "abcmart",
  "collectedAt": "2026-07-19T10:00:02+09:00",
  "collectorVersion": "abcmart-search-v1",
  "products": [],
  "warnings": [],
  "errors": []
}
```

가격·재고·옵션과 같은 판매처 사실에는 `sourceUrl`, `collectedAt`, `collectorVersion`을 포함한다. 이를 통해 어떤 페이지에서 언제 어떤 Collector 버전으로 수집했는지 확인할 수 있다.

필수 상태:

- `success`: 요청한 공개 정보 수집 성공
- `partial`: 일부 필드 또는 페이지 수집 실패
- `blocked`: 로그인·CAPTCHA·접근 제한
- `unsupported`: 현재 parser가 지원하지 않는 페이지
- `temporarily_unavailable`: timeout 또는 일시적 원격 오류

### Contract 변경 규칙

- Go 코드만 먼저 바꾸거나 Python 코드만 먼저 바꾸지 않는다.
- Schema, 예제, Go DTO, Python model과 관련 테스트를 같은 변경 단위로 갱신한다.
- 기존 v1 사용자에게 영향을 주는 필드 제거와 의미 변경은 `v2`에서 진행한다.
- 현재 Schema는 초안이므로 Python 연결과 contract test가 끝나기 전까지 확정으로 표시하지 않는다.

## 7. 내부 API 초안

### Go Collector

```http
POST /internal/v1/collect/search
POST /internal/v1/collect/product
POST /internal/v1/collect/reviews
POST /internal/v1/collect/verify
GET  /internal/v1/health
```

### Python FastAPI

```http
POST /api/v1/research-sessions
POST /api/v1/research-sessions/{id}/messages
POST /api/v1/research-sessions/{id}/search
GET  /api/v1/research-sessions/{id}
GET  /api/v1/research-sessions/{id}/events
GET  /api/v1/research-sessions/{id}/products
GET  /api/v1/products/{id}/evidence
POST /api/v1/products/{id}/verify
```

수집 진행 상태는 1차 PoC에서 SSE로 제공하고, WebSocket은 필요성이 확인될 때 도입한다.

## 8. MCP 도구

| Tool | Python 책임 | Go 호출 |
|---|---|---|
| `search_products` | PostgreSQL의 기존 상품을 조건 검색 | 없음 |
| `get_product` | 상품·판매처·최신 가격·옵션 조회 | 없음 |
| `compare_products` | 후보의 공통 비교 데이터와 evidence 연결 | 없음 |
| `verify_offer` | RabbitMQ에 우선순위 재검증 작업 등록 | Worker가 비동기로 상품·옵션 재수집 |
| `get_verification_status` | Redis 또는 DB에서 재검증 상태 반환 | 없음 |
| `get_evidence` | DB에서 주장 근거 반환 | 없음 |

MCP 응답은 최종 홍보 문장이 아니라 구조화된 사실·근거·불확실성을 제공한다.

## 9. 저장 모델

- `research_sessions`: 사용자 요청, 구조화 조건, 상태
- `products`: 판매처와 분리 가능한 상품 기본 정보
- `offers`: 판매처별 URL·가격·배송·판매자
- `product_options`: 색상·사이즈·옵션 가격·재고
- `product_measurements`: 의류·신발 실측
- `review_signals`: 발볼·사이즈·착화감 등 구조화 신호
- `snapshots`: 수집 시점별 offer/option 상태
- `evidence`: 출처 URL, 근거 유형, 수집 시각, parser/model 버전
- `recommendations`: 조건과 가중치, 점수 구성, evidence 연결
- `verification_results`: 추천 snapshot과 최신 snapshot 차이
- `collection_jobs`: 수집 작업의 목표·상태·성공·실패 집계(planned)
- `collection_tasks`: 검색 페이지·상품 상세·리뷰·재검증 단위 작업(planned)

리뷰 작성자 식별정보와 이미지 원본은 저장하지 않는다.

## 10. 실패와 보안 정책

- 도메인 allowlist 밖 URL은 Collector가 거부한다.
- private IP, localhost redirect 등 SSRF 경로를 차단한다.
- 판매처별 동시 작업과 분당 요청 수를 제한한다.
- retry는 idempotent 조회에만 제한된 횟수로 수행한다.
- MCP stdout은 protocol 전용으로 사용하고 로그는 stderr로 분리한다.
- 수집 실패를 빈 검색 결과로 위장하지 않는다.
- 오래된 snapshot은 현재 가격·재고처럼 표현하지 않는다.

## 11. 테스트 전략

- Go unit: URL 검증, parser, timeout, rate limit, 상태 매핑
- Go contract: 저장된 HTML/JSON fixture로 selector 회귀 검증
- Python unit: 정규화, 중복 제거, 점수, 최신성, 변경 비교
- Python integration: Collector stub, PostgreSQL, MCP/FastAPI 계약
- Opt-in live smoke: 실제 판매처에 낮은 빈도로 검색·상세 확인
- E2E: Codex 질문 → 실제 수집 → 비교 → 재검증

실제 판매처 smoke test는 기본 CI에서 제외한다.

## 12. 주요 결정

- Go는 외부 수집만, Python은 DB와 application 상태만 소유한다.
- 단건 개발 경로는 내부 HTTP JSON을 유지하고, 백그라운드 대량 수집은 RabbitMQ로 분리한다.
- RabbitMQ는 내구성 있는 수집 작업과 결과 전달, Redis는 속도 제한·중복 방지·짧은 진행 상태에 사용한다.
- Redis를 두 번째 작업 Queue로 사용하지 않는다.
- ABC마트와 29CM Adapter는 같은 공통 계약과 Python 저장 경로를 사용한다.
- PoC의 Codex MCP 경로와 장기 서비스 Agent API는 동일한 Python application use case를 공유한다.
