# Collector

Go 기반 판매처 데이터 수집 서비스다.

현재 구현:

- ABC마트 공개 검색 JSON의 상품 검색
- 상품 번호, 상품명, 브랜드, 가격, 카테고리, 리뷰 수, 상품 URL 수집
- 검색 결과에 공개된 사이즈와 사이즈별 재고 수집
- ABC마트·29CM 검색 결과의 `totalCount`, `hasNext` 수집
- ABC마트 검색 JSON과 Chrome rendering HTML의 선택 상품 전수 교차 검증
- 29CM 검색 JSON과 상품 상세 HTML Product JSON-LD의 선택 상품 전수 교차 검증
- 검증 결과 `MATCHED`/`MISMATCH`/`FAILED`와 원본 JSON/HTML 판매처별 저장
- 응답 최상위 `verificationSummary`에서 일치/불일치/실패 개수 제공
- `POST /internal/v1/collect/search`
- `GET /internal/v1/health`
- `GET /openapi.json`
- `GET /swagger-ui/`
- RabbitMQ `CollectionTask` 검색 작업 consumer와 `CollectionResult` publisher
- 5초 retry Queue와 재시도 소진·계약 오류 Dead Letter Queue
- 판매처 Registry와 지원하지 않는 판매처 상태 반환
- 29CM 공개 검색 화면의 상품 응답에서 상품 기본정보 수집
- 무신사 공개 검색 HTML의 `__NEXT_DATA__`에서 상품 기본정보 수집
- timeout, 응답 크기 제한, 판매처 외부 redirect 차단
- ABC마트·29CM·무신사 요청 사이의 최소 1초 간격 제한
- 역할별로 분리된 단위 테스트
- `tests/integration`의 opt-in 실제 ABC마트·29CM·무신사 검색 테스트
- Python/Go 비교용 최대 10,000개 pagination, 중복 제거, checkpoint와 gzip NDJSON 저장
- 운영 `CollectorResult`를 `v1-unified` 비교 계약으로 바꾸는 별도 Adapter와 validator

판매처 범위:

- ABC마트: 공개 검색 수집 지원
- 29CM: 공개 검색 상품 기본정보 수집 지원
- 무신사: 기술 PoC만 유지하고 추가 개발 보류

아직 구현되지 않은 책임:

- 상품 상세 페이지의 가격·배송 수집
- 상품 상세 페이지의 전체 옵션·재고·사이즈표 수집
- 공개 리뷰 수집
- 판매처별 동시성 상한과 Redis 전체 rate limit
- 로그인·CAPTCHA·접근 제한 감지
- Spring Boot Product Backend의 Collector API 호출

DB에 쓰거나 상품 추천을 수행하지 않는다.

Collector 실행 후 브라우저에서 `http://localhost:8090/swagger-ui/`에 접속하면 curl 없이
ABC마트와 29CM 예시 요청을 선택해 실행할 수 있다. 응답 최상위
`verificationSummary`에서 전체 일치/불일치/실패 개수를 확인하고
`products[].verification.differences`에서 상품별 차이를 확인한다. Swagger UI 정적
자원은 공식 `swagger-ui-dist` 5.32.6 CDN을 사용하므로 화면을 처음 열 때 인터넷 연결이
필요하다. API 명세 JSON은 인터넷 없이 `/openapi.json`에서 확인할 수 있다.

## 현재 구조

```text
collector/
├── cmd/server/                 # Collector 실행
├── cmd/worker/                 # RabbitMQ 검색 작업 Worker 실행
├── cmd/batch/                  # 단계별 대량 수집과 비교 결과 저장
├── internal/
│   ├── app/                    # HTTP·Worker가 공유하는 판매처 Registry 조립
│   ├── bulk/                   # pagination, 중복 제거, checkpoint와 성능 통계
│   ├── comparison/             # 운영 상품을 v1-unified 비교 상품으로 변환
│   ├── collector/              # 공통 요청·결과 형식
│   │   ├── registry.go         # 판매처 이름과 Searcher 연결
│   ├── config/                 # 실행 설정
│   ├── messaging/              # Queue 계약, retry·DLQ와 결과 발행
│   ├── merchants/abcmart/      # ABC마트 수집기
│   ├── merchants/twentyninecm/ # 29CM 공개 검색 Adapter
│   ├── merchants/musinsa/      # 무신사 검색 PoC
│   └── transport/http/         # HTTP API
├── testdata/                   # 판매처별 저장 HTML·JSON
└── tests/                      # unit, integration 테스트
```

## 검증

빠른 단위 테스트와 기본 검증은 실제 쇼핑몰에 접속하지 않는다.

```bash
cd services/collector
go test ./...
go test -race ./...
go vet ./...
```

테스트 파일 구성:

- `tests/unit/`: 저장 자료와 가짜 HTTP 응답을 사용하는 빠른 단위 테스트
- `testdata/`: 단위 테스트용 저장 HTML·JSON
- `tests/integration/`: 실제 외부 서비스에 접속하는 통합 테스트

`internal/`에는 실행에 사용되는 실제 코드만 둔다.

## 단계별 비교 수집

대량 수집 명령은 PostgreSQL이나 RabbitMQ에 직접 쓰지 않고 루트 `tmp/` 아래에
`products.ndjson.gz`, `checkpoint.json`, `summary.json`을 저장한다. 한 번에 10,000건을
시작하지 않고 100건/1,000건/최대 10,000건 순서로 실행한다.

```bash
make collector-batch \
  MERCHANT=abcmart \
  QUERY=구두 \
  MAX_ITEMS=100 \
  REQUEST_BUDGET=10
```

직접 실행하면서 여러 검색어를 사용할 때는 `-query`를 반복한다.

```bash
cd services/collector
go run ./cmd/batch \
  -merchant 29cm \
  -query 구두 \
  -query 운동화 \
  -max-items 1000 \
  -request-budget 30 \
  -output-dir ../../tmp/go-collector/29cm-1000
```

401/403/429는 즉시 중단한다. timeout/5xx처럼 일시적인 오류만 기본 1회 재시도하며,
상품 수를 맞추기 위해 중복 상품이나 확인하지 않은 필드를 만들지 않는다. 중단된 같은
작업은 동일 출력 경로와 `-resume`으로 이어서 실행할 수 있다.

## 저장 fixture parser benchmark

실제 네트워크 시간과 언어의 JSON decode/정규화 비용을 분리하기 위해 Python과 같은
`testdata` fixture를 반복 처리한다. 측정 범위는 fixture bytes의 JSON decode, 판매처별
운영 상품 변환, `v1-unified` 변환과 Contract 검증이다.

```bash
cd services/collector
go run ./cmd/parser-benchmark -merchant abcmart -iterations 1000 -warmup 100
go run ./cmd/parser-benchmark -merchant 29cm -iterations 1000 -warmup 100
```

## RabbitMQ Worker 실행

저장소 루트에서 RabbitMQ를 실행한 뒤 Worker를 시작한다.

```bash
make infra-up
make collector-worker
```

이 Worker는 HTTP 서버를 거치지 않고 `CollectionTask`를 기존 판매처 Searcher에
전달한다. 각 Queue 작업은 지정된 검색 페이지 한 건을 처리하고 prefetch와 동시 처리 개수는 1이며,
일시 오류는 5초 뒤 한 번 재시도한다.

실제 ABC마트 공개 검색 결과 확인은 아래 명령을 명시적으로 실행한 경우에만 동작한다.

```bash
cd services/collector
ABCMART_LIVE_SMOKE=1 go test -count=1 -run TestABC마트실제검색 -v ./tests/integration
```

실제 29CM 공개 검색 결과 확인도 명시적으로 실행한 경우에만 동작한다.

```bash
cd services/collector
TWENTYNINECM_LIVE_SMOKE=1 go test -count=1 -run TestTwentyNineCMActualSearch -v ./tests/integration
```

실제 무신사 검색 결과 확인도 명시적으로 실행한 경우에만 동작한다.

```bash
cd services/collector
MUSINSA_LIVE_SMOKE=1 go test -count=1 -run TestMusinsaActualSearch -v ./tests/integration
```

## 무신사 PoC 수집 범위

확인일: 2026-07-19

일반 User-agent로 공개 검색 페이지를 소량 확인한 결과, 검색 HTML의 `__NEXT_DATA__`에 초기 상품 목록이 서버 렌더링되어 있었다. 현재 Go Searcher는 이 데이터에서 상품번호, 상품명, 브랜드, 현재 가격, 품절 여부, 썸네일, 평점과 리뷰 수를 읽는다.

```text
GET https://www.musinsa.com/search/goods?keyword={검색어}&gf=A
  ↓
HTML의 __NEXT_DATA__
  ↓
props.pageProps.dehydratedState.queries[].state.data.pages[].items[]
```

2026-07-19 실제 `구두` 검색 smoke test에서 상품 3개가 공통 `Product`로 변환되는 것을 확인했다. 기본 테스트에서는 실제 판매처에 접속하지 않으며, live smoke test만 opt-in으로 실행한다.

현재 [무신사 robots.txt](https://www.musinsa.com/robots.txt)와 서비스 정책에 대한 운영 검토는 별도로 남아 있다. 따라서 현재 구현은 구조 확인과 소량 PoC 범위이며, 대량·상시 운영 수집이 승인됐다는 의미는 아니다.

따라서 다음 방식은 사용하지 않는다.

- `ChatGPT-User` 같은 허용된 이름으로 User-agent만 바꾸는 방법
- 로그인, cookie, CAPTCHA 또는 Cloudflare 접근 통제 우회
- 웹이나 앱의 비공개 내부 API를 역으로 찾아 무단 호출하는 방법

장기 서비스 운영 전 확인할 경로는 다음 세 가지다.

1. 무신사가 외부 개발자에게 공개한 상품 API 또는 MCP 사용 권한을 받는다.
2. 무신사에 본 프로젝트의 낮은 요청 빈도와 사용 목적을 설명하고 별도 수집 허가를 받는다.
3. 무신사 상품 사용 권한을 가진 제휴·상품 Feed 공급자를 사용한다.

무신사는 자체 MCP를 ChatGPT 앱에 사용한다고 공식 발표했지만, 현재 외부 개발자가 우리 서버에서 직접 호출할 수 있는 공개 MCP 주소는 확인되지 않았다. 무신사의 공개 CMS API도 회사 소식용이며 상품 검색 API가 아니다.

현재 `merchant: "musinsa"` 검색 요청은 다음과 같이 실제 상품 기본정보를 반환한다.

```json
{
  "status": "success",
  "merchant": "musinsa",
  "collectorVersion": "musinsa-search-v1",
  "products": ["검색된 상품 기본정보"],
  "errors": []
}
```

검증을 위한 상세 HTML은 수집하지만 29CM 옵션과 리뷰 본문 수집은 아직 구현 전이다. 리뷰는 상품 단위 작업 Queue와 제한된 Worker Pool로 구현할 예정이다.

## JSON/HTML 교차 검증

Go Collector는 JSON을 DB에 저장할 기본값으로 사용하고 HTML은 화면에 같은 상품 정보가 표시되는지 검증하는 데 사용한다.

- ABC마트: 검색 페이지를 headless Chrome으로 rendering한 후 상품 카드를 비교한다.
- 29CM: browser를 띄우지 않고 각 상품의 공개 상세 HTML에 있는 Product JSON-LD를 비교한다.
- 원본은 Collector 실행 기준 `output/raw_json/{merchant}`와 `output/raw_html/{merchant}`에 저장된다.
- Chrome을 자동으로 찾지 못하면 `.env`의 `COLLECTOR_CHROME_BIN`에 실행 파일 경로를 입력한다.
- 29CM은 상품별 상세 요청 사이에 최소 1초 간격을 유지한다.

응답의 `verificationSummary`를 보면 상품 상세 배열을 펼치지 않고도 검증 결과를 확인할 수 있다.

```json
{
  "verificationSummary": {
    "total": 3,
    "matched": 3,
    "mismatched": 0,
    "failed": 0,
    "missingInHtml": 0,
    "missingInJson": 0,
    "pending": 0
  }
}
```

50개 29CM 상품 검증은 최소 약 50초가 필요하므로 기본 `COLLECTOR_WRITE_TIMEOUT`과 `COLLECTOR_WORKER_TIMEOUT`은 90초다.

## 29CM 검색 수집 범위

확인일: 2026-07-20

[29CM robots.txt](https://www.29cm.co.kr/robots.txt)는 일반 Agent에 `/`를 허용하고 로그인·주문·마이페이지 등 일부 경로를 제한한다. 현재 Searcher는 허용된 공개 검색 화면이 로그인 없이 요청하는 상품 응답에서 다음 기본정보를 읽는다.

- 상품번호, 상품명, 브랜드
- 현재 표시 가격과 품절 여부
- 상품·썸네일 URL
- 대·중·소 카테고리
- 평점과 리뷰 수
- 수집 URL과 수집 시각

2026-07-20 실제 `구두` 검색 smoke test에서 상품 3개가 공통 `Product`로 변환되는 것을 확인했다. 사이즈·옵션·리뷰 본문과 DB 적재는 아직 구현 전이다.
