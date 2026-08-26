package collector_test

import (
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

// TestSearchRequestApplyDefaults는 생략된 검색 조건에 계약 기본값이 적용되는지 검증한다.
func TestSearchRequestApplyDefaults(t *testing.T) {
	request := collector.SearchRequest{}
	request.ApplyDefaults()

	if request.Limit != 10 || request.Locale != "ko-KR" || request.Currency != "KRW" {
		t.Fatalf("defaults = %#v", request)
	}
}

// TestSearchRequestValidate는 정상 검색 요청이 검증을 통과하는지 확인한다.
func TestSearchRequestValidate(t *testing.T) {
	if err := validSearchRequest().Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
}

// TestSearchRequestValidateRejectsInvalidValues는 잘못된 필수값과 범위를 거부하는지 검증한다.
func TestSearchRequestValidateRejectsInvalidValues(t *testing.T) {
	negativePrice := -1
	priceMin := 100_000
	priceMax := 50_000

	testCases := []struct {
		name   string
		change func(*collector.SearchRequest)
	}{
		{name: "empty request id", change: func(r *collector.SearchRequest) { r.RequestID = "" }},
		{name: "invalid merchant", change: func(r *collector.SearchRequest) { r.Merchant = "ABCMART" }},
		{name: "empty query", change: func(r *collector.SearchRequest) { r.Query = " " }},
		{name: "zero requested at", change: func(r *collector.SearchRequest) { r.RequestedAt = time.Time{} }},
		{name: "limit too large", change: func(r *collector.SearchRequest) { r.Limit = 51 }},
		{name: "invalid locale", change: func(r *collector.SearchRequest) { r.Locale = "ko" }},
		{name: "invalid currency", change: func(r *collector.SearchRequest) { r.Currency = "krw" }},
		{name: "negative price", change: func(r *collector.SearchRequest) { r.Filters.PriceMin = &negativePrice }},
		{name: "reversed price range", change: func(r *collector.SearchRequest) {
			r.Filters.PriceMin = &priceMin
			r.Filters.PriceMax = &priceMax
		}},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			request := validSearchRequest()
			testCase.change(&request)
			if err := request.Validate(); err == nil {
				t.Fatal("Validate() error = nil, want validation error")
			}
		})
	}
}

// validSearchRequest는 검증 테스트에서 재사용할 정상 요청을 생성한다.
func validSearchRequest() collector.SearchRequest {
	return collector.SearchRequest{
		RequestID:   "research-001",
		Merchant:    "abcmart",
		Query:       "면접용 구두",
		RequestedAt: time.Date(2026, 7, 16, 12, 0, 0, 0, time.FixedZone("KST", 9*60*60)),
		Limit:       10,
		Locale:      "ko-KR",
		Currency:    "KRW",
	}
}
