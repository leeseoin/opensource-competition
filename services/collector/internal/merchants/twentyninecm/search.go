// Package twentyninecm은 29CM 공개 검색 화면의 상품 데이터를 공통 수집 결과로 변환한다.
package twentyninecm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"sync"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

const (
	merchantName       = "29cm"
	collectorVersion   = "29cm-search-v1"
	searchPageEndpoint = "https://www.29cm.co.kr/store/search"
	searchAPIEndpoint  = "https://display-bff-api.29cm.co.kr/api/v1/listing/items?colorchipVariant=control"
	maxSearchBodyBytes = 4 * 1024 * 1024
	minRequestInterval = time.Second
)

// Searcher는 29CM 공개 검색 화면에서 사용하는 상품 응답을 공통 결과로 변환한다.
type Searcher struct {
	client          *http.Client
	now             func() time.Time
	minInterval     time.Duration
	rateMu          sync.Mutex
	nextRequestTime time.Time
}

// searchRequestBody는 29CM 검색 화면이 상품 목록을 요청할 때 사용하는 최소 공개 조건을 표현한다.
type searchRequestBody struct {
	Keyword     string      `json:"keyword"`
	PageType    string      `json:"pageType"`
	SortType    string      `json:"sortType"`
	PageRequest pageRequest `json:"pageRequest"`
}

// pageRequest는 한 번의 검색에서 요청할 페이지와 상품 개수를 표현한다.
type pageRequest struct {
	Page int `json:"page"`
	Size int `json:"size"`
}

// searchResponse는 29CM 검색 응답에서 상품 변환에 필요한 필드만 표현한다.
type searchResponse struct {
	Meta struct {
		Result string `json:"result"`
	} `json:"meta"`
	Data struct {
		List       []searchItem `json:"list"`
		Pagination *struct {
			Page       int   `json:"page"`
			Size       int   `json:"size"`
			HasNext    *bool `json:"hasNext"`
			TotalCount *int  `json:"totalCount"`
		} `json:"pagination"`
	} `json:"data"`
}

// searchItem은 29CM 검색 결과의 상품 기본정보와 공개 카테고리를 표현한다.
type searchItem struct {
	ItemID   int    `json:"itemId"`
	ItemType string `json:"itemType"`
	ItemURL  struct {
		WebLink string `json:"webLink"`
	} `json:"itemUrl"`
	ItemInfo struct {
		ProductName  string  `json:"productName"`
		ThumbnailURL string  `json:"thumbnailUrl"`
		IsSoldOut    bool    `json:"isSoldOut"`
		DisplayPrice int     `json:"displayPrice"`
		BrandName    string  `json:"brandName"`
		ReviewScore  float64 `json:"reviewScore"`
		ReviewCount  int     `json:"reviewCount"`
	} `json:"itemInfo"`
	ItemEvent struct {
		EventProperties struct {
			LargeCategoryName  string `json:"largeCategoryName"`
			MiddleCategoryName string `json:"middleCategoryName"`
			SmallCategoryName  string `json:"smallCategoryName"`
		} `json:"eventProperties"`
	} `json:"itemEvent"`
}

// NewSearcher는 timeout과 redirect 제한이 설정된 HTTP client로 29CM 검색기를 생성한다.
func NewSearcher(timeout time.Duration) *Searcher {
	return NewSearcherWithClient(&http.Client{
		Timeout:       timeout,
		CheckRedirect: checkRedirect,
	}, time.Now, minRequestInterval)
}

// NewSearcherWithClient는 테스트용 HTTP client, 시계, 요청 간격을 주입해 29CM 검색기를 생성한다.
func NewSearcherWithClient(client *http.Client, now func() time.Time, minInterval time.Duration) *Searcher {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second, CheckRedirect: checkRedirect}
	}
	if now == nil {
		now = time.Now
	}
	return &Searcher{client: client, now: now, minInterval: minInterval}
}

// Search는 검색어를 29CM 공개 검색 데이터 요청으로 전달하고 상품 기본정보를 공통 결과로 변환한다.
func (s *Searcher) Search(ctx context.Context, request collector.SearchRequest) collector.SearchResult {
	collectedAt := s.now()
	searchPageURL := buildSearchPageURL(request)
	result := newResult(request, collectedAt)

	if request.Currency != "KRW" {
		return failedResult(result, collector.StatusUnsupported, "CURRENCY_UNSUPPORTED", "29CM 검색 가격은 KRW만 지원합니다", false, searchPageURL)
	}
	if err := s.waitForTurn(ctx); err != nil {
		return failedResult(result, collector.StatusTemporarilyUnavailable, "29CM_REQUEST_CANCELED", err.Error(), true, searchPageURL)
	}

	body, err := json.Marshal(searchRequestBody{
		Keyword:  request.Query,
		PageType: "SRP",
		SortType: "RECOMMENDED",
		PageRequest: pageRequest{
			Page: 1,
			Size: request.Limit,
		},
	})
	if err != nil {
		return failedResult(result, collector.StatusUnsupported, "REQUEST_ENCODE_FAILED", err.Error(), false, searchPageURL)
	}

	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodPost, searchAPIEndpoint, bytes.NewReader(body))
	if err != nil {
		return failedResult(result, collector.StatusUnsupported, "REQUEST_BUILD_FAILED", err.Error(), false, searchPageURL)
	}
	httpRequest.Header.Set("User-Agent", "PurchaseResearchAgent/0.1 (+public product research; low rate)")
	httpRequest.Header.Set("Accept", "application/json")
	httpRequest.Header.Set("Content-Type", "application/json")
	httpRequest.Header.Set("Origin", "https://www.29cm.co.kr")
	httpRequest.Header.Set("Referer", searchPageURL)

	response, err := s.client.Do(httpRequest)
	if err != nil {
		return failedResult(result, collector.StatusTemporarilyUnavailable, "29CM_REQUEST_FAILED", err.Error(), true, searchPageURL)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		status := collector.StatusUnsupported
		retryable := response.StatusCode == http.StatusTooManyRequests || response.StatusCode >= 500
		if response.StatusCode == http.StatusUnauthorized || response.StatusCode == http.StatusForbidden {
			status = collector.StatusBlocked
		} else if retryable {
			status = collector.StatusTemporarilyUnavailable
		}
		return failedResult(result, status, "29CM_HTTP_ERROR", fmt.Sprintf("29CM 검색 결과가 HTTP %d를 반환했습니다", response.StatusCode), retryable, searchPageURL)
	}

	responseBody, err := io.ReadAll(io.LimitReader(response.Body, maxSearchBodyBytes+1))
	if err != nil {
		return failedResult(result, collector.StatusTemporarilyUnavailable, "29CM_RESPONSE_READ_FAILED", err.Error(), true, searchPageURL)
	}
	if len(responseBody) > maxSearchBodyBytes {
		return failedResult(result, collector.StatusUnsupported, "29CM_RESPONSE_TOO_LARGE", "29CM 검색 응답이 허용 크기를 넘었습니다", false, searchPageURL)
	}

	var payload searchResponse
	if err := json.Unmarshal(responseBody, &payload); err != nil {
		return failedResult(result, collector.StatusUnsupported, "29CM_RESPONSE_INVALID", fmt.Sprintf("29CM 검색 JSON 해석 실패: %v", err), false, searchPageURL)
	}
	if payload.Meta.Result != "SUCCESS" {
		return failedResult(result, collector.StatusUnsupported, "29CM_RESPONSE_FAILED", "29CM 검색 응답이 성공 상태가 아닙니다", false, searchPageURL)
	}
	if payload.Data.Pagination == nil || payload.Data.Pagination.TotalCount == nil || payload.Data.Pagination.HasNext == nil {
		return failedResult(result, collector.StatusUnsupported, "29CM_RESPONSE_INVALID", "29CM 검색 JSON에서 페이지 정보를 찾지 못했습니다", false, searchPageURL)
	}
	if *payload.Data.Pagination.TotalCount < 0 {
		return failedResult(result, collector.StatusUnsupported, "29CM_RESPONSE_INVALID", "29CM 검색 JSON의 전체 상품 수가 음수입니다", false, searchPageURL)
	}
	totalCount := *payload.Data.Pagination.TotalCount
	hasNext := *payload.Data.Pagination.HasNext
	result.TotalCount = &totalCount
	result.HasNext = &hasNext

	for _, item := range payload.Data.List {
		if item.ItemType != "PRODUCT" || item.ItemID <= 0 || item.ItemInfo.ProductName == "" || item.ItemInfo.DisplayPrice < 0 {
			continue
		}
		if request.Filters.InStockOnly && item.ItemInfo.IsSoldOut {
			continue
		}
		if request.Filters.PriceMin != nil && item.ItemInfo.DisplayPrice < *request.Filters.PriceMin {
			continue
		}
		if request.Filters.PriceMax != nil && item.ItemInfo.DisplayPrice > *request.Filters.PriceMax {
			continue
		}
		result.Products = append(result.Products, toProduct(item, searchPageURL, collectedAt))
		if len(result.Products) >= request.Limit {
			break
		}
	}

	if len(request.Filters.Categories) > 0 || len(request.Filters.Sizes) > 0 || len(request.Filters.Colors) > 0 || len(request.Filters.Attributes) > 0 {
		result.Status = collector.StatusPartial
		result.Warnings = append(result.Warnings, collector.Issue{
			Code:      "FILTERS_PARTIALLY_SUPPORTED",
			Message:   "현재 29CM 검색은 가격과 검색 단계 품절 조건만 지원합니다",
			Retryable: false,
		})
	}

	return result
}

// waitForTurn은 한 Searcher가 29CM에 보내는 요청 사이에 최소 간격을 보장한다.
func (s *Searcher) waitForTurn(ctx context.Context) error {
	if s.minInterval <= 0 {
		return nil
	}
	s.rateMu.Lock()
	now := time.Now()
	scheduled := now
	if s.nextRequestTime.After(now) {
		scheduled = s.nextRequestTime
	}
	s.nextRequestTime = scheduled.Add(s.minInterval)
	s.rateMu.Unlock()

	wait := time.Until(scheduled)
	if wait <= 0 {
		return nil
	}
	timer := time.NewTimer(wait)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

// buildSearchPageURL은 검색 결과를 사람이 다시 확인할 수 있는 29CM 공개 페이지 주소를 생성한다.
func buildSearchPageURL(request collector.SearchRequest) string {
	values := url.Values{}
	values.Set("keyword", request.Query)
	values.Set("sort", "RECOMMENDED")
	return searchPageEndpoint + "?" + values.Encode()
}

// checkRedirect는 검색 데이터 호스트 안에서 최대 5회까지만 redirect를 허용한다.
func checkRedirect(request *http.Request, previous []*http.Request) error {
	if len(previous) >= 5 {
		return fmt.Errorf("29CM redirect 횟수가 5회를 넘었습니다")
	}
	if request.URL.Hostname() != "display-bff-api.29cm.co.kr" {
		return fmt.Errorf("허용되지 않은 redirect host입니다: %s", request.URL.Hostname())
	}
	return nil
}

// toProduct는 29CM 검색 상품을 개인정보가 없는 Collector 공통 상품으로 변환한다.
func toProduct(item searchItem, sourceURL string, collectedAt time.Time) collector.Product {
	provenance := collector.Provenance{SourceURL: sourceURL, CollectedAt: collectedAt, CollectorVersion: collectorVersion}
	price := collector.Money{Amount: item.ItemInfo.DisplayPrice, Currency: "KRW"}
	productURL := item.ItemURL.WebLink
	if productURL == "" {
		productURL = "https://product.29cm.co.kr/catalog/" + strconv.Itoa(item.ItemID)
	}

	var brand *string
	if item.ItemInfo.BrandName != "" {
		value := item.ItemInfo.BrandName
		brand = &value
	}
	imageURLs := []string{}
	if item.ItemInfo.ThumbnailURL != "" {
		imageURLs = append(imageURLs, item.ItemInfo.ThumbnailURL)
	}
	stockStatus := collector.StockAvailable
	if item.ItemInfo.IsSoldOut {
		stockStatus = collector.StockOut
	}
	var rating *float64
	if item.ItemInfo.ReviewCount > 0 && item.ItemInfo.ReviewScore > 0 {
		value := item.ItemInfo.ReviewScore
		rating = &value
	}
	reviewCount := item.ItemInfo.ReviewCount

	return collector.Product{
		ExternalID:   strconv.Itoa(item.ItemID),
		Name:         item.ItemInfo.ProductName,
		Brand:        brand,
		CategoryPath: categoryPath(item),
		ProductURL:   productURL,
		ImageURLs:    imageURLs,
		Price:        &price,
		Shipping:     collector.Shipping{Fee: nil, Summary: nil, Provenance: provenance},
		StockStatus:  stockStatus,
		Rating:       rating,
		ReviewCount:  &reviewCount,
		Options:      []collector.Option{},
		Measurements: map[string]collector.MeasurementValue{},
		Reviews:      []collector.Review{},
		Provenance:   provenance,
	}
}

// categoryPath는 검색 응답의 대·중·소 카테고리 이름을 빈 값 없이 순서대로 반환한다.
func categoryPath(item searchItem) []string {
	properties := item.ItemEvent.EventProperties
	values := []string{properties.LargeCategoryName, properties.MiddleCategoryName, properties.SmallCategoryName}
	path := make([]string, 0, len(values))
	for _, value := range values {
		if value != "" {
			path = append(path, value)
		}
	}
	return path
}

// newResult는 오류가 없고 상품이 비어 있는 29CM 검색 결과를 생성한다.
func newResult(request collector.SearchRequest, collectedAt time.Time) collector.SearchResult {
	return collector.SearchResult{
		RequestID: request.RequestID, Operation: collector.OperationSearch, Status: collector.StatusSuccess,
		Merchant: merchantName, CollectedAt: collectedAt, CollectorVersion: collectorVersion,
		Products: []collector.Product{}, Warnings: []collector.Issue{}, Errors: []collector.Issue{},
	}
}

// failedResult는 29CM 수집 실패를 빈 정상 결과로 숨기지 않고 지정한 상태와 오류로 반환한다.
func failedResult(result collector.SearchResult, status, code, message string, retryable bool, sourceURL string) collector.SearchResult {
	result.Status = status
	result.Errors = append(result.Errors, collector.Issue{Code: code, Message: message, Retryable: retryable, SourceURL: &sourceURL})
	return result
}
