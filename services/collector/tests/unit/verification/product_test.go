package verification_test

import (
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/verification"
)

// TestCompareNormalizesVisibleFormats는 원화 표시, 이미지 query와 상품 URL 형식 차이를 일치로 판단하는지 검증한다.
func TestCompareNormalizesVisibleFormats(t *testing.T) {
	discount := 30
	jsonProduct := verification.Candidate{
		ExternalID: "101", Title: "페니 로퍼", Brand: "호킨스", Price: "69,000원",
		OriginalPrice: "99,000원", DiscountPercent: &discount,
		ImageURL: "https://example.com/shoe.jpg?shrink=590:590",
		Link:     "https://abcmart.a-rt.com/product?prdtNo=101",
	}
	htmlProduct := verification.Candidate{
		ExternalID: "101", Title: " 페니  로퍼 ", Brand: "호킨스", Price: "69000 원",
		OriginalPrice: "99000원", DiscountPercent: &discount,
		ImageURL: "https://example.com/shoe.jpg",
		Link:     "https://abcmart.a-rt.com/product?prdtNo=101&track=test",
	}

	result := verification.Compare(jsonProduct, htmlProduct, "https://example.com/json", "https://example.com/html", time.Now())

	if result.Status != collector.VerificationMatched || len(result.Differences) != 0 {
		t.Fatalf("result = %#v", result)
	}
}

// TestCompareReportsMismatchFields는 상품명과 판매가가 다르면 해당 필드만 불일치로 기록하는지 검증한다.
func TestCompareReportsMismatchFields(t *testing.T) {
	jsonProduct := verification.Candidate{Title: "A", Price: "10000", Link: "https://product.29cm.co.kr/catalog/1"}
	htmlProduct := verification.Candidate{Title: "B", Price: "9000", Link: "https://product.29cm.co.kr/catalog/1"}

	result := verification.Compare(jsonProduct, htmlProduct, "https://example.com/json", "https://example.com/html", time.Now())

	if result.Status != collector.VerificationMismatch || verification.DifferenceFields(result) != "title,price" {
		t.Fatalf("result = %#v", result)
	}
}
