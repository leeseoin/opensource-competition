package abcmart_test

import (
	"context"
	"net/http"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
)

// staticRenderer는 실제 browser 없이 rendering HTML을 반환하는 검증용 renderer다.
type staticRenderer struct {
	body []byte
	err  error
}

// Render는 테스트가 지정한 HTML 또는 오류를 반환한다.
func (r staticRenderer) Render(context.Context, string) ([]byte, error) {
	return r.body, r.err
}

// TestParseSearchHTML은 ABC마트 상품 카드에서 공통 검증 필드를 추출하는지 검증한다.
func TestParseSearchHTML(t *testing.T) {
	html := []byte(`<ul><li class="col-list-item prod-item" data-product-no="1010110882">
<a class="prod-link" href="/product?prdtNo=1010110882"></a>
<span class="prod-brand">호킨스</span><span class="prod-name"><span class="badge-gender" aria-label="남성">남성</span>페니 로퍼</span>
<span class="price-cost">69,000</span><span class="price-unit">원</span>
<div class="img-wrap"><img src="https://image.a-rt.com/art/product/example-1010110882.jpg?shrink=590:590"></div>
</li></ul>`)

	products := abcmart.ParseSearchHTML(html)
	product, exists := products["1010110882"]
	if !exists || product.Title != "페니 로퍼" || product.Brand != "호킨스" || product.Price != "69,000" {
		t.Fatalf("products = %#v", products)
	}
}

// TestSearcherAttachesMatchedVerification는 JSON 검색 결과에 rendering HTML 일치 검증을 붙이는지 검증한다.
func TestSearcherAttachesMatchedVerification(t *testing.T) {
	fixture := readABCFixture(t)
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, fixture), nil
	})}
	renderer := staticRenderer{body: []byte(`<li class="col-list-item prod-item" data-product-no="1010110882">
<span class="prod-brand">호킨스</span><span class="prod-name">페니 로퍼</span>
<span class="price-cost">69,000</span><div class="img-wrap"><img src="https://image.a-rt.com/art/product/example-1010110882.jpg"></div>
<a class="prod-link" href="/product?prdtNo=1010110882"></a></li>`)}
	request := validRequest()
	request.Limit = 1
	searcher := abcmart.NewSearcherWithDependencies(client, time.Now, 0, renderer, nil)

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
