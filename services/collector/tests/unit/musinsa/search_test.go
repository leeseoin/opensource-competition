package musinsa_test

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/musinsa"
)

// roundTripFunc는 함수로 HTTP 응답을 제어하는 무신사 검색 테스트 대역이다.
type roundTripFunc func(*http.Request) (*http.Response, error)

// RoundTrip은 전달받은 함수로 테스트 HTTP 요청을 처리한다.
func (f roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return f(request)
}

// TestSearcherSearch는 검색 JSON을 상품 공통 구조로 변환하고 limit을 적용하는지 검증한다.
func TestSearcherSearch(t *testing.T) {
	fixedNow := time.Date(2026, 7, 19, 12, 0, 0, 0, time.FixedZone("KST", 9*60*60))
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.Query().Get("keyword") != "구두" || request.URL.Query().Get("gf") != "A" {
			t.Fatalf("query = %s", request.URL.RawQuery)
		}
		if request.Header.Get("User-Agent") != "PurchaseResearchAgent/0.1 (+public product research; low rate)" {
			t.Fatalf("user-agent = %q", request.Header.Get("User-Agent"))
		}
		return htmlResponse(http.StatusOK, searchHTML), nil
	})}
	searcher := musinsa.NewSearcherWithClient(client, func() time.Time { return fixedNow }, 0)
	result := searcher.Search(context.Background(), collector.SearchRequest{
		RequestID: "musinsa-001", Merchant: "musinsa", Query: "구두", Limit: 1, Locale: "ko-KR", Currency: "KRW",
	})

	if result.Status != collector.StatusSuccess || result.CollectorVersion != "musinsa-search-v1" {
		t.Fatalf("result = %#v", result)
	}
	if len(result.Products) != 1 {
		t.Fatalf("products = %#v", result.Products)
	}
	product := result.Products[0]
	if product.ExternalID != "5877464" || product.Name != "보그스 더비슈즈" || product.Price == nil || product.Price.Amount != 53800 {
		t.Fatalf("product = %#v", product)
	}
	if product.Brand == nil || *product.Brand != "더노피" || product.ReviewCount == nil || *product.ReviewCount != 64 {
		t.Fatalf("product metadata = %#v", product)
	}
	if product.Rating == nil || *product.Rating != 4.7 || product.Provenance.CollectedAt != fixedNow {
		t.Fatalf("rating/provenance = %#v", product)
	}
}

// TestSearcherReportsChangedPage는 검색 JSON이 사라졌을 때 구조 변경 오류를 반환하는지 검증한다.
func TestSearcherReportsChangedPage(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return htmlResponse(http.StatusOK, `<html><body>changed</body></html>`), nil
	})}
	result := musinsa.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), collector.SearchRequest{
		RequestID: "musinsa-002", Merchant: "musinsa", Query: "구두", Limit: 10, Locale: "ko-KR", Currency: "KRW",
	})
	if result.Status != collector.StatusUnsupported || len(result.Errors) != 1 || result.Errors[0].Code != "MUSINSA_PAGE_CHANGED" {
		t.Fatalf("result = %#v", result)
	}
}

// htmlResponse는 문자열 body를 가진 테스트 HTTP 응답을 생성한다.
func htmlResponse(status int, body string) *http.Response {
	return &http.Response{StatusCode: status, Header: make(http.Header), Body: io.NopCloser(strings.NewReader(body))}
}

const searchHTML = `<html><body><script id="__NEXT_DATA__" type="application/json">{
  "props":{"pageProps":{"dehydratedState":{"queries":[{"state":{"data":{"pages":[{"items":[
    {"goodsNo":5877464,"goodsName":"보그스 더비슈즈","goodsLinkUrl":"https://www.musinsa.com/products/5877464","thumbnail":"https://image.example/5877464.jpg","isSoldOut":false,"price":53800,"brandName":"더노피","reviewCount":64,"reviewScore":94},
    {"goodsNo":5404863,"goodsName":"독일군 스니커즈","goodsLinkUrl":"https://www.musinsa.com/products/5404863","thumbnail":"","isSoldOut":false,"price":69000,"brandName":"코어오브알케미","reviewCount":41,"reviewScore":96}
  ]}]}}}]}}},"page":"/search/[[...tabId]]"
}</script></body></html>`
