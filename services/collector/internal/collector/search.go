// Package collector는 판매처 수집 흐름에서 공유하는 요청, 결과, 인터페이스를 정의한다.
package collector

import (
	"context"
	"fmt"
	"regexp"
	"strings"
	"time"
)

const (
	// OperationSearch는 상품 검색 작업을 나타낸다.
	OperationSearch = "search"

	// StatusSuccess는 요청한 공개 정보가 정상적으로 수집됐음을 나타낸다.
	StatusSuccess = "success"
	// StatusPartial은 일부 정보만 수집됐음을 나타낸다.
	StatusPartial = "partial"
	// StatusBlocked는 판매처 정책이나 접근 통제로 수집하지 않았음을 나타낸다.
	StatusBlocked = "blocked"
	// StatusUnsupported는 요청한 판매처나 작업을 현재 지원하지 않음을 나타낸다.
	StatusUnsupported = "unsupported"
	// StatusTemporarilyUnavailable은 timeout이나 일시적인 원격 오류를 나타낸다.
	StatusTemporarilyUnavailable = "temporarily_unavailable"

	// StockAvailable은 상품을 현재 구매할 수 있음을 나타낸다.
	StockAvailable = "available"
	// StockOut은 상품의 재고가 없음을 나타낸다.
	StockOut = "out_of_stock"

	// VerificationPending은 HTML 교차 검증을 아직 수행하지 않았음을 나타낸다.
	VerificationPending = "PENDING"
	// VerificationMatched는 JSON과 HTML의 비교 필드가 모두 일치함을 나타낸다.
	VerificationMatched = "MATCHED"
	// VerificationMismatch는 JSON과 HTML의 비교 필드 중 하나 이상이 다름을 나타낸다.
	VerificationMismatch = "MISMATCH"
	// VerificationMissingInHTML은 JSON 상품을 HTML에서 찾지 못했음을 나타낸다.
	VerificationMissingInHTML = "MISSING_IN_HTML"
	// VerificationMissingInJSON은 HTML 상품을 JSON에서 찾지 못했음을 나타낸다.
	VerificationMissingInJSON = "MISSING_IN_JSON"
	// VerificationFailed는 HTML 요청 또는 parsing 실패로 비교를 완료하지 못했음을 나타낸다.
	VerificationFailed = "FAILED"
)

var (
	identifierPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]*$`)
	merchantPattern   = regexp.MustCompile(`^[a-z0-9][a-z0-9-]*$`)
	localePattern     = regexp.MustCompile(`^[a-z]{2}-[A-Z]{2}$`)
	currencyPattern   = regexp.MustCompile(`^[A-Z]{3}$`)
)

// Searcher는 하나의 판매처에서 상품 검색 결과를 수집하는 구현 계약이다.
type Searcher interface {
	Search(ctx context.Context, request SearchRequest) SearchResult
}

// PageSearcher는 Queue 대량 작업이 지정한 검색 페이지를 수집하는 확장 계약이다.
// 일반 HTTP 검색은 Searcher를 사용하고 page=2 이상이 필요한 내부 Worker만 이 계약을 사용한다.
type PageSearcher interface {
	SearchPage(ctx context.Context, request SearchRequest, page int) SearchResult
}

// SearchRequest는 Product Backend 또는 내부 호출자가 Collector에 전달하는 상품 검색 조건이다.
type SearchRequest struct {
	RequestID   string        `json:"requestId"`
	Merchant    string        `json:"merchant"`
	Query       string        `json:"query"`
	RequestedAt time.Time     `json:"requestedAt"`
	Limit       int           `json:"limit,omitempty"`
	Locale      string        `json:"locale,omitempty"`
	Currency    string        `json:"currency,omitempty"`
	Filters     SearchFilters `json:"filters,omitempty"`
}

// ApplyDefaults는 선택 검색 조건이 빠졌을 때 v1 계약의 기본값을 적용한다.
func (r *SearchRequest) ApplyDefaults() {
	if r.Limit == 0 {
		r.Limit = 10
	}
	if r.Locale == "" {
		r.Locale = "ko-KR"
	}
	if r.Currency == "" {
		r.Currency = "KRW"
	}
}

// Validate는 검색 요청이 Collector v1 계약의 필수 형식과 길이를 만족하는지 검사한다.
func (r SearchRequest) Validate() error {
	if len(r.RequestID) == 0 || len(r.RequestID) > 128 || !identifierPattern.MatchString(r.RequestID) {
		return fmt.Errorf("requestId 형식이 올바르지 않습니다")
	}
	if len(r.Merchant) == 0 || len(r.Merchant) > 64 || !merchantPattern.MatchString(r.Merchant) {
		return fmt.Errorf("merchant 형식이 올바르지 않습니다")
	}
	query := strings.TrimSpace(r.Query)
	if len(query) == 0 || len(query) > 200 {
		return fmt.Errorf("query는 1자 이상 200자 이하여야 합니다")
	}
	if r.RequestedAt.IsZero() {
		return fmt.Errorf("requestedAt은 RFC3339 날짜와 시간이어야 합니다")
	}
	if r.Limit < 1 || r.Limit > 50 {
		return fmt.Errorf("limit은 1 이상 50 이하여야 합니다")
	}
	if !localePattern.MatchString(r.Locale) {
		return fmt.Errorf("locale은 ko-KR 형식이어야 합니다")
	}
	if !currencyPattern.MatchString(r.Currency) {
		return fmt.Errorf("currency는 KRW 같은 3자리 대문자 코드여야 합니다")
	}
	if r.Filters.PriceMin != nil && *r.Filters.PriceMin < 0 {
		return fmt.Errorf("priceMin은 0 이상이어야 합니다")
	}
	if r.Filters.PriceMax != nil && *r.Filters.PriceMax < 0 {
		return fmt.Errorf("priceMax는 0 이상이어야 합니다")
	}
	if r.Filters.PriceMin != nil && r.Filters.PriceMax != nil && *r.Filters.PriceMin > *r.Filters.PriceMax {
		return fmt.Errorf("priceMin은 priceMax보다 클 수 없습니다")
	}
	return nil
}

// SearchFilters는 가격, 분류, 옵션, 재고 등 선택 검색 조건을 표현한다.
type SearchFilters struct {
	PriceMin    *int                   `json:"priceMin,omitempty"`
	PriceMax    *int                   `json:"priceMax,omitempty"`
	Categories  []string               `json:"categories,omitempty"`
	Sizes       []string               `json:"sizes,omitempty"`
	Colors      []string               `json:"colors,omitempty"`
	InStockOnly bool                   `json:"inStockOnly,omitempty"`
	Attributes  map[string]interface{} `json:"attributes,omitempty"`
}

// SearchResult는 상품 검색 작업의 상태, 원본 검색 조건, 판매처 기준 페이지 정보, 상품, 경고와 오류를 전달한다.
// TotalCount와 HasNext는 판매처가 값을 제공하지 않거나 요청이 실패하면 nil이며, 로컬 필터 적용 전 판매처 검색 결과를 기준으로 한다.
type SearchResult struct {
	RequestID           string               `json:"requestId"`
	Operation           string               `json:"operation"`
	Status              string               `json:"status"`
	Merchant            string               `json:"merchant"`
	Query               string               `json:"query"`
	Filters             SearchFilters        `json:"filters"`
	TotalCount          *int                 `json:"totalCount"`
	HasNext             *bool                `json:"hasNext"`
	CollectedAt         time.Time            `json:"collectedAt"`
	CollectorVersion    string               `json:"collectorVersion"`
	VerificationSummary *VerificationSummary `json:"verificationSummary,omitempty"`
	Products            []Product            `json:"products"`
	Warnings            []Issue              `json:"warnings"`
	Errors              []Issue              `json:"errors"`
}

// VerificationSummary는 응답 상품들의 JSON/HTML 비교 상태를 한눈에 볼 수 있게 집계한다.
type VerificationSummary struct {
	Total         int `json:"total"`
	Matched       int `json:"matched"`
	Mismatched    int `json:"mismatched"`
	Failed        int `json:"failed"`
	MissingInHTML int `json:"missingInHtml"`
	MissingInJSON int `json:"missingInJson"`
	Pending       int `json:"pending"`
}

// SummarizeVerifications는 검증 결과가 있는 상품만 상태별로 집계하고 없으면 nil을 반환한다.
func SummarizeVerifications(products []Product) *VerificationSummary {
	summary := VerificationSummary{}
	for _, product := range products {
		if product.Verification == nil {
			continue
		}
		summary.Total++
		switch product.Verification.Status {
		case VerificationMatched:
			summary.Matched++
		case VerificationMismatch:
			summary.Mismatched++
		case VerificationFailed:
			summary.Failed++
		case VerificationMissingInHTML:
			summary.MissingInHTML++
		case VerificationMissingInJSON:
			summary.MissingInJSON++
		case VerificationPending:
			summary.Pending++
		}
	}
	if summary.Total == 0 {
		return nil
	}
	return &summary
}

// Product는 판매처 검색 페이지에서 수집한 상품 기본 정보를 표현한다.
type Product struct {
	ExternalID   string                      `json:"externalId"`
	Name         string                      `json:"name"`
	Brand        *string                     `json:"brand"`
	CategoryPath []string                    `json:"categoryPath"`
	ProductURL   string                      `json:"productUrl"`
	ImageURLs    []string                    `json:"imageUrls"`
	Price        *Money                      `json:"price"`
	Shipping     Shipping                    `json:"shipping"`
	StockStatus  string                      `json:"stockStatus"`
	Rating       *float64                    `json:"rating"`
	ReviewCount  *int                        `json:"reviewCount"`
	Options      []Option                    `json:"options"`
	Measurements map[string]MeasurementValue `json:"measurements"`
	Reviews      []Review                    `json:"reviews"`
	Verification *Verification               `json:"verification,omitempty"`
	Provenance   Provenance                  `json:"provenance"`
}

// Verification은 JSON 기본 수집값과 HTML 표시값의 상품별 비교 결과를 표현한다.
type Verification struct {
	Status         string                   `json:"status"`
	ComparedFields []string                 `json:"comparedFields"`
	Differences    []VerificationDifference `json:"differences"`
	JSONSourceURL  string                   `json:"jsonSourceUrl"`
	HTMLSourceURL  string                   `json:"htmlSourceUrl"`
	VerifiedAt     time.Time                `json:"verifiedAt"`
}

// VerificationDifference는 하나의 비교 필드에서 확인한 JSON과 HTML 값을 표현한다.
type VerificationDifference struct {
	Field     string  `json:"field"`
	JSONValue *string `json:"jsonValue"`
	HTMLValue *string `json:"htmlValue"`
}

// Money는 금액을 최소 통화 단위의 정수와 통화 코드로 표현한다.
type Money struct {
	Amount   int    `json:"amount"`
	Currency string `json:"currency"`
}

// Shipping은 검색 단계에서 확인 가능한 배송 정보와 출처를 표현한다.
type Shipping struct {
	Fee        *Money     `json:"fee"`
	Summary    *string    `json:"summary"`
	Provenance Provenance `json:"provenance"`
}

// Option은 상품 상세 단계에서 채울 색상·사이즈 옵션 구조다.
type Option struct {
	ExternalID *string    `json:"externalId"`
	Label      string     `json:"label"`
	Size       *string    `json:"size"`
	Color      *string    `json:"color"`
	Stock      string     `json:"stockStatus"`
	Price      *Money     `json:"price"`
	Provenance Provenance `json:"provenance"`
}

// MeasurementValue는 상품 실측값과 단위 및 출처를 표현한다.
type MeasurementValue struct {
	Value      float64    `json:"value"`
	Unit       string     `json:"unit"`
	Provenance Provenance `json:"provenance"`
}

// Review는 작성자 식별정보를 제외한 공개 리뷰의 최소 수집 필드를 표현한다.
type Review struct {
	ExternalID      *string    `json:"externalId"`
	Rating          *float64   `json:"rating"`
	Text            *string    `json:"text"`
	HasImage        bool       `json:"hasImage"`
	PurchasedOption *string    `json:"purchasedOption"`
	CreatedAt       *time.Time `json:"createdAt"`
	Provenance      Provenance `json:"provenance"`
}

// Provenance는 상품 사실을 확인한 공개 URL, 시각, Collector 버전을 기록한다.
type Provenance struct {
	SourceURL        string    `json:"sourceUrl"`
	CollectedAt      time.Time `json:"collectedAt"`
	CollectorVersion string    `json:"collectorVersion"`
}

// Issue는 수집 중 발생한 경고나 오류를 표현한다.
type Issue struct {
	Code      string  `json:"code"`
	Message   string  `json:"message"`
	Retryable bool    `json:"retryable"`
	SourceURL *string `json:"sourceUrl,omitempty"`
}
