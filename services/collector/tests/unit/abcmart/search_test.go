package abcmart_test

import (
	"context"
	"io"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
)

// roundTripFunc는 함수로 HTTP 응답을 제어하는 테스트 전송기다.
type roundTripFunc func(*http.Request) (*http.Response, error)

// RoundTrip은 등록된 함수에 HTTP 요청을 전달한다.
func (f roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return f(request)
}

// TestSearcherSearch는 저장 HTML을 상품 결과로 변환하고 검색 조건을 적용하는지 검증한다.
func TestSearcherSearch(t *testing.T) {
	body := readABCFixture(t)
	collectedAt := time.Date(2026, 7, 16, 15, 0, 0, 0, time.FixedZone("KST", 9*60*60))
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.Query().Get("searchWord") != "구두" || request.URL.Query().Get("perPage") != "30" {
			t.Errorf("query = %q", request.URL.RawQuery)
		}
		return htmlResponse(http.StatusOK, body), nil
	})}
	maxPrice := 80_000

	result := abcmart.NewSearcherWithClient(client, func() time.Time { return collectedAt }, 0).Search(
		context.Background(),
		collector.SearchRequest{
			RequestID:   "research-001",
			Merchant:    "abcmart",
			Query:       "구두",
			RequestedAt: collectedAt,
			Limit:       10,
			Locale:      "ko-KR",
			Currency:    "KRW",
			Filters: collector.SearchFilters{
				PriceMax:    &maxPrice,
				InStockOnly: true,
				Sizes:       []string{"270"},
			},
		},
	)

	if result.Status != collector.StatusSuccess || len(result.Products) != 1 {
		t.Fatalf("result = %#v", result)
	}
	product := result.Products[0]
	if product.Name != "페니 로퍼" || product.Price == nil || product.Price.Amount != 69_000 {
		t.Errorf("product = %#v", product)
	}
	if len(product.Options) != 7 || product.Options[4].Stock != collector.StockAvailable {
		t.Errorf("options = %#v", product.Options)
	}
}

// TestSearcherReportsChangedPage는 상품 목록이 사라진 HTML을 정상 결과로 숨기지 않는지 검증한다.
func TestSearcherReportsChangedPage(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return htmlResponse(http.StatusOK, []byte("<html></html>")), nil
	})}
	result := abcmart.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusUnsupported || len(result.Errors) != 1 {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherReportsHTTPFailure는 원격 HTTP 오류를 일시 실패로 반환하는지 검증한다.
func TestSearcherReportsHTTPFailure(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return htmlResponse(http.StatusTooManyRequests, []byte("too many requests")), nil
	})}
	result := abcmart.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusTemporarilyUnavailable || len(result.Errors) != 1 || !result.Errors[0].Retryable {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherStopsCanceledRateLimitWait는 요청 간격 대기 중 취소되면 두 번째 HTTP 요청을 보내지 않는지 검증한다.
func TestSearcherStopsCanceledRateLimitWait(t *testing.T) {
	var calls atomic.Int32
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		calls.Add(1)
		return htmlResponse(http.StatusOK, readABCFixture(t)), nil
	})}
	searcher := abcmart.NewSearcherWithClient(client, time.Now, time.Hour)
	if result := searcher.Search(context.Background(), validRequest()); result.Status != collector.StatusSuccess {
		t.Fatalf("first result = %#v", result)
	}

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result := searcher.Search(ctx, validRequest())
	if result.Status != collector.StatusTemporarilyUnavailable || calls.Load() != 1 {
		t.Fatalf("second result = %#v, HTTP calls = %d", result, calls.Load())
	}
}

// validRequest는 ABC마트 검색 테스트에서 재사용할 정상 요청을 생성한다.
func validRequest() collector.SearchRequest {
	return collector.SearchRequest{
		RequestID:   "research-001",
		Merchant:    "abcmart",
		Query:       "구두",
		RequestedAt: time.Now(),
		Limit:       10,
		Locale:      "ko-KR",
		Currency:    "KRW",
	}
}

// htmlResponse는 테스트용 HTTP 응답을 생성한다.
func htmlResponse(status int, body []byte) *http.Response {
	return &http.Response{
		StatusCode: status,
		Body:       io.NopCloser(strings.NewReader(string(body))),
		Header:     make(http.Header),
	}
}

// readABCFixture는 저장된 ABC마트 검색 HTML을 읽는다.
func readABCFixture(t *testing.T) []byte {
	t.Helper()
	body, err := os.ReadFile("../../../testdata/abcmart/search-goods.html")
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	return body
}
