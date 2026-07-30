# Go Collector 데이터 변환이 실제 코드에서 움직이는 순서

- 작성일: 2026-07-21
- 문서 상태: 현재 구현 기준 설명
- 대상 독자: Go Collector와 Contracts의 관계가 처음에는 헷갈리는 개발자

## 1. 결론부터 보기

쇼핑몰 데이터를 공통 형식으로 바꾸는 작업은 Go 코드가 수행한다.

`contracts`의 JSON Schema가 자동으로 번역하는 것은 아니다.

```text
Go 판매처 코드 = 실제 번역기
공통 Go struct  = 번역 결과를 담는 DTO
JSON Schema     = 번역 결과가 지켜야 하는 규칙
Contract test   = 규칙을 지켰는지 확인하는 검사
```

전체 흐름은 다음과 같다.

```text
사용자 검색 요청
    ↓
HTTP Handler가 요청 JSON을 Go struct로 읽음
    ↓
Registry가 merchant에 맞는 판매처 Searcher 선택
    ↓
판매처 Searcher가 쇼핑몰 HTML 또는 JSON 요청
    ↓
판매처 원본 응답을 판매처 전용 Go struct로 읽음
    ↓
문자열 가격·재고·카테고리를 공통 의미로 정리
    ↓
collector.Product 공통 struct로 변환
    ↓
HTTP Handler가 공통 struct를 JSON으로 출력
    ↓
Product Backend가 Contract 기준으로 검사하고 저장할 예정
```

## 2. 각 구성요소의 역할

| 구성요소 | 실제 역할 | 직접 번역하는가? |
|---|---|---|
| HTTP Handler | 검색 요청을 읽고 결과를 JSON으로 반환 | 아니오 |
| Registry | `merchant`에 맞는 판매처 코드를 선택 | 아니오 |
| 판매처 Searcher | 원본 응답 요청·해석·정규화·공통 변환 | 예 |
| `collector.Product` | 모든 판매처가 사용하는 공통 Go DTO | 변환 결과를 담음 |
| `contracts/*.schema.json` | 최종 JSON의 필드와 자료형 규칙 | 아니오 |
| Product Backend | Contract 검사, 정규화 보완, DB 저장 | Spring Boot로 구현 예정 |

Spring Boot에 비유하면 다음과 같다.

```text
Controller        = Go HTTP Handler
Service Router    = SearchRegistry
외부 API Client   = 판매처별 Searcher
외부 Response DTO = 판매처별 searchItem
Mapper            = normalizeItem + toProduct
공통 Response DTO = collector.Product
OpenAPI/Schema    = contracts의 JSON Schema
```

## 3. 1단계: 검색 요청이 HTTP Handler로 들어온다

사용자가 다음 요청을 보낸다고 가정한다.

```json
{
  "requestId": "my-test-001",
  "merchant": "abcmart",
  "query": "구두",
  "requestedAt": "2026-07-21T10:00:00+09:00",
  "limit": 3
}
```

코드 위치:

- [`services/collector/internal/transport/http/search.go`](../../services/collector/internal/transport/http/search.go)
- 함수: `searchHandler.ServeHTTP`

핵심 코드는 다음과 같다.

```go
var request collector.SearchRequest
decoder.Decode(&request)
```

이 단계에서 요청 JSON이 `collector.SearchRequest`로 바뀐다.

```text
JSON의 merchant → request.Merchant
JSON의 query    → request.Query
JSON의 limit    → request.Limit
```

아직 쇼핑몰에는 접속하지 않았고, 상품 데이터 변환도 시작하지 않았다.

## 4. 2단계: Registry가 판매처 코드를 고른다

코드 위치:

- [`services/collector/internal/transport/http/search.go`](../../services/collector/internal/transport/http/search.go)
- [`services/collector/internal/collector/registry.go`](../../services/collector/internal/collector/registry.go)

등록된 판매처는 다음과 같다.

```go
map[string]collector.Searcher{
    "29cm":    twentyninecm.NewSearcher(searchTimeout),
    "abcmart": abcmart.NewSearcher(searchTimeout),
    "musinsa": musinsa.NewSearcher(searchTimeout),
}
```

Registry는 요청의 `merchant`를 보고 Searcher를 선택한다.

```text
merchant=abcmart → abcmart.Searcher.Search
merchant=29cm    → twentyninecm.Searcher.Search
등록되지 않은 값 → unsupported 오류
```

Registry는 ABC마트 JSON 필드나 29CM JSON 필드를 모른다. 판매처 코드를 선택하기만 한다.

## 5. 3단계: 판매처 원본 JSON을 전용 struct로 읽는다

### 5.1 ABC마트 원본

ABC마트는 상품을 대략 다음처럼 반환한다.

```json
{
  "PRDT_NO": "1010110882",
  "PRDT_NAME": "페니 로퍼",
  "PRDT_DC_PRICE": "69000",
  "RVW_COUNT": "4",
  "SIZE_LIST": {
    "250": "0",
    "270": "10"
  }
}
```

ABC마트 전용 구조체는 다음 파일에 있다.

- [`services/collector/internal/merchants/abcmart/search.go`](../../services/collector/internal/merchants/abcmart/search.go)
- type: `searchItem`

```go
type searchItem struct {
    ProductNo     string            `json:"PRDT_NO"`
    Name          string            `json:"PRDT_NAME"`
    DiscountPrice string            `json:"PRDT_DC_PRICE"`
    ReviewCount   string            `json:"RVW_COUNT"`
    SizeList      map[string]string `json:"SIZE_LIST"`
}
```

Go의 JSON tag가 원본 이름과 Go 필드를 연결한다.

```text
`json:"PRDT_NO"`       → PRDT_NO 값을 ProductNo에 넣음
`json:"PRDT_NAME"`     → PRDT_NAME 값을 Name에 넣음
`json:"PRDT_DC_PRICE"` → PRDT_DC_PRICE 값을 DiscountPrice에 넣음
```

실제 응답을 읽는 코드는 `json.Unmarshal`이다.

```go
var payload searchResponse
err := json.Unmarshal(body, &payload)
```

이 시점에는 아직 ABC마트의 원본 자료형을 그대로 유지한다.

```text
DiscountPrice = "69000" // 문자열
ReviewCount   = "4"     // 문자열
```

### 5.2 29CM 원본

29CM는 대략 다음처럼 반환한다.

```json
{
  "itemId": 2468262,
  "itemInfo": {
    "productName": "BELLA SLINGBACK",
    "displayPrice": 100960
  }
}
```

29CM 전용 구조체는 다음 파일에 있다.

- [`services/collector/internal/merchants/twentyninecm/search.go`](../../services/collector/internal/merchants/twentyninecm/search.go)
- type: `searchItem`

```go
type searchItem struct {
    ItemID int `json:"itemId"`
    ItemInfo struct {
        ProductName  string `json:"productName"`
        DisplayPrice int    `json:"displayPrice"`
    } `json:"itemInfo"`
}
```

판매처마다 `searchItem` 모양은 달라도 정상이다. 이 구조체는 판매처 폴더 안에서만 사용하는 원본 응답 DTO다.

## 6. 4단계: 원본 값을 공통 의미로 정리한다

ABC마트 가격은 문자열이지만 우리 공통 가격은 정수다.

```text
ABC마트 원본 "69000"
        ↓ parseNonNegativeInt
Go 정수 69000
```

ABC마트 코드의 `normalizeItem`이 이 작업을 한다.

```go
price, err := parseNonNegativeInt(item.DiscountPrice)
```

동시에 다음 작업도 수행한다.

- 상품 번호와 이름이 비었는지 검사
- 음수 가격 거부
- 리뷰 수 문자열을 정수로 변환
- `신발 > 구두 > 로퍼`를 배열로 분리
- `SOLD_OUT` 문자열을 bool 의미로 변환
- 사이즈별 재고 문자열을 정수로 변환

결과는 `normalizedItem`에 담긴다.

```go
type normalizedItem struct {
    ProductNo   string
    Name        string
    Price       int
    Category    []string
    IsSoldOut   bool
    ReviewCount *int
    SizeStocks  []sizeStock
}
```

이 단계가 필요한 이유는 원본 JSON을 바로 공통 결과에 넣으면 문자열 가격, 잘못된 재고 값, 판매처 전용 표현이 그대로 퍼지기 때문이다.

## 7. 5단계: 공통 Product로 변환한다

공통 상품 구조는 다음 파일에 있다.

- [`services/collector/internal/collector/search.go`](../../services/collector/internal/collector/search.go)
- type: `Product`

```go
type Product struct {
    ExternalID   string   `json:"externalId"`
    Name         string   `json:"name"`
    Brand        *string  `json:"brand"`
    CategoryPath []string `json:"categoryPath"`
    Price        *Money   `json:"price"`
    StockStatus  string   `json:"stockStatus"`
    Rating       *float64 `json:"rating"`
    ReviewCount  *int     `json:"reviewCount"`
}
```

판매처별 `toProduct` 함수가 실제 매핑을 작성한다.

### ABC마트 매핑

```go
return collector.Product{
    ExternalID:   item.ProductNo,
    Name:         item.Name,
    CategoryPath: item.Category,
    Price: &collector.Money{
        Amount:   item.Price,
        Currency: "KRW",
    },
    ReviewCount: item.ReviewCount,
}
```

```text
ABC마트 ProductNo → 공통 ExternalID
ABC마트 Name      → 공통 Name
ABC마트 Price     → 공통 Price.Amount
ABC마트 Category  → 공통 CategoryPath
```

### 29CM 매핑

```go
return collector.Product{
    ExternalID: strconv.Itoa(item.ItemID),
    Name:       item.ItemInfo.ProductName,
    Price: &collector.Money{
        Amount:   item.ItemInfo.DisplayPrice,
        Currency: "KRW",
    },
}
```

29CM의 상품 번호는 숫자이므로 `strconv.Itoa`를 이용해 공통 문자열 ID로 바꾼다.

## 8. 6단계: 공통 struct를 응답 JSON으로 출력한다

HTTP Handler가 마지막으로 실행하는 코드는 다음과 같다.

```go
json.NewEncoder(w).Encode(value)
```

공통 Go struct의 JSON tag를 이용해 다음 응답이 만들어진다.

```json
{
  "externalId": "1010110882",
  "name": "페니 로퍼",
  "price": {
    "amount": 69000,
    "currency": "KRW"
  },
  "reviewCount": 4
}
```

전체 변환을 한 줄로 보면 다음과 같다.

```text
"PRDT_DC_PRICE": "69000"
        ↓ json.Unmarshal
DiscountPrice string = "69000"
        ↓ normalizeItem
Price int = 69000
        ↓ toProduct
Money{Amount: 69000, Currency: "KRW"}
        ↓ json.Encode
"price": {"amount": 69000, "currency": "KRW"}
```

## 9. Contract는 정확히 어디에 끼는가

Contract는 위 실행 과정에서 판매처 값을 자동으로 변환하지 않는다.

Contract가 정하는 것은 최종 결과의 규칙이다.

```text
toProduct가 공통 응답 작성
        ↓
collector-result.schema.json 기준과 비교
        ↓
필드·자료형·필수값이 맞으면 통과
```

예를 들어 Contract가 가격을 다음 형식으로 정했다고 가정한다.

```json
{
  "amount": 69000,
  "currency": "KRW"
}
```

아래 응답은 공통 규칙에 맞지 않는다.

```json
{
  "shoe_prices": "69,000원"
}
```

관련 파일:

- [`contracts/collector/v1/collector-result.schema.json`](../../contracts/collector/v1/collector-result.schema.json)
- [`contracts/collector/v1/examples/collector-result.success.json`](../../contracts/collector/v1/examples/collector-result.success.json)

현재 상태는 다음과 같다.

- 판매처별 변환 Go 코드: 구현
- 공통 Go DTO: 구현
- JSON Schema와 예제: 초안 구현
- Schema 예제 검증: 통과
- 실제 Go HTTP 응답 전체를 Schema로 자동 검사하는 contract test: 아직 구현 예정
- Python Pydantic model과 DB 저장: 아직 구현 예정

## 10. 값이 없거나 의미가 불확실하면

판매처가 필드를 제공하지 않으면 값을 만들어내지 않는다.

```text
평점을 제공하지 않음       → rating: null
옵션을 아직 수집하지 않음   → options: []
재고 상태를 알 수 없음      → stockStatus: unknown
일부 필터만 지원함          → status: partial + warnings
전체 상품 수를 제공하지 않음 → totalCount: null
```

필드 이름이 비슷해도 의미가 다르면 바로 매핑하지 않는다.

예를 들어 `originalPrice`, `sellPrice`, `displayPrice` 중 실제 화면 가격이 무엇인지 확인한 다음 공통 가격에 연결한다.

## 11. 새로운 판매처를 추가할 때

예를 들어 `kkst` 판매처를 추가한다면 다음 순서로 구현한다.

- [ ] `internal/merchants/kkst/search.go` 생성
- [ ] 공개 검색 응답 구조 확인
- [ ] `kkst` 원본 `searchResponse`, `searchItem` 작성
- [ ] `json.Unmarshal` 또는 HTML parser 구현
- [ ] 가격·재고·카테고리 정규화 함수 작성
- [ ] `toProduct`로 공통 `collector.Product` 변환
- [ ] Registry에 `kkst` 등록
- [ ] 저장 fixture 단위 테스트 작성
- [ ] `totalCount`, `hasNext` 매핑
- [ ] 실제 smoke test는 opt-in으로 분리
- [ ] Contract에서 표현할 수 없는 새 개념인지 검토

판매처 원본 필드 이름이 달라도 공통 Contract를 매번 새로 만들지는 않는다.

```text
kkst의 shoe_prices → kkst 코드에서 해석 → 공통 price.amount
```

정말 새로운 구매 개념이 생겼을 때만 공통 Contract 변경을 검토한다.

## 12. 코드를 따라가며 확인하는 순서

처음 코드를 볼 때는 다음 순서가 가장 쉽다.

1. `services/collector/internal/transport/http/search.go`의 `ServeHTTP`
2. `services/collector/internal/collector/registry.go`의 `Search`
3. `services/collector/internal/merchants/abcmart/search.go`의 `Search`
4. 같은 파일의 `normalizeItem`
5. 같은 파일의 `toProduct`
6. `services/collector/internal/collector/search.go`의 `Product`
7. `contracts/collector/v1/collector-result.schema.json`

이 순서로 보면 요청이 어디로 들어오고, 어느 판매처 코드가 선택되고, 최종 JSON이 어떻게 만들어지는지 한 방향으로 따라갈 수 있다.
