# Purchase Research Agent

ABC마트 상품 정보(가격·리뷰·옵션)를 카테고리 기반으로 수집하는 FastAPI 크롤링 서버입니다.

## 개요

- ABC마트 카테고리 페이지를 탐색해 상품 목록을 수집합니다.
- 수집된 상품 중 상위 N개에 대해 리뷰·옵션(사이즈/색상)을 API로 추가 수집합니다.
- 결과는 JSON 파일로 저장되며, Markdown 리포트도 자동 생성됩니다.

## 기술 스택

- **FastAPI** + uvicorn
- **Crawl4AI** (Playwright 기반 브라우저 크롤링)
- **httpx** (리뷰·옵션 API 호출)
- Python 3.12

## 실행 방법

저장소 루트에서는 다음 명령만 사용하면 됩니다.

```bash
# 최초 한 번만 실행
make python-crawler-setup

# 이후 서버 실행
make python-crawler-run
```

Queue 등록부터 PostgreSQL 저장까지 손으로 단계별 검증할 때는 별도 터미널에서
RabbitMQ Worker와 Spring Boot를 각각 실행하지 않아도 됩니다. 저장소 루트에서 다음
명령 하나를 실행합니다.

```bash
make python-crawler-swagger
```

명령이 준비 완료 주소를 출력하면 브라우저에서 `http://localhost:8012/docs`를 열고
Swagger의 번호 순서대로 실행합니다.

1. `00 준비 확인`에서 `ready: true`를 확인합니다.
2. `01 작업 등록`에서 기본 소량 요청을 실행하고 `jobId`를 복사합니다.
3. `02 진행 조회`에 `jobId`를 넣고 최종 상태와 수집 개수를 확인합니다.
4. `03 결과 조회`에서 PostgreSQL에 저장된 상품, 가격, 옵션과 출처를 확인합니다.

`01 작업 등록`은 실제 판매처 공개 페이지를 요청합니다. 기본값은 3개이며 짧은 간격으로
반복 실행하지 않습니다. 종료할 때 `Ctrl+C`를 누르면 이 명령이 시작한 Spring Boot와
Python API/Worker가 함께 종료되고 PostgreSQL/Redis/RabbitMQ container와 저장 data는
유지됩니다.

포트를 바꾸려면 다음처럼 실행합니다.

```bash
make python-crawler-run PYTHON_CRAWLER_PORT=8013
```

의존성은 Python 3.12와 `uv.lock`으로 고정합니다. 아래 명령은
`purchase-research-agent` 디렉토리에서 직접 실행할 때 사용합니다.

```bash
uv sync --frozen --python 3.12
uv run --frozen uvicorn app.main:app --host 0.0.0.0 --port 8012
```

## RabbitMQ Worker

Spring Boot가 발행하는 `CollectionTask v1` 검색 작업은 다음 명령으로 처리합니다.
Go Worker와 같은 검색 Queue를 경쟁 소비하므로 전환 검증 중에는 둘 중 하나만 실행합니다.

```bash
# 계속 실행
make python-crawler-worker

# 작업 한 건 처리 후 종료
make python-crawler-worker-once
```

Python Worker는 `purchase-research.collection.search.v1`을 소비하고 결과를
`purchase-research.collection.result.v1`에 발행합니다. prefetch 1, persistent 메시지,
publisher confirm, 5초 retry Queue와 DLQ 이름은 Go/Spring 계약과 동일합니다. 결과 발행이
확인된 뒤에만 원본 작업을 ACK하며, 발행 실패 시 원본을 requeue합니다.

검색 작업은 page 1부터 200까지 한 페이지씩 처리합니다. 가격/카테고리/사이즈/색상/재고
필터는 확인된 원본 필드로 AND 적용하고, 한 필드에 여러 값이 있으면 그중 하나의 일치를
허용합니다. 현재 `ko-KR`/`KRW`만 지원하고 의미를 보존할 수 없는 `attributes` 필터는
무시하지 않고 non-retryable 실패로 반환합니다.

판매처 HTTP/TLS 오류를 우회하지 않습니다. TLS 인증서를 기본 검증하고 3xx redirect를
자동 추적하지 않으며 401/403은 non-retryable 접근 차단으로 종료합니다. 429와 5xx만
일시 오류로 분류합니다. Queue 경고에는 요청 URL/query, 응답 body와 traceback을 넣지
않습니다. 다음 정적 검사는 위험한 설정이 다시 추가되는 것을 차단합니다.

```bash
make python-crawler-safety-check
```

실제 판매처 요청이 없는 RabbitMQ 통합 검증은 운영 vhost가 아닌 별도 테스트 vhost URL을
지정해 실행합니다.

```bash
make python-crawler-rabbitmq-test TEST_RABBITMQ_URL='amqp://사용자:암호@127.0.0.1:35672/격리_vhost'
```

## API 엔드포인트

### `POST /api/v1/collect-and-store`

Python 크롤러로 상품을 수집한 뒤 현재 Spring Boot Product Backend의
`POST /internal/v1/collection-results`를 호출해 PostgreSQL에 저장합니다.

```json
{
  "merchant": "abcmart",
  "query": "구두",
  "limit": 3,
  "detail_limit": 0
}
```

`BACKEND_BASE_URL`의 기본값은 `http://127.0.0.1:8080`입니다. Swagger UI는
`http://localhost:8012/docs`에서 사용할 수 있습니다. 이 API는 Queue를 우회해 Adapter와
DB 적재만 빠르게 확인할 때 사용합니다. 전체 경로는 위의 번호가 붙은 단계 API로
검증합니다.

성공하면 응답의 `storeResult`에 `productCount`, `snapshotCount`, `optionCount`,
`evidenceCount`, `verificationCount`가 표시됩니다. `verificationSummary`에서는
`total`, `matched`, `mismatched`, `failed`, `missingInHtml`, `missingInJson`,
`pending` 개수를 확인할 수 있습니다. 이 구조는 Go Collector와 동일하며 50개씩
나뉜 각 저장 batch에도 해당 batch의 집계가 포함됩니다. 실제 DB 조회는 Product
Backend의 다음 API를 사용합니다.

Python Adapter는 전송 직전에
`contracts/collector/v1/collector-result.schema.json`을 검사합니다. 공통 계약에
없는 상태나 필드 구조가 만들어지면 Spring Boot에 보내지 않고 오류로 중단합니다.

`limit`은 최대 500까지 입력할 수 있습니다. Spring Boot의 `CollectorResult` 한 요청은
최대 50개이므로 Python 연결 API가 자동으로 50개씩 나눠 저장합니다. 예를 들어
`limit: 500`은 최대 10개 batch로 저장되며 응답의 `batchCount`에서 실제 batch 수를
확인할 수 있습니다.

ABC마트 검색은 JSON 응답을 기본 상품값으로 사용하고, 같은 검색어와
페이지의 HTML을 Playwright 기반 browser로 렌더링해 수집한 모든 상품을
대조합니다. JSON/HTML 차이는 성공 값으로 숨기지 않고 Product Backend의
`product_verifications` 테이블에 상태와 필드별 차이로 저장합니다.

29CM은 검색 HTML에 상품 카드가 남지 않아 검색 JSON으로 수집한 모든
상품의 공개 상세 HTML을 순차적으로 요청합니다. 상세 HTML의 SEO용 Product
JSON-LD에서 상품명, 브랜드, 가격, 정상가, 이미지와 URL을 꺼내 검색
JSON과 대조합니다. 상품당 상세 요청이 하나씩 필요하므로 ABC마트보다
느립니다.

```bash
curl 'http://localhost:8080/internal/v1/products?merchant=abcmart&query=구두&limit=10'
```

### `GET /api/v1/categories`
지원하는 카테고리 목록 반환

### `POST /api/v1/category`
카테고리 기반 상품 수집

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `category` | 필수 | 카테고리 키 (예: `스니커즈_남성`) |
| `max_items` | 500 | 최대 수집 상품 수 |
| `detail_limit` | 10 | 리뷰·옵션을 수집할 상위 상품 수 (0이면 미수집) |

**응답 예시**
```json
{
  "status": "ok",
  "total_found": 40,
  "returned": 40,
  "elapsed_seconds": 35.2,
  "items": [
    {
      "title": "상품명",
      "brand": "브랜드명",
      "price": "00,000원",
      "link": "https://www.abcmart.co.kr/product?prdtNo=XXXXXXXXXX",
      "review_count": 10,
      "reviews": [
        {
          "content": "리뷰 내용 (최대 200자)",
          "score": 5,
          "date": "2026-00-00",
          "size": "000"
        }
      ],
      "options": {
        "colors": ["COLOR1", "COLOR2"],
        "sizes": ["220", "225", "230", "..."]
      }
    }
  ]
}
```

> `detail_limit` 초과 상품은 `title`, `brand`, `price`, `link`만 포함됩니다.

### `POST /api/v1/search`
키워드 기반 검색 (abcmart 전용, 최대 64건 제한)

## 지원 카테고리

| 카테고리 키 | 설명 |
|-------------|------|
| `신발_남성` / `신발_여성` / `신발_아동` | 신발 전체 |
| `스니커즈_남성` / `스니커즈_여성` | 스니커즈 |
| `스포츠_남성` / `스포츠_여성` | 스포츠화 |
| `러닝화_남성` / `러닝화_여성` | 러닝화 |
| `샌들_남성` / `샌들_여성` | 샌들 |
| `부츠_남성` / `부츠_여성` | 부츠 |
| `구두_남성` / `구두_여성` | 구두 |
| `의류_남성` / `의류_여성` | 의류 |
| `잡화_남성` / `잡화_여성` | 잡화 |

## 수집 구조

```
AbcMartCrawler          → 카테고리 페이지 크롤링 (Crawl4AI)
DetailFetcher           → 리뷰/옵션 API 호출 (httpx, 5초 딜레이)
CrawlerService          → 위 두 모듈 조합
API Endpoint (/category) → 결과 JSON + Markdown 리포트 저장
```

## 딜레이 정책

- 카테고리 페이지 간: `asyncio.sleep(1)` + Crawl4AI 브라우저 로드 (~3~4초) = 실효 4~5초
- 상품 리뷰/옵션 API 간: `asyncio.sleep(5)` (요청당)
- ABC Mart `robots.txt`: `User-agent: * / Allow: /` (전체 허용)

## 출력 파일

- `output/{site}_{category}_top{n}_{timestamp}.json` — 정제 데이터
- `output/raw/{site}_{category}_raw_{timestamp}.json` — 원본 데이터
- `output/raw_json/{merchant}/` — 판매처별 검색 JSON 원본
- `output/raw_html/{merchant}/` — 판매처별 검색 또는 상세 HTML 원본
- `logs/reports/{site}_{category}_{timestamp}.md` — 분석 리포트
- `logs/search_log.md` — 누적 요청 로그

> `output/`, `logs/`는 `.gitignore`에 등록되어 git에 포함되지 않습니다. 원본은
> 수집 결과 검증용 로컬 산출물이며 PostgreSQL에는 상품별 출처 URL과 비교 결과를
> 저장합니다.
