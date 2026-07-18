# Purchase Research Agent 시스템 구조

작성일: 2026-07-13
상태: planned

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
- 실제 판매처 한 곳의 검색·상세·옵션·공개 리뷰 수집
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
Codex Gateway
  ↓ Codex process/app-server
Codex + Purchase Research Plugin
  ↓ MCP
Python Research Backend
                    - 조사 세션과 상태
                    - Go Collector client
                    - 정규화·중복 제거
                    - 리뷰 신호 추출
                    - 비교·근거·재검증
                    - PostgreSQL repository
  ↓ internal HTTP
Go Collector
                    - 판매처별 검색·상세
                    - 옵션·리뷰 parsing
                    - rate limit·timeout
                    - retry·blocked 감지
  ↓
공개 판매처 페이지

장기 서비스 전환 경로:

Next.js → OpenAI API Agent → 같은 Python application use case
```

## 4. 언어별 책임

### Go Collector

- 판매처 검색 결과와 공개 JSON/HTML 요청
- 상품 상세, 가격, 배송, 옵션, 재고, 사이즈표 parsing
- 공개 리뷰 페이지네이션과 최소 리뷰 필드 parsing
- 제한된 병렬 처리, 판매처별 rate limit, timeout, retry 상한
- JavaScript가 필요한 경우에만 browser adapter 사용
- 로그인·CAPTCHA·접근 제한을 `blocked`로 반환
- `sourceUrl`, `collectedAt`, `collectorVersion`, warning을 포함한 `CollectorResult` 반환

Go는 DB에 쓰거나 상품을 추천하지 않는다.

### Python Research Backend

- Codex가 호출할 MCP server 제공
- Next.js와 장기 서비스 Agent용 FastAPI/SSE 제공
- 조사 세션과 장기 작업 상태 관리
- Go Collector 내부 HTTP client
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
- 판매처별 수집 진행 상태 표시
- 상품 비교, 점수 구성, 주의사항 표시
- 주장별 출처와 수집 시각 표시
- 선택 상품 재검증 전후 차이 표시

## 5. Repository 구조

```text
services/
├── collector/                         # Go
│   ├── cmd/server/
│   ├── internal/
│   │   ├── collector/                 # 수집 흐름과 worker 제한
│   │   ├── config/                    # 실행 설정
│   │   ├── merchants/abcmart/         # ABC마트 구현
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

apps/purchase-web/                     # Next.js + React(planned)
plugins/purchase-research-agent/       # PoC Codex workflow
```

## 6. Go → Python 수집 계약

Go는 raw HTML 전체 대신 판매처별로 parsing한 transport DTO를 반환한다. Python은 이를 공통 domain model로 검증·정규화한다.

```json
{
  "status": "success",
  "merchant": "abcmart",
  "sourceUrl": "https://abcmart.a-rt.com/product?prdtNo=1010110882",
  "collectedAt": "2026-07-16T14:30:00+09:00",
  "collectorVersion": "abcmart-search-v1",
  "product": {
    "externalId": "1010110882",
    "name": "페니 로퍼",
    "price": 69000
  },
  "options": [],
  "reviews": [],
  "warnings": []
}
```

필수 상태:

- `success`: 요청한 공개 정보 수집 성공
- `partial`: 일부 필드 또는 페이지 수집 실패
- `blocked`: 로그인·CAPTCHA·접근 제한
- `unsupported`: 현재 parser가 지원하지 않는 페이지
- `temporarily_unavailable`: timeout 또는 일시적 원격 오류

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
| `search_products` | 세션 생성, 조건 검증, 후보 저장 | 검색·기본 상세 수집 |
| `collect_product` | 정규화, snapshot 저장 | 상세·옵션 수집 |
| `collect_reviews` | review signal 추출·저장 | 공개 리뷰 수집 |
| `compare_products` | 필터·점수·evidence 연결 | 없음 |
| `verify_offer` | 과거 snapshot과 최신 값 비교 | 상품·옵션 재수집 |
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
- Go와 Python은 1차 PoC에서 내부 HTTP JSON으로 통신한다.
- 메시지 큐와 gRPC는 초기 범위에서 제외한다.
- 첫 판매처 하나를 end-to-end로 완성한 뒤 Adapter를 확장한다.
- PoC의 Codex MCP 경로와 장기 서비스 Agent API는 동일한 Python application use case를 공유한다.
