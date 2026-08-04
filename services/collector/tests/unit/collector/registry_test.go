package collector_test

import (
	"context"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

// registrySearcherFunc는 함수로 판매처 검색 결과를 제어하는 Registry 테스트 대역이다.
type registrySearcherFunc func(context.Context, collector.SearchRequest) collector.SearchResult

// Search는 등록된 함수에 검색 요청을 전달한다.
func (f registrySearcherFunc) Search(ctx context.Context, request collector.SearchRequest) collector.SearchResult {
	return f(ctx, request)
}

// registryPageSearcher는 Registry가 지정 페이지를 판매처 검색기에 전달하는지 기록한다.
type registryPageSearcher struct {
	page int
}

// Search는 첫 페이지 검색 결과를 반환한다.
func (s *registryPageSearcher) Search(_ context.Context, request collector.SearchRequest) collector.SearchResult {
	return collector.SearchResult{RequestID: request.RequestID, Merchant: request.Merchant, Status: collector.StatusSuccess}
}

// SearchPage는 요청 페이지를 기록하고 검색 결과를 반환한다.
func (s *registryPageSearcher) SearchPage(_ context.Context, request collector.SearchRequest, page int) collector.SearchResult {
	s.page = page
	return collector.SearchResult{RequestID: request.RequestID, Merchant: request.Merchant, Status: collector.StatusSuccess}
}

// TestSearchRegistryRoutesMerchant는 판매처 이름에 맞는 검색기가 호출되는지 검증한다.
func TestSearchRegistryRoutesMerchant(t *testing.T) {
	called := false
	priceMin := 10000
	registry := collector.NewSearchRegistry(map[string]collector.Searcher{
		"abcmart": registrySearcherFunc(func(_ context.Context, request collector.SearchRequest) collector.SearchResult {
			called = true
			return collector.SearchResult{Merchant: request.Merchant, Status: collector.StatusSuccess}
		}),
	})

	result := registry.Search(context.Background(), collector.SearchRequest{
		Merchant: "abcmart",
		Query:    "구두",
		Filters:  collector.SearchFilters{PriceMin: &priceMin, Sizes: []string{"270"}},
	})
	if !called || result.Merchant != "abcmart" || result.Status != collector.StatusSuccess {
		t.Fatalf("result = %#v, called = %v", result, called)
	}
	if result.Query != "구두" || result.Filters.PriceMin == nil || *result.Filters.PriceMin != priceMin || len(result.Filters.Sizes) != 1 {
		t.Fatalf("검색 요청 문맥이 결과에 보존되지 않았습니다: %#v", result)
	}
}

// TestSearchRegistryRejectsUnknownMerchant는 미등록 판매처를 외부 요청 없이 unsupported로 반환하는지 검증한다.
func TestSearchRegistryRejectsUnknownMerchant(t *testing.T) {
	fixedNow := time.Date(2026, 7, 19, 12, 0, 0, 0, time.FixedZone("KST", 9*60*60))
	registry := collector.NewSearchRegistryWithClock(nil, func() time.Time { return fixedNow })

	result := registry.Search(context.Background(), collector.SearchRequest{
		RequestID: "unknown-001",
		Merchant:  "other-shop",
	})
	if result.Status != collector.StatusUnsupported || result.CollectedAt != fixedNow {
		t.Fatalf("result = %#v", result)
	}
	if len(result.Errors) != 1 || result.Errors[0].Code != "MERCHANT_UNSUPPORTED" {
		t.Fatalf("errors = %#v", result.Errors)
	}
}

// TestSearchRegistryRoutesSelectedPage는 page=2를 등록된 PageSearcher에 전달하는지 검증한다.
func TestSearchRegistryRoutesSelectedPage(t *testing.T) {
	searcher := &registryPageSearcher{}
	registry := collector.NewSearchRegistry(map[string]collector.Searcher{"abcmart": searcher})

	result := registry.SearchPage(context.Background(), collector.SearchRequest{
		RequestID: "page-002", Merchant: "abcmart", Query: "구두",
	}, 2)

	if result.Status != collector.StatusSuccess || searcher.page != 2 {
		t.Fatalf("result=%#v page=%d", result, searcher.page)
	}
}
