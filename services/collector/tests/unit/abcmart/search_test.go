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

// TestSearcherSearch는 저장 JSON을 상품·페이지 결과로 변환하고 검색 조건을 적용하는지 검증한다.
func TestSearcherSearch(t *testing.T) {
	body := readABCFixture(t)
	collectedAt := time.Date(2026, 7, 16, 15, 0, 0, 0, time.FixedZone("KST", 9*60*60))
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.Path != "/display/search-word/result-total/list" || request.URL.Query().Get("searchWord") != "구두" || request.URL.Query().Get("perPage") != "30" {
			t.Errorf("query = %q", request.URL.RawQuery)
		}
		if request.Header.Get("Accept") != "application/json" {
			t.Errorf("Accept = %q", request.Header.Get("Accept"))
		}
		return jsonResponse(http.StatusOK, body), nil
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
	if result.TotalCount == nil || *result.TotalCount != 1650 || result.HasNext == nil || !*result.HasNext {
		t.Errorf("pagination = totalCount %v, hasNext %v", result.TotalCount, result.HasNext)
	}
	product := result.Products[0]
	if product.Name != "페니 로퍼" || product.Price == nil || product.Price.Amount != 69_000 {
		t.Errorf("product = %#v", product)
	}
	if len(product.Options) != 7 || product.Options[4].Stock != collector.StockAvailable {
		t.Errorf("options = %#v", product.Options)
	}
	if len(product.CategoryPath) != 3 || product.ReviewCount == nil || *product.ReviewCount != 4 {
		t.Errorf("product metadata = %#v", product)
	}
}

// TestSearcherReportsChangedPage는 SEARCH 목록이 사라진 JSON을 정상 결과로 숨기지 않는지 검증한다.
func TestSearcherReportsChangedPage(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, []byte(`{}`)), nil
	})}
	result := abcmart.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusUnsupported || len(result.Errors) != 1 {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherReportsMissingPagination은 상품 배열만 있고 페이지 정보가 사라진 응답을 구조 변경으로 감지하는지 검증한다.
func TestSearcherReportsMissingPagination(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, []byte(`{"SEARCH":[]}`)), nil
	})}
	result := abcmart.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusUnsupported || len(result.Errors) != 1 || result.Errors[0].Code != "ABCMART_PAGE_CHANGED" {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherReportsEmptyLastPage는 검색 결과가 없을 때 전체 수 0과 다음 페이지 없음이 구분되는지 검증한다.
func TestSearcherReportsEmptyLastPage(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, []byte(`{"SEARCH":[],"SEARCH_COUNT":0,"PAGE":{"finalPageNo":1}}`)), nil
	})}
	result := abcmart.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusSuccess || len(result.Products) != 0 {
		t.Fatalf("result = %#v", result)
	}
	if result.TotalCount == nil || *result.TotalCount != 0 || result.HasNext == nil || *result.HasNext {
		t.Fatalf("pagination = totalCount %v, hasNext %v", result.TotalCount, result.HasNext)
	}
}

// TestSearcherReportsHTTPFailure는 원격 HTTP 오류를 일시 실패로 반환하는지 검증한다.
func TestSearcherReportsHTTPFailure(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusTooManyRequests, []byte("too many requests")), nil
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
		return jsonResponse(http.StatusOK, readABCFixture(t)), nil
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

// TestSearcherRequestsSelectedPage는 대량 수집용 SearchPage가 지정 페이지를 URL에 넣는지 검증한다.
func TestSearcherRequestsSelectedPage(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.Query().Get("page") != "2" {
			t.Fatalf("page = %s", request.URL.Query().Get("page"))
		}
		return jsonResponse(http.StatusOK, []byte(`{"SEARCH":[],"SEARCH_COUNT":60,"PAGE":{"finalPageNo":2}}`)), nil
	})}
	searcher := abcmart.NewSearcherWithClient(client, time.Now, 0)

	result := searcher.SearchPage(context.Background(), validRequest(), 2)

	if result.Status != collector.StatusSuccess || result.HasNext == nil || *result.HasNext {
		t.Fatalf("status=%s hasNext=%v errors=%v", result.Status, result.HasNext, result.Errors)
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

// jsonResponse는 테스트용 HTTP 응답을 생성한다.
func jsonResponse(status int, body []byte) *http.Response {
	return &http.Response{
		StatusCode: status,
		Body:       io.NopCloser(strings.NewReader(string(body))),
		Header:     http.Header{"Content-Type": []string{"application/json"}},
	}
}

// readABCFixture는 저장된 ABC마트 검색 JSON을 읽는다.
func readABCFixture(t *testing.T) []byte {
	t.Helper()
	body, err := os.ReadFile("../../../testdata/abcmart/search-products.json")
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	return body
}
