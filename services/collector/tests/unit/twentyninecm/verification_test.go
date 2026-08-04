package twentyninecm_test

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

const detailHTML = `<html><head><script type="application/ld+json">{
"@context":"https://schema.org","@type":"Product","sku":"2468262",
"name":"[29EDITION] BELLA SLINGBACK / BLACK","brand":{"@type":"Brand","name":"기호"},
"image":["https://img.29cm.co.kr/item/example-2468262.jpg"],
"offers":{"@type":"Offer","url":"https://product.29cm.co.kr/catalog/2468262","price":134400,
"priceSpecification":{"@type":"UnitPriceSpecification","price":168000}}
}</script></head></html>`

// TestParseProductJSONLD는 29CM 상세 HTML의 Product JSON-LD에서 판매가와 정상가를 추출하는지 검증한다.
func TestParseProductJSONLD(t *testing.T) {
	product, err := twentyninecm.ParseProductJSONLD([]byte(detailHTML), "https://product.29cm.co.kr/catalog/2468262")
	if err != nil {
		t.Fatalf("ParseProductJSONLD() error = %v", err)
	}
	if product.ExternalID != "2468262" || product.Price != "134400" || product.OriginalPrice != "168000" || product.DiscountPercent == nil || *product.DiscountPercent != 20 {
		t.Fatalf("product = %#v", product)
	}
}

// TestSearcherAttachesMatchedDetailVerification는 검색 JSON과 상품 상세 JSON-LD를 전체 비교해 일치 결과를 붙이는지 검증한다.
func TestSearcherAttachesMatchedDetailVerification(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method == http.MethodPost {
			return jsonResponse(http.StatusOK, readFixture(t)), nil
		}
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader(detailHTML)),
			Header:     http.Header{"Content-Type": []string{"text/html"}},
			Request:    request,
		}, nil
	})}
	request := validRequest()
	request.Limit = 1
	searcher := twentyninecm.NewSearcherWithDependencies(client, time.Now, 0, true, nil)

	result := searcher.Search(context.Background(), request)

	if result.Status != collector.StatusSuccess || len(result.Products) != 1 || result.Products[0].Verification == nil {
		t.Fatalf("result = %#v", result)
	}
	if result.Products[0].Verification.Status != collector.VerificationMatched {
		t.Fatalf("verification = %#v", result.Products[0].Verification)
	}
	if result.VerificationSummary == nil || result.VerificationSummary.Matched != 1 || result.VerificationSummary.Total != 1 {
		t.Fatalf("verification summary = %#v", result.VerificationSummary)
	}
}
