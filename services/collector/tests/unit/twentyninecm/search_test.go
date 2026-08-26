package twentyninecm_test

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

// roundTripFunc는 함수로 HTTP 응답을 제어하는 테스트 전송기다.
type roundTripFunc func(*http.Request) (*http.Response, error)

// RoundTrip은 등록된 함수에 HTTP 요청을 전달한다.
func (f roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return f(request)
}

// TestSearcherSearch는 저장 JSON을 공통 상품으로 변환하고 공개 검색 요청 형식을 지키는지 검증한다.
func TestSearcherSearch(t *testing.T) {
	collectedAt := time.Date(2026, 7, 20, 11, 0, 0, 0, time.FixedZone("KST", 9*60*60))
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method != http.MethodPost || request.URL.Hostname() != "display-bff-api.29cm.co.kr" {
			t.Errorf("request = %s %s", request.Method, request.URL.String())
		}
		if request.Header.Get("User-Agent") != "PurchaseResearchAgent/0.1 (+public product research; low rate)" {
			t.Errorf("User-Agent = %q", request.Header.Get("User-Agent"))
		}
		var body struct {
			Keyword     string `json:"keyword"`
			PageType    string `json:"pageType"`
			SortType    string `json:"sortType"`
			PageRequest struct {
				Page int `json:"page"`
				Size int `json:"size"`
			} `json:"pageRequest"`
		}
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		if body.Keyword != "구두" || body.PageType != "SRP" || body.SortType != "RECOMMENDED" || body.PageRequest.Page != 1 || body.PageRequest.Size != 10 {
			t.Errorf("request body = %#v", body)
		}
		return jsonResponse(http.StatusOK, readFixture(t)), nil
	})}

	result := twentyninecm.NewSearcherWithClient(client, func() time.Time { return collectedAt }, 0).Search(
		context.Background(),
		validRequest(),
	)

	if result.Status != collector.StatusSuccess || result.Merchant != "29cm" || len(result.Products) != 2 {
		t.Fatalf("result = %#v", result)
	}
	if result.TotalCount == nil || *result.TotalCount != 5452 || result.HasNext == nil || !*result.HasNext {
		t.Errorf("pagination = totalCount %v, hasNext %v", result.TotalCount, result.HasNext)
	}
	product := result.Products[0]
	if product.ExternalID != "2468262" || product.Name != "[29EDITION] BELLA SLINGBACK / BLACK" {
		t.Errorf("product identity = %#v", product)
	}
	if product.Brand == nil || *product.Brand != "기호" || product.Price == nil || product.Price.Amount != 100_960 {
		t.Errorf("product facts = %#v", product)
	}
	if len(product.CategoryPath) != 3 || product.CategoryPath[2] != "플랫" || product.Rating == nil || *product.Rating != 4.5 {
		t.Errorf("product evidence = %#v", product)
	}
	if product.ReviewCount == nil || *product.ReviewCount != 753 || product.Provenance.SourceURL == "" {
		t.Errorf("product provenance = %#v", product)
	}
}

// TestSearcherAppliesSupportedFilters는 가격과 품절 제외 조건을 검색 결과에 적용하는지 검증한다.
func TestSearcherAppliesSupportedFilters(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, readFixture(t)), nil
	})}
	request := validRequest()
	maxPrice := 120_000
	request.Filters.PriceMax = &maxPrice
	request.Filters.InStockOnly = true

	result := twentyninecm.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), request)
	if result.Status != collector.StatusSuccess || len(result.Products) != 1 || result.Products[0].ExternalID != "2468262" {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherReportsBlockedResponse는 접근 거부를 blocked 상태로 숨김없이 반환하는지 검증한다.
func TestSearcherReportsBlockedResponse(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusForbidden, []byte(`{"meta":{"result":"FAIL"}}`)), nil
	})}

	result := twentyninecm.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusBlocked || len(result.Errors) != 1 || result.Errors[0].Code != "29CM_HTTP_ERROR" {
		t.Fatalf("result = %#v", result)
	}
}

// TestSearcherReportsMissingPagination은 성공 응답에서 pagination이 사라지면 구조 변경 오류로 반환하는지 검증한다.
func TestSearcherReportsMissingPagination(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusOK, []byte(`{"meta":{"result":"SUCCESS"},"data":{"list":[]}}`)), nil
	})}

	result := twentyninecm.NewSearcherWithClient(client, time.Now, 0).Search(context.Background(), validRequest())
	if result.Status != collector.StatusUnsupported || len(result.Errors) != 1 || result.Errors[0].Code != "29CM_RESPONSE_INVALID" {
		t.Fatalf("result = %#v", result)
	}
}

// validRequest는 29CM 검색 테스트에서 재사용할 정상 요청을 생성한다.
func validRequest() collector.SearchRequest {
	return collector.SearchRequest{
		RequestID:   "29cm-research-001",
		Merchant:    "29cm",
		Query:       "구두",
		RequestedAt: time.Now(),
		Limit:       10,
		Locale:      "ko-KR",
		Currency:    "KRW",
	}
}

// jsonResponse는 테스트용 JSON HTTP 응답을 생성한다.
func jsonResponse(status int, body []byte) *http.Response {
	return &http.Response{
		StatusCode: status,
		Body:       io.NopCloser(strings.NewReader(string(body))),
		Header:     http.Header{"Content-Type": []string{"application/json"}},
	}
}

// readFixture는 저장된 29CM 공개 검색 응답 예제를 읽는다.
func readFixture(t *testing.T) []byte {
	t.Helper()
	body, err := os.ReadFile("../../../testdata/twentyninecm/search-items.json")
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	return body
}
