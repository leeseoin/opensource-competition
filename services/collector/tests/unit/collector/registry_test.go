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

// TestSearchRegistryRoutesMerchant는 판매처 이름에 맞는 검색기가 호출되는지 검증한다.
func TestSearchRegistryRoutesMerchant(t *testing.T) {
	called := false
	registry := collector.NewSearchRegistry(map[string]collector.Searcher{
		"abcmart": registrySearcherFunc(func(_ context.Context, request collector.SearchRequest) collector.SearchResult {
			called = true
			return collector.SearchResult{Merchant: request.Merchant, Status: collector.StatusSuccess}
		}),
	})

	result := registry.Search(context.Background(), collector.SearchRequest{Merchant: "abcmart"})
	if !called || result.Merchant != "abcmart" || result.Status != collector.StatusSuccess {
		t.Fatalf("result = %#v, called = %v", result, called)
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
