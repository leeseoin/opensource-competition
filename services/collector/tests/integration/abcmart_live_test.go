package integration_test

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
)

// TestABC마트실제검색은 명시적으로 허용한 경우에만 실제 ABC마트 공개 검색 결과를 확인한다.
func TestABC마트실제검색(t *testing.T) {
	if os.Getenv("ABCMART_LIVE_SMOKE") != "1" {
		t.Skip("ABCMART_LIVE_SMOKE=1일 때만 실제 판매처에 접속합니다")
	}

	result := abcmart.NewSearcher(15*time.Second).Search(context.Background(), collector.SearchRequest{
		RequestID:   "live-smoke-001",
		Merchant:    "abcmart",
		Query:       "구두",
		RequestedAt: time.Now(),
		Limit:       1,
		Locale:      "ko-KR",
		Currency:    "KRW",
		Filters: collector.SearchFilters{
			Sizes: []string{"270"},
		},
	})

	if result.Status != collector.StatusSuccess {
		t.Fatalf("Status = %q, errors = %#v", result.Status, result.Errors)
	}
	if len(result.Products) == 0 {
		t.Fatal("실제 ABC마트 검색 결과가 비어 있습니다")
	}
	if len(result.Products[0].Options) == 0 {
		t.Fatal("실제 ABC마트 상품에서 공개 사이즈 재고를 읽지 못했습니다")
	}
	t.Logf(
		"상품 확인: %s, 옵션 %d개 (%s)",
		result.Products[0].Name,
		len(result.Products[0].Options),
		result.Products[0].ProductURL,
	)
}
