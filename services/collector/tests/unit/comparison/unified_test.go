package comparison_test

import (
	"bytes"
	"encoding/json"
	"os"
	"testing"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/comparison"
)

// TestSharedExamplesPassGoValidator는 전달받은 ABC마트/29CM 예제 20건을 Go 계약 자료형과 validator로 검사한다.
func TestSharedExamplesPassGoValidator(t *testing.T) {
	file, err := os.Open("../../../../../contracts/collector/unified/examples/unified_구두_top20_20260803_002024.json")
	if err != nil {
		t.Fatalf("open examples: %v", err)
	}
	defer file.Close()
	decoder := json.NewDecoder(file)
	decoder.DisallowUnknownFields()
	var products []comparison.UnifiedProduct
	if err := decoder.Decode(&products); err != nil {
		t.Fatalf("decode examples: %v", err)
	}
	if len(products) != 20 {
		t.Fatalf("product count = %d", len(products))
	}
	for _, product := range products {
		if err := product.Validate(); err != nil {
			t.Fatalf("product %s: %v", product.SourceProductID, err)
		}
	}
}

// TestFromCollectorProductDoesNotInventFacts는 운영 계약에 없는 원가와 할인율을 빈 값으로 유지하는지 검증한다.
func TestFromCollectorProductDoesNotInventFacts(t *testing.T) {
	brand := "호킨스"
	size := "270"
	color := "BLACK"
	product := collector.Product{
		ExternalID: "101", Name: "페니 로퍼", Brand: &brand,
		CategoryPath: []string{"신발", "구두", "로퍼"}, ProductURL: "https://example.com/product/101",
		ImageURLs: []string{"https://example.com/101.jpg"}, Price: &collector.Money{Amount: 69_000, Currency: "KRW"},
		StockStatus: collector.StockAvailable,
		Options:     []collector.Option{{Label: "270 / BLACK", Size: &size, Color: &color}},
		Reviews:     []collector.Review{},
	}

	unified := comparison.FromCollectorProduct(product, "abcmart")

	if unified.Price != "69,000원" || unified.PriceOriginal != "" || unified.DiscountPercent != nil {
		t.Fatalf("price facts = %#v", unified)
	}
	if len(unified.Options.Sizes) != 1 || unified.Options.Sizes[0] != "270" {
		t.Fatalf("options = %#v", unified.Options)
	}
	if err := unified.Validate(); err != nil {
		t.Fatalf("validate: %v", err)
	}
	body, err := json.Marshal(unified)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if !bytes.Contains(body, []byte(`"images":[`)) || !bytes.Contains(body, []byte(`"reviews":[`)) {
		t.Fatalf("배열 필드가 null로 변환됐습니다: %s", body)
	}
}

// TestUnifiedProductRejectsEmptyIdentifier는 빈 상품 ID가 계약 검증에서 거절되는지 확인한다.
func TestUnifiedProductRejectsEmptyIdentifier(t *testing.T) {
	product := comparison.UnifiedProduct{Title: "상품", Link: "https://example.com", Site: "abcmart", Images: []string{}, Options: comparison.UnifiedOptions{Colors: []string{}, Sizes: []string{}}, Reviews: []comparison.UnifiedReview{}}
	if err := product.Validate(); err == nil {
		t.Fatal("빈 source_product_id가 검증을 통과했습니다")
	}
}

// TestUnifiedProductRejectsNullArrays는 JSON Schema의 배열 필드가 null이면 거절되는지 검증한다.
func TestUnifiedProductRejectsNullArrays(t *testing.T) {
	product := comparison.UnifiedProduct{SourceProductID: "1", Title: "상품", Link: "https://example.com", Site: "abcmart"}
	if err := product.Validate(); err == nil {
		t.Fatal("null 배열 필드가 검증을 통과했습니다")
	}
}
