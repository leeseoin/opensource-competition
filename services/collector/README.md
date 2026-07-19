# Collector

Go 기반 판매처 데이터 수집 서비스다.

현재 구현:

- ABC마트 공개 검색 결과의 상품 검색
- 상품 번호, 상품명, 브랜드, 가격, 상품 URL 수집
- 검색 결과에 공개된 사이즈와 사이즈별 재고 수집
- `POST /internal/v1/collect/search`
- `GET /internal/v1/health`
- 판매처 Registry와 지원하지 않는 판매처 상태 반환
- 무신사 공개 검색 HTML의 `__NEXT_DATA__`에서 상품 기본정보 수집
- timeout, 응답 크기 제한, A-RT 외부 redirect 차단
- ABC마트·무신사 요청 사이의 최소 1초 간격 제한
- 역할별로 분리된 단위 테스트
- `tests/integration`의 opt-in 실제 ABC마트·무신사 검색 테스트

판매처 범위:

- ABC마트: 공개 검색 수집 지원
- 무신사: 공개 검색 페이지의 서버 렌더링 초기 상품 데이터 수집 지원

아직 구현되지 않은 책임:

- 상품 상세 페이지의 가격·배송 수집
- 상품 상세 페이지의 전체 옵션·재고·사이즈표 수집
- 공개 리뷰 수집
- 동시성 상한과 retry 통제
- 로그인·CAPTCHA·접근 제한 감지
- Python Research Backend의 Collector API 호출

DB에 쓰거나 상품 추천을 수행하지 않는다.

## 현재 구조

```text
collector/
├── cmd/server/                 # Collector 실행
├── internal/
│   ├── collector/              # 공통 요청·결과 형식
│   │   ├── registry.go         # 판매처 이름과 Searcher 연결
│   ├── config/                 # 실행 설정
│   ├── merchants/abcmart/      # ABC마트 수집기
│   ├── merchants/musinsa/      # 무신사 공개 검색 Adapter
│   └── transport/http/         # HTTP API
├── testdata/abcmart/           # 저장 HTML
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
- `testdata/`: 단위 테스트용 저장 HTML
- `tests/integration/`: 실제 외부 서비스에 접속하는 통합 테스트

`internal/`에는 실행에 사용되는 실제 코드만 둔다.

실제 ABC마트 공개 검색 결과 확인은 아래 명령을 명시적으로 실행한 경우에만 동작한다.

```bash
cd services/collector
ABCMART_LIVE_SMOKE=1 go test -count=1 -run TestABC마트실제검색 -v ./tests/integration
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

상품 상세·옵션과 리뷰 수집은 아직 구현 전이다. 리뷰는 상품 단위 작업 큐와 제한된 Worker Pool로 구현할 예정이다.
