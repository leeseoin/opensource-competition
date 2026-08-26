# dev-jw Python 크롤러 가져오기와 차이 분석

작성일: 2026-08-04

## 1. 결론

정우님이 `origin/dev-jw`에서 개발한 Python 크롤링 코드와 ABC마트 Contract를
`sandbox-python-crawler/ls`에 원본 그대로 가져왔다.

가져온 기준 commit은 `origin/dev-jw@e2d863c`이다. 가져온 파일은 원격 브랜치의 같은
파일과 byte 단위로 동일하다. 따라서 앞으로 수정하기 전의 기준 구현으로 사용할 수 있다.

다만 기존 `services/python-collector`는 정우님 코드의 단순 확장판이 아니다. ABC마트
데이터를 가져오는 방법부터 서로 다르다. 두 구현을 섞어서 성능을 측정하면 Python과 Go의
성능 차이가 아니라 브라우저 방식과 JSON 방식의 차이를 함께 측정하게 된다.

## 2. 이번에 가져온 범위

### Python 크롤러

- `purchase-research-agent/app/crawlers/base.py`
- `purchase-research-agent/app/crawlers/abcmart/`
- `purchase-research-agent/app/crawlers/cm29/`
- `purchase-research-agent/app/services/crawler_service.py`
- `purchase-research-agent/app/services/contract_validation.py`
- `purchase-research-agent/app/api/`
- `purchase-research-agent/app/main.py`
- 실행 스크립트와 Python 의존성 파일

### Contract

- `contracts/collector/v1-abcmart/abcmart-crawl-item.schema.json`
- `contracts/collector/v1-abcmart/product-batch-request.schema.json`
- 정상 예제와 실패 예제
- Contract 설명 문서

정우님 브랜치의 Spring Boot/MySQL 코드와 현재 프로젝트 구조에 관계없는 파일은 가져오지
않았다.

## 3. 구현 방식 비교

| 구분 | 정우님 원본 `purchase-research-agent` | 기존 `services/python-collector` |
|---|---|---|
| ABC마트 목록 수집 | Crawl4AI/Playwright로 화면을 열고 HTML 파싱 | 공개 검색 JSON 응답을 HTTPX로 요청 |
| ABC마트 파싱 입력 | 렌더링된 HTML | `result-total/list` JSON |
| 29CM 목록 수집 | HTTPX로 JSON 요청 | HTTPX로 JSON 요청 |
| 페이지 간격 | 코드에 1초 고정 | 실행 설정으로 조절 |
| 중복 제거 | 실행 중 메모리 `set` | 실행 중 `set`과 checkpoint |
| 중단 후 재개 | 없음 | checkpoint로 지원 |
| 결과 저장 | 일반 JSON과 원본 HTML | gzip NDJSON과 요약 JSON |
| 요청 예산/시간 상한 | 없음 | 지원 |
| 401/403/429 중단 | 별도 정책 없음 | 즉시 중단 |
| Contract | ABC마트 `v1-abcmart`만 실행 중 검사 | ABC마트/29CM 모두 `v1-unified` 검사 |
| 주요 목적 | 실제 화면과 상세 정보 확인용 PoC | 대량 수집과 Python/Go 비교 실험 |

## 4. 실제 코드 흐름

### 정우님 ABC마트

1. `AbcMartCrawler.crawl()`이 검색 페이지를 브라우저로 연다.
2. 렌더링된 HTML을 파일로 저장한다.
3. BeautifulSoup이 상품 카드의 CSS selector를 찾아 상품을 만든다.
4. 상품 번호로 중복을 제거한다.
5. 필요하면 `DetailFetcher`가 옵션과 리뷰를 추가한다.
6. `CrawlerService`가 ABC마트 결과만 `v1-abcmart` Contract로 검사한다.

근거 코드는 `purchase-research-agent/app/crawlers/abcmart/crawler.py`의
`AbcMartCrawler.crawl`과 `AbcMartCrawler._parse`,
`purchase-research-agent/app/services/crawler_service.py`의
`CrawlerService.search_items`다.

### 정우님 29CM

1. `Cm29Crawler.crawl()`이 29CM 목록 JSON 주소에 검색 조건을 POST한다.
2. 응답의 `data.list`를 상품 목록으로 변환한다.
3. `pagination.hasNext`가 참이면 다음 페이지를 요청한다.
4. 상품 ID로 중복을 제거한다.

근거 코드는 `purchase-research-agent/app/crawlers/cm29/crawler.py`의
`Cm29Crawler.crawl`과 `Cm29Crawler._parse`다.

## 5. Contract 차이

`v1-abcmart`는 정우님 Python ABC마트 크롤러와 당시 Spring Boot/MySQL 적재 요청을 위한
계약이다. 가격은 `"89,000원"` 같은 문자열이며 필드 이름도 정우님 Python 결과를 따른다.

`v1-unified`는 Python과 Go의 비교를 위해 만든 계약이다. ABC마트와 29CM를 같은 필드로
비교하기 위해 이미지 목록, 평점, 리뷰 수, 카테고리, 재고, 옵션과 리뷰 필드를 추가로
요구한다.

두 Contract는 목적이 다르므로 지금 바로 하나를 삭제하거나 덮어쓰지 않는다. 먼저
정우님 원본 결과를 보존하고, 별도의 변환 Adapter가 `v1-unified` 결과를 만들게 해야 한다.

## 6. 공정한 성능 비교 방법

성능 비교는 두 단계로 나눈다.

1. 원본 재현: 정우님 `purchase-research-agent`를 수정하지 않고 소량 실행해 실제 동작을
   확인한다.
2. 동일 조건 비교: Python과 Go가 같은 판매처 JSON fixture 및 같은 `v1-unified`
   Contract를 처리하도록 맞춘 뒤 parser/정규화 성능을 비교한다.

실제 판매처 E2E 비교에서는 ABC마트 Python 원본만 브라우저를 사용하므로 결과를
"Python 언어가 느리다"라고 해석하면 안 된다. 브라우저 실행 비용이 포함됐다고 따로
표시해야 한다.

## 7. 다음 작업

- [ ] 정우님 원본 Python 크롤러를 3건에서 10건으로 소량 수동 실행한다.
- [x] 정우님 원본 Python 결과를 현재 Spring Boot `CollectorResult`로 변환해 수동 DB 적재 API로 연결한다.
- [ ] ABC마트/29CM 원본 결과를 공통 `v1-unified`로 바꾸는 Python Adapter를 만든다.
- [ ] 같은 저장 fixture를 처리하는 Go Adapter를 만든다.
- [ ] 두 언어에 같은 입력/반복 횟수/Contract 검사를 적용한다.
- [ ] 소량 검증 후 100개/1,000개/최대 10,000개 순서로 확대한다.

## 8. 이번 검증 결과

- `python3 -m compileall -q purchase-research-agent/app purchase-research-agent/scripts`: 통과
- ABC마트 정상 crawl 예제와 `abcmart-crawl-item.schema.json`: 통과
- 정상 batch 예제와 `product-batch-request.schema.json`: 통과
- 가져온 파일과 `origin/dev-jw@e2d863c`의 같은 경로 파일 hash 비교: 모두 동일

실제 사이트 요청은 이번 비교 단계에서 실행하지 않았다.
