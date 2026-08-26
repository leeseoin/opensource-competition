package integration_test

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

// TestTwentyNineCMActualSearch는 명시적으로 활성화했을 때만 실제 29CM 검색 결과 3개를 확인한다.
func TestTwentyNineCMActualSearch(t *testing.T) {
	if os.Getenv("TWENTYNINECM_LIVE_SMOKE") != "1" {
		t.Skip("TWENTYNINECM_LIVE_SMOKE=1일 때만 실제 판매처에 요청합니다")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	result := twentyninecm.NewSearcher(15*time.Second).Search(ctx, collector.SearchRequest{
		RequestID: "29cm-live-001", Merchant: "29cm", Query: "구두", RequestedAt: time.Now(),
		Limit: 3, Locale: "ko-KR", Currency: "KRW",
	})
	if result.Status != collector.StatusSuccess {
		t.Fatalf("status = %s, errors = %#v", result.Status, result.Errors)
	}
	if len(result.Products) != 3 {
		t.Fatalf("products = %d, want 3", len(result.Products))
	}
	if result.TotalCount == nil || *result.TotalCount < len(result.Products) || result.HasNext == nil {
		t.Fatalf("29CM 페이지 정보가 올바르지 않습니다: totalCount=%v, hasNext=%v", result.TotalCount, result.HasNext)
	}
	for _, product := range result.Products {
		if product.ExternalID == "" || product.Name == "" || product.ProductURL == "" || product.Price == nil {
			t.Fatalf("required product fields are missing: %#v", product)
		}
	}
}
