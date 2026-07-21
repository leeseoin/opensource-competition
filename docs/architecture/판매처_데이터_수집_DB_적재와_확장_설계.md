# 판매처 데이터 수집, DB 적재와 확장 설계

- 작성일: 2026-07-19
- 문서 상태: 현재 구현과 다음 개발 계획
- 대상: 프로젝트 개발자와 협업자

## 1. 이 문서에서 답하려는 질문

이 문서는 다음 질문을 쉽게 설명하기 위해 작성했다.

1. ABC마트와 무신사에서 현재 데이터를 어떻게 가져오는가?
2. 실제 Go 코드에서는 어떤 순서로 처리하는가?
3. 지금 가져올 수 있는 데이터와 아직 못 가져오는 데이터는 무엇인가?
4. 데이터 양을 늘리려면 무엇을 늘려야 하는가?
5. 고루틴 worker를 몇 개 사용해야 하는가?
6. 수집한 데이터는 PostgreSQL에 어떻게 저장할 것인가?
7. 새로운 쇼핑몰을 추가하려면 어떤 코드를 만들면 되는가?
8. 쇼핑몰 화면이나 JSON 구조가 바뀌면 어떻게 알아차릴 것인가?

## 2. 가장 간단한 전체 설명

Go Collector는 쇼핑몰에서 받은 HTML이나 JSON을 우리 프로젝트의 공통 상품 형식으로 번역한다.

Python Research Backend는 번역된 결과를 검사하고 PostgreSQL에 저장한다.

```text
사용자가 "구두 찾아줘"라고 요청
        ↓
Python Research Backend가 조사 작업을 만듦
        ↓
Go Collector에 "ABC마트와 무신사에서 구두 검색" 요청
        ↓
Go Collector가 각 쇼핑몰의 HTML 또는 JSON을 읽음
        ↓
두 쇼핑몰 결과를 같은 Product 형식으로 변환
        ↓
Python이 데이터 형식을 검사하고 중복을 정리
        ↓
PostgreSQL에 상품, 가격, 옵션, 리뷰, 출처를 저장
        ↓
Python이 조건에 맞는 상품을 비교
```

쉽게 비유하면 다음과 같다.

```text
ABC마트·무신사     = 서로 다른 언어를 사용하는 판매처
Go Collector       = 판매처별 통역사
Contracts          = 통역 결과를 적는 공통 양식
Python Backend     = 조사 관리자
PostgreSQL         = 조사 기록 보관소
```

## 3. 사용자가 검색했을 때 코드가 움직이는 순서

사용자는 Collector에 직접 요청하지 않는다. 장기 구조에서는 Python Backend가 Collector를 호출한다. 현재는 개발 테스트를 위해 `curl`로 Collector를 직접 호출할 수 있다.

```text
POST /internal/v1/collect/search
        ↓
searchHandler가 JSON 요청 검사
        ↓
SearchRegistry가 merchant 확인
        ├── merchant=abcmart → ABC마트 Searcher
        └── merchant=musinsa → 무신사 Searcher
        ↓
판매처별 HTML 또는 JSON 수집
        ↓
공통 SearchResult 반환
```

현재 코드 위치는 다음과 같다.

| 역할 | 파일과 위치 | 설명 |
|---|---|---|
| HTTP 요청 검사 | `services/collector/internal/transport/http/search.go:38` | JSON을 읽고 기본값과 형식을 검사한다. |
| 판매처 선택 | `services/collector/internal/collector/registry.go:34` | `merchant` 값에 맞는 Searcher를 고른다. |
| ABC마트 요청 | `services/collector/internal/merchants/abcmart/search.go:79` | ABC마트 검색 페이지를 요청한다. |
| ABC마트 JSON 해석 | `services/collector/internal/merchants/abcmart/search.go` `Searcher.Search` | `result-total/list`의 상품과 페이지 정보를 읽는다. |
| ABC마트 공통 변환 | `services/collector/internal/merchants/abcmart/search.go` `toProduct` | ABC마트 상품을 공통 `Product`로 바꾼다. |
| 무신사 요청 | `services/collector/internal/merchants/musinsa/search.go:93` | 무신사 검색 페이지를 요청한다. |
| 무신사 JSON 해석 | `services/collector/internal/merchants/musinsa/search.go:220` | HTML의 `__NEXT_DATA__` JSON을 읽는다. |
| 무신사 공통 변환 | `services/collector/internal/merchants/musinsa/search.go:250` | 무신사 상품을 공통 `Product`로 바꾼다. |

줄 번호는 2026-07-19 기준이며 코드가 추가되면 달라질 수 있다. 함수 이름을 함께 기록하는 이유는 줄 번호가 바뀌어도 위치를 찾기 위해서다.

## 4. ABC마트 데이터 수집 방식

> 2026-07-20 공개 검색 화면의 `/display/search-word/result-total/list` JSON을 확인한 뒤 기존 HTML 파서를 JSON 방식으로 교체했다. 공통 필드 기준은 [판매처 공통 수집 데이터 명세](판매처_공통_수집_데이터_명세.md)를 따른다.

### 4.1 한 문장 설명

ABC마트는 공개 검색 화면이 요청하는 JSON에 상품 정보, 사이즈별 재고와 페이지 정보가 들어 있으므로 Go가 JSON 필드를 읽는다.

### 4.2 실제 흐름

```text
검색어: 구두
        ↓
ABC마트 검색 URL 생성
        ↓
JSON 한 번 요청
        ↓
SEARCH 배열의 상품 순회
        ↓
상품번호·브랜드·상품명·가격·카테고리·이미지 추출
        ↓
사이즈와 공개 재고 수량 추출
        ↓
SEARCH_COUNT와 PAGE에서 totalCount·hasNext 계산
        ↓
공통 Product로 변환
```

JSON은 대략 다음과 같은 형태다.

```json
{
  "SEARCH": [
    {
      "PRDT_NO": "1010110882",
      "PRDT_NAME": "페니 로퍼",
      "PRDT_DC_PRICE": "69000",
      "SIZE_LIST": {"250": "0", "270": "10"}
    }
  ],
  "SEARCH_COUNT": 1650,
  "PAGE": {"finalPageNo": 55}
}
```

현재 ABC마트에서 가져오는 데이터:

- 상품번호
- 상품명
- 브랜드
- 가격
- 상품 URL
- 대표 이미지 URL
- 카테고리
- 리뷰 개수
- 판매 중 또는 품절 상태
- 신발 사이즈
- 사이즈별 공개 재고 유무
- 전체 검색 결과 수와 다음 페이지 여부
- 수집 URL과 수집 시각

현재 가져오지 못하는 데이터:

- 상세 배송 정보
- 검색 JSON에 없는 추가 옵션
- 전체 사이즈표와 실측
- 공개 리뷰
- 상품 상세 설명

### 4.3 현재 요청량

ABC마트 Searcher는 검색 결과를 최소 30개 요청한 뒤 사용자의 가격·재고·사이즈 조건을 적용하고 최종 `limit`만큼 반환한다.

검색 한 번으로 여러 상품을 얻기 때문에 상품 30개를 얻기 위해 30번 요청하는 구조가 아니다.

```text
ABC마트 검색 요청 1회
        ↓
상품 후보 최소 30개가 포함된 JSON
        ↓
조건 필터
        ↓
최종 3개, 10개 또는 최대 50개 반환
```

## 5. 무신사 데이터 수집 방식

### 5.1 한 문장 설명

무신사는 SPA이지만 첫 검색 결과를 HTML의 `__NEXT_DATA__` JSON에 함께 넣어 주므로, 현재 Go 코드는 브라우저를 실행하지 않고 그 JSON을 읽는다.

### 5.2 실제 검색 흐름

```text
검색어: 구두
        ↓
GET /search/goods?keyword=구두&gf=A
        ↓
무신사 검색 HTML 수신
        ↓
<script id="__NEXT_DATA__"> 찾기
        ↓
JSON 안의 상품 items[] 찾기
        ↓
공통 Product로 변환
```

JSON의 현재 경로는 다음과 같다.

```text
props
  .pageProps
  .dehydratedState
  .queries[]
  .state
  .data
  .pages[]
  .items[]
```

상품 하나는 대략 다음처럼 들어 있다.

```json
{
  "goodsNo": 5877464,
  "goodsName": "보그스 스퀘어토 더비슈즈",
  "goodsLinkUrl": "https://www.musinsa.com/products/5877464",
  "thumbnail": "https://image.msscdn.net/...jpg",
  "isSoldOut": false,
  "price": 53800,
  "brandName": "더노피",
  "reviewCount": 64,
  "reviewScore": 94
}
```

현재 무신사 검색에서 가져오는 데이터:

- 상품번호
- 상품명
- 브랜드
- 현재 가격
- 상품 URL
- 썸네일 URL
- 품절 여부
- 평점
- 리뷰 개수
- 수집 URL과 수집 시각

현재 가져오지 못하는 데이터:

- 상품 상세 설명
- 실제 옵션별 재고
- 색상과 사이즈 조합
- 배송 정보
- 리뷰 본문을 Collector 결과에 연결하는 기능

### 5.3 실제로 확인한 데이터 양

2026-07-19 `구두` 검색 한 번에서 확인한 내용:

- HTML 안의 초기 상품 항목: 약 60개
- 검색 결과가 표시한 전체 개수: 15,850개
- 현재 Collector 계약의 최대 반환 개수: 50개
- 실제 Go smoke test 반환 개수: 3개

현재는 첫 검색 응답만 사용한다. 검색 결과 전체 15,850개를 모두 가져오는 구현은 아니다.

### 5.4 무신사 리뷰 상태

다음 리뷰 JSON 요청이 상품번호 `5119569`에 대해 정상 응답하는 것을 소량 확인했다.

```text
GET https://goods.musinsa.com/api2/review/v1/view/list
    ?goodsNo=5119569
    &page=0
    &pageSize=10
```

확인한 응답:

- 첫 페이지 리뷰 10개
- 전체 리뷰 405개
- 전체 41페이지
- 리뷰 내용, 평점, 구매 옵션, 작성 시각, 사진, 만족도 설문 포함

아직 이 리뷰 응답을 Go `Review` 구조로 변환하거나 HTTP API에 연결하지 않았다.

응답에 포함된 다음 정보는 저장하지 않는다.

- 닉네임
- 사용자 ID 또는 암호화된 사용자 ID
- 프로필 이미지
- 리뷰 작성자의 개인 프로필
- 리뷰 이미지 원본

리뷰에서는 다음 정보만 남긴다.

- 리뷰 외부 번호
- 리뷰 본문
- 평점
- 구매 옵션
- 작성 시각
- 사진 존재 여부 `hasImage`
- 사이즈·색감·두께감 등의 공개 설문 신호
- 출처 URL과 수집 시각

## 6. 현재 상태를 한눈에 보기

| 기능 | ABC마트 | 무신사 |
|---|---|---|
| 검색어로 상품 찾기 | 완료 | 완료 |
| 상품명·브랜드 | 완료 | 완료 |
| 가격 | 완료 | 완료 |
| 대표 이미지 URL | 완료 | 완료 |
| 검색 단계 품절 여부 | 완료 | 완료 |
| 사이즈·옵션 | 검색 JSON 범위에서 부분 완료 | 미구현 |
| 옵션별 재고 | 검색 JSON 범위에서 부분 완료 | 미구현 |
| 평점·리뷰 수 | 리뷰 수 완료, 평점 미구현 | 검색 결과에서 완료 |
| 리뷰 본문 | 미구현 | 응답 구조만 확인 |
| 상품 상세 | 미구현 | 미구현 |
| 고루틴 Worker Pool | 미구현 | 미구현 |
| PostgreSQL 적재 | 미구현 | 미구현 |
| opt-in 실제 검색 테스트 | 완료 | 완료 |

## 7. 데이터 양을 늘리는 방법

데이터 양은 worker 개수 하나로 결정되지 않는다. 다음 세 가지를 각각 정해야 한다.

```text
검색 범위       = 검색 결과 페이지를 몇 페이지 가져올 것인가
상세 조사 범위  = 검색 상품 중 몇 개의 상세·옵션을 확인할 것인가
리뷰 조사 범위  = 상품 하나당 리뷰를 몇 페이지 가져올 것인가
```

worker는 이미 정해진 작업을 동시에 몇 개 처리할지만 결정한다.

### 7.1 예시

```text
검색 상품 후보       50개
상세 조사 상품       상위 10개
리뷰 조사 상품       최종 후보 5개
상품당 리뷰          30개, 3페이지
```

예상 요청 수는 대략 다음과 같다.

```text
검색 요청                   1회
상품 상세 10개             10회
리뷰 5개 상품 × 3페이지    15회
--------------------------------
총 요청 예산               26회
```

worker를 1개에서 2개로 늘려도 총 26회라는 요청 수는 줄지 않는다. 단지 기다리는 시간을 줄일 수 있다.

### 7.2 처음부터 모든 데이터를 가져오지 않는 이유

검색된 상품 50개에 리뷰가 각각 1,000개 있다고 해서 처음부터 리뷰 50,000개를 가져올 필요는 없다.

구매 조사에서는 다음 순서가 효율적이다.

```text
1. 상품 기본정보를 넓게 수집
2. 사용자 조건으로 후보를 줄임
3. 남은 후보만 상세·옵션 수집
4. 최종 후보만 리뷰 수집
5. 구매 직전에 가격·재고 재확인
```

이 구조가 요청량과 DB 저장량을 줄이고 사용자 응답도 빠르게 만든다.

## 8. Worker와 요청 속도 설계

### 8.1 가장 중요한 구분

robots.txt는 일반적으로 어떤 URL 경로에 자동 클라이언트가 접근해도 되는지를 표시한다. worker 개수나 초당 요청 횟수를 정해 주는 문서가 아니다.

[Robots Exclusion Protocol 표준](https://www.rfc-editor.org/rfc/rfc9309.html)은 `Allow`와 `Disallow` 경로 규칙을 정의하며, robots 규칙은 접근 인증 자체가 아니라고 설명한다. 따라서 다음 값은 별도로 관리해야 한다.

- 접근 가능한 경로
- 동시 worker 수
- 요청 사이의 최소 간격
- 작업 하나의 최대 요청 수
- timeout
- retry 횟수

즉, `worker=2`라고 해서 자동으로 robots 정책을 지키는 것도 아니고 위반하는 것도 아니다. 먼저 접근 경로 정책을 확인하고, 그 다음 서버 부담을 줄이는 요청 속도를 별도로 정해야 한다.

### 8.2 2026-07-19 확인 결과

- [ABC마트 robots.txt](https://abcmart.a-rt.com/robots.txt): `User-agent: *`, `Allow: /`
- [무신사 robots.txt](https://www.musinsa.com/robots.txt): 지정된 Agent 외 wildcard 그룹은 `Disallow: /`

따라서 ABC마트와 무신사에 같은 운영 설정을 적용하면 안 된다.

현재 무신사 구현은 일반 User-agent를 사용하는 검색 구조 확인·소량 PoC다. 장기적으로 기본 활성화해 계속 수집할지, 공식 MCP·API·제휴·별도 허가로 전환할지는 별도 운영 결정이 필요하다.

### 8.3 제안하는 초기 설정

아래 숫자는 판매처가 공식적으로 보장한 허용량이 아니라, 개발 단계에서 과도한 요청을 막기 위한 보수적인 초안이다.

| 설정 | ABC마트 제안 | 무신사 PoC 제안 |
|---|---:|---:|
| 사용자가 명시한 검색 요청 | 지원 | 지원 |
| 자동 배치 기본 활성화 | 운영 설정 후 결정 | 비활성·opt-in 제안 |
| 검색 worker | 1 | 1 |
| 상세·리뷰 worker | 최대 2 | 공식 운영 경로 확정 전 1 |
| 판매처 전체 최소 요청 간격 | 1초 | 최소 1초, PoC만 |
| 요청 timeout | 10~15초 | 10~15초 |
| retry | 일시 오류에 최대 1회 | 일시 오류에 최대 1회 |
| 작업당 최대 요청 | 30회 | PoC는 더 작은 예산부터 시작 |
| 429 응답 | 즉시 중단·긴 backoff | 즉시 중단·긴 backoff |

### 8.4 고루틴 구조

worker가 여러 개여도 판매처 전체 rate limiter는 하나를 공유해야 한다.

```text
리뷰 작업 큐
  ├── 상품 A, 리뷰 1페이지
  ├── 상품 B, 리뷰 1페이지
  └── 상품 C, 리뷰 1페이지
          ↓
Worker 1 ─┐
Worker 2 ─┼→ 판매처 공통 Rate Limiter → 실제 HTTP 요청
          └→ 최소 1초 간격
```

잘못된 구조:

```text
Worker 1이 초당 1회
Worker 2도 초당 1회
Worker 3도 초당 1회
→ 실제 판매처에는 초당 3회 요청
```

권장 구조:

```text
Worker는 2개
공통 limiter는 초당 최대 1회
→ 두 worker가 있어도 판매처 전체 요청은 최소 1초 간격
```

worker가 필요한 이유는 HTTP 요청 외에도 JSON 해석, 결과 정리, 서로 다른 판매처 처리 등을 겹쳐 진행할 수 있기 때문이다.

### 8.5 백그라운드 작업 주체

Go HTTP handler에서 단순히 `go func()`를 실행하고 바로 끝내면 서버 재시작 시 작업과 결과를 잃을 수 있다.

따라서 백그라운드 작업 상태는 Python Backend가 소유하는 것이 좋다.

```text
Python이 collection_job 생성
        ↓
상태를 queued로 DB 저장
        ↓
Go Collector에 제한된 batch 요청
        ↓
Go 내부 Worker Pool이 상품 단위로 처리
        ↓
Python이 결과를 받음
        ↓
검증 후 DB 저장
        ↓
job 상태를 completed 또는 partial로 변경
```

Go는 실제 판매처 요청과 파싱을 담당하고, Python은 장기 작업 상태와 DB transaction을 담당한다.

## 9. PostgreSQL 적재 설계

### 9.1 책임 경계

Go Collector는 PostgreSQL에 직접 쓰지 않는다.

```text
Go Collector
  → 판매처 원본을 공통 CollectorResult로 변환

Python Research Backend
  → Contract 검사
  → 중복 정리와 정규화
  → PostgreSQL transaction
```

이렇게 나누는 이유:

- Go 파서가 실패해도 DB가 반쯤 저장되는 것을 막는다.
- ABC마트와 무신사의 서로 다른 데이터를 Python에서 같은 기준으로 정리한다.
- 수집 결과 Contract를 통과한 데이터만 저장한다.
- 추천 로직과 실제 크롤링 코드를 분리한다.

### 9.2 제안 테이블

| 테이블 | 저장 내용 |
|---|---|
| `research_sessions` | 사용자의 질문과 정리된 구매 조건 |
| `collection_jobs` | 수집 작업 상태, 진행 개수, 실패 개수 |
| `products` | 판매처와 무관하게 정리한 상품 기본정보 |
| `merchant_products` | 판매처 상품번호, 상품 URL, 판매처 이름 |
| `offer_snapshots` | 특정 시각의 가격·배송·재고 상태 |
| `product_options` | 사이즈·색상·옵션 가격·재고 |
| `review_snapshots` | 개인정보를 제외한 공개 리뷰 최소 필드 |
| `review_signals` | 사이즈감·착화감·품질 등 리뷰 분석 결과 |
| `evidence` | source URL, 수집 시각, Collector 버전 |
| `verification_results` | 추천 당시 값과 구매 직전 값의 차이 |

### 9.3 같은 상품 가격이 바뀌었을 때

가격을 기존 행에 덮어쓰기만 하면 과거 추천 근거를 잃는다. 따라서 snapshot을 새로 추가한다.

```text
2026-07-19 10:00  69,000원  재고 있음
2026-07-20 09:00  59,000원  재고 있음
2026-07-21 18:00  59,000원  품절
```

추천에 사용한 snapshot과 구매 직전에 확인한 snapshot을 분리해야 한다.

### 9.4 중복 처리 기준

1차 중복 키는 다음처럼 잡는다.

```text
merchant + externalId
```

예시:

```text
abcmart + 1010110882
musinsa + 5877464
```

같은 실제 상품이 ABC마트와 무신사에 동시에 있어도 처음부터 하나로 합치지 않는다. 브랜드, 모델 번호, 상품명 등을 이용한 판매처 간 상품 연결은 Python의 별도 정규화 단계에서 수행한다.

### 9.5 저장 순서

```text
1. collection_job 생성
2. Go Collector 결과 수신
3. JSON Schema와 Pydantic 검증
4. 판매처 상품 upsert
5. 새 offer snapshot 추가
6. 옵션 upsert와 snapshot 연결
7. 리뷰 중복 제거 후 저장
8. evidence 저장
9. transaction commit
10. job 완료 상태 저장
```

## 10. 새로운 쇼핑몰 추가 방법

새 판매처를 추가할 때 HTTP handler와 Python API 전체를 다시 만들지 않는다. 판매처별 Searcher 또는 상세·리뷰 Collector만 추가한다.

### 10.1 추가 순서

- [ ] 1. 판매처 이름 결정: 예 `29cm`, `wconcept`
- [ ] 2. robots.txt, 이용 조건, 공식 API·Feed·MCP 확인
- [ ] 3. 로그인 없이 공개된 검색·상세·리뷰 범위 확인
- [ ] 4. 데이터 원천 선택: 공식 API, JSON, HTML, 필요한 경우 브라우저
- [ ] 5. 저장한 HTML 또는 JSON fixture 준비
- [ ] 6. 공통 `Searcher` 구현
- [ ] 7. 판매처 데이터를 공통 `Product`로 변환
- [ ] 8. timeout, 응답 크기, redirect host 제한 구현
- [ ] 9. 판매처 공통 rate limiter와 concurrency 설정
- [ ] 10. parser 구조 변경 단위 테스트 작성
- [ ] 11. Registry에 판매처 한 줄 등록
- [ ] 12. opt-in live smoke test 작성
- [ ] 13. 운영 활성화 여부를 설정으로 분리

### 10.2 판매처 폴더 예시

```text
services/collector/internal/merchants/newshop/
├── search.go       # 검색 결과 수집
├── product.go      # 상세·옵션·배송 수집
├── reviews.go      # 공개 리뷰 수집
├── parser.go       # HTML 또는 JSON 해석
└── policy.go       # host, timeout, rate, worker 설정
```

실제 코드가 생기기 전에는 빈 폴더를 만들지 않고 TODO에만 기록한다.

### 10.3 Registry 등록 예시

```go
registry := collector.NewSearchRegistry(map[string]collector.Searcher{
    "abcmart": abcmart.NewSearcher(searchTimeout),
    "musinsa": musinsa.NewSearcher(searchTimeout),
    "newshop": newshop.NewSearcher(searchTimeout),
})
```

이렇게 하면 다음 코드는 판매처마다 새로 만들지 않는다.

- HTTP 요청 JSON 검사
- 공통 응답 형식
- Python Collector client의 기본 호출 방식
- DB 저장 규격
- MCP 도구의 기본 흐름

## 11. HTML이나 JSON 구조가 바뀌었을 때

판매처 구조 변경을 완전히 자동으로 고치는 것은 위험하다. 자동으로 잘못된 필드를 가격이나 재고로 저장할 수 있기 때문이다.

우선은 자동 감지와 수동 수정 구조가 적절하다.

```text
판매처 구조 변경
        ↓
parser가 상품 목록을 찾지 못함
        ↓
MUSINSA_PAGE_CHANGED 또는 ABCMART_PAGE_CHANGED
        ↓
빈 정상 결과로 저장하지 않음
        ↓
알림과 실패 fixture 저장
        ↓
개발자가 parser 수정
        ↓
fixture regression test 통과 후 배포
```

필요한 자동화:

- 기본 CI에서는 저장 fixture로 parser 회귀 테스트
- 실제 판매처 smoke test는 opt-in 또는 낮은 빈도 스케줄
- 상품 수가 갑자기 0개가 되면 경고
- 필수 필드 누락 비율이 높아지면 경고
- HTTP 403·429·5xx 비율 기록
- Collector 버전별 성공률 기록
- 구조 변경 오류 응답의 일부를 민감정보 없이 보관

향후 LLM이 새 구조를 분석해 parser 수정 후보를 제안할 수는 있지만, 자동 배포하지 않고 fixture와 사람 검토를 통과시킨다.

## 12. 개발 순서 제안

### 1단계: 현재 검색 안정화

- [x] ABC마트 검색
- [x] 무신사 검색
- [x] 판매처 Registry
- [x] 요청 사이 최소 1초 간격
- [x] 실제 검색 opt-in smoke test
- [ ] 판매처별 설정 구조 분리
- [ ] 검색 pagination 계약 추가
- [ ] 요청 예산과 최대 페이지 제한 추가

완료 기준: 검색 결과를 최대 50개까지 안정적으로 공통 형식으로 반환한다.

### 2단계: 상세·옵션·리뷰

- [ ] 상품 상세 요청 계약
- [ ] ABC마트 상세·배송·전체 옵션
- [ ] 무신사 상세·옵션 구조 확인
- [ ] 리뷰 요청 계약
- [ ] 무신사 리뷰 JSON을 공통 `Review`로 변환
- [ ] 리뷰 작성자 식별정보 제거 테스트
- [ ] 리뷰 이미지 다운로드 금지와 `hasImage` 변환 테스트

완료 기준: 선택한 상품의 구매 판단에 필요한 가격·옵션·리뷰 근거를 반환한다.

### 3단계: 제한된 Worker Pool

- [ ] 상품 작업 큐
- [ ] 판매처별 worker 상한
- [ ] worker가 공유하는 판매처 전체 rate limiter
- [ ] 작업당 최대 요청 예산
- [ ] context 취소와 graceful shutdown
- [ ] 부분 실패 결과
- [ ] 429·5xx backoff
- [ ] race test

완료 기준: 여러 상품을 처리해도 설정된 동시성·요청 간격·최대 요청 수를 넘지 않는다.

### 4단계: Python과 PostgreSQL

- [ ] Python Collector client
- [ ] Pydantic Contract 검증
- [ ] PostgreSQL 실행 환경과 migration 도구
- [ ] collection job repository
- [ ] product와 merchant product repository
- [ ] offer·option snapshot repository
- [ ] review·review signal repository
- [ ] evidence repository
- [ ] transaction과 중복 처리 테스트

완료 기준: Go 결과가 검증된 뒤 수집 시각과 출처를 유지하며 재현 가능하게 저장된다.

### 5단계: 운영과 판매처 확장

- [ ] 판매처별 기능·정책 설정 파일
- [ ] parser 구조 변경 감지
- [ ] 낮은 빈도의 opt-in 또는 승인된 live smoke
- [ ] 수집 성공률과 지연시간 로그
- [ ] 새 판매처 Adapter 템플릿
- [ ] 공식 API·MCP·Feed 우선 연결 정책

완료 기준: 새 판매처 추가가 기존 ABC마트·무신사 코드를 수정하지 않고 Adapter 등록으로 가능하다.

## 13. 지금 바로 다음에 할 작업

가장 먼저 구현할 수직 흐름은 다음과 같다.

```text
무신사에서 상품 5개 검색
        ↓
상위 상품 2개 선택
        ↓
상품당 리뷰 첫 페이지 10개 수집
        ↓
개인정보 필드 제거
        ↓
공통 CollectorResult 반환
        ↓
Python에서 Contract 검증
        ↓
PostgreSQL에 상품·snapshot·review 저장
```

첫 Worker Pool 코드는 worker 2개까지 지원하도록 만들되 판매처별 설정을 다르게 적용한다. ABC마트는 최대 2개, 무신사는 운영 경로가 확정되기 전 worker 1개와 작은 요청 예산을 사용한다. 두 경우 모두 worker가 판매처 전체 최소 요청 간격을 공유해야 한다. 자동 배치와 live smoke test는 opt-in으로 유지하고, 사용자가 명시한 검색 요청과 구분한다.

이 흐름이 완성되면 상품 검색만 되는 데모가 아니라 실제로 검색 결과와 공개 리뷰 근거가 DB에 남는 첫 번째 완성된 세로 기능이 된다.
