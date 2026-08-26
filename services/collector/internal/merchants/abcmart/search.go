// Package abcmart는 ABC마트의 공개 상품 검색 데이터를 공통 수집 결과로 변환한다.
package abcmart

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

const (
	merchantName       = "abcmart"
	collectorVersion   = "abcmart-search-v2"
	searchEndpoint     = "https://abcmart.a-rt.com/display/search-word/result-total/list"
	productEndpoint    = "https://abcmart.a-rt.com/product"
	maxSearchBodyBytes = 4 * 1024 * 1024
	minRequestInterval = time.Second
)

// Searcher는 ABC마트 공개 검색 JSON을 요청하고 상품과 페이지 정보를 공통 결과로 변환한다.
type Searcher struct {
	client          *http.Client
	now             func() time.Time
	minInterval     time.Duration
	rateMu          sync.Mutex
	nextRequestTime time.Time
}

// searchResponse는 ABC마트 검색 JSON에서 상품과 페이지 계산에 필요한 필드만 표현한다.
type searchResponse struct {
	Search      []searchItem `json:"SEARCH"`
	SearchCount *int         `json:"SEARCH_COUNT"`
	Page        *struct {
		FinalPageNo *int `json:"finalPageNo"`
	} `json:"PAGE"`
}

// searchItem은 ABC마트 상품 JSON의 원본 문자열 필드를 표현한다.
type searchItem struct {
	ProductNo      string            `json:"PRDT_NO"`
	Name           string            `json:"PRDT_NAME"`
	Brand          string            `json:"BRAND_NAME"`
	ImageURL       string            `json:"PRDT_IMAGE_URL"`
	DiscountPrice  string            `json:"PRDT_DC_PRICE"`
	Category       string            `json:"CTGR_NAME_ALL"`
	ProductOptions string            `json:"PRDT_OPTION"`
	ColorID        string            `json:"COLOR_ID"`
	SoldOut        string            `json:"SOLD_OUT"`
	ReviewCount    string            `json:"RVW_COUNT"`
	SizeList       map[string]string `json:"SIZE_LIST"`
}

// normalizedItem은 숫자와 옵션을 검증한 뒤 공통 상품으로 변환할 ABC마트 상품을 표현한다.
type normalizedItem struct {
	ProductNo   string
	Name        string
	Brand       string
	ImageURL    string
	Price       int
	Category    []string
	Color       string
	IsSoldOut   bool
	ReviewCount *int
	SizeStocks  []sizeStock
}

// sizeStock은 하나의 신발 사이즈와 검색 JSON에 공개된 수량을 표현한다.
type sizeStock struct {
	Size     string
	Quantity int
}

// NewSearcher는 timeout과 redirect 제한이 설정된 HTTP client로 ABC마트 검색기를 생성한다.
func NewSearcher(timeout time.Duration) *Searcher {
	return NewSearcherWithClient(&http.Client{
		Timeout:       timeout,
		CheckRedirect: checkRedirect,
	}, time.Now, minRequestInterval)
}

// NewSearcherWithClient는 테스트용 HTTP client와 시계를 주입해 ABC마트 검색기를 생성한다.
func NewSearcherWithClient(client *http.Client, now func() time.Time, minInterval time.Duration) *Searcher {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second, CheckRedirect: checkRedirect}
	}
	if now == nil {
		now = time.Now
	}
	return &Searcher{client: client, now: now, minInterval: minInterval}
}

// Search는 ABC마트 공개 검색 JSON에서 상품, 사이즈별 재고, 전체 수와 다음 페이지 여부를 수집한다.
func (s *Searcher) Search(ctx context.Context, request collector.SearchRequest) collector.SearchResult {
	collectedAt := s.now()
	searchURL := buildSearchURL(request)
	result := newResult(request, collectedAt)

	if request.Currency != "KRW" {
		return failedResult(result, "CURRENCY_UNSUPPORTED", "ABC마트 검색 가격은 KRW만 지원합니다", false, searchURL)
	}
	if err := s.waitForTurn(ctx); err != nil {
		return failedResult(result, "ABCMART_REQUEST_CANCELED", err.Error(), true, searchURL)
	}

	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return failedResult(result, "REQUEST_BUILD_FAILED", err.Error(), false, searchURL)
	}
	httpRequest.Header.Set("User-Agent", "PurchaseResearchAgent/0.1 (+public product research; low rate)")
	httpRequest.Header.Set("Accept", "application/json")
	httpRequest.Header.Set("Accept-Language", request.Locale)

	response, err := s.client.Do(httpRequest)
	if err != nil {
		return failedResult(result, "ABCMART_REQUEST_FAILED", err.Error(), true, searchURL)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		retryable := response.StatusCode == http.StatusTooManyRequests || response.StatusCode >= 500
		return failedResult(result, "ABCMART_HTTP_ERROR", fmt.Sprintf("ABC마트 검색 결과가 HTTP %d를 반환했습니다", response.StatusCode), retryable, searchURL)
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, maxSearchBodyBytes+1))
	if err != nil {
		return failedResult(result, "ABCMART_RESPONSE_READ_FAILED", err.Error(), true, searchURL)
	}
	if len(body) > maxSearchBodyBytes {
		return failedResult(result, "ABCMART_RESPONSE_TOO_LARGE", "ABC마트 검색 응답이 허용 크기를 넘었습니다", false, searchURL)
	}

	var payload searchResponse
	if err := json.Unmarshal(body, &payload); err != nil {
		return failedResult(result, "ABCMART_RESPONSE_INVALID", fmt.Sprintf("ABC마트 검색 JSON 해석 실패: %v", err), false, searchURL)
	}
	if payload.Search == nil {
		return failedResult(result, "ABCMART_PAGE_CHANGED", "ABC마트 검색 JSON에서 SEARCH 목록을 찾지 못했습니다", false, searchURL)
	}
	if payload.SearchCount == nil || payload.Page == nil || payload.Page.FinalPageNo == nil {
		return failedResult(result, "ABCMART_PAGE_CHANGED", "ABC마트 검색 JSON에서 페이지 정보를 찾지 못했습니다", false, searchURL)
	}
	if *payload.SearchCount < 0 || *payload.Page.FinalPageNo < 0 {
		return failedResult(result, "ABCMART_RESPONSE_INVALID", "ABC마트 검색 JSON의 페이지 정보가 음수입니다", false, searchURL)
	}

	totalCount := *payload.SearchCount
	hasNext := *payload.Page.FinalPageNo > 1
	result.TotalCount = &totalCount
	result.HasNext = &hasNext

	for _, rawItem := range payload.Search {
		item, err := normalizeItem(rawItem)
		if err != nil {
			return failedResult(result, "ABCMART_RESPONSE_INVALID", err.Error(), false, searchURL)
		}
		if request.Filters.InStockOnly && item.IsSoldOut {
			continue
		}
		if request.Filters.PriceMin != nil && item.Price < *request.Filters.PriceMin {
			continue
		}
		if request.Filters.PriceMax != nil && item.Price > *request.Filters.PriceMax {
			continue
		}
		if len(request.Filters.Sizes) > 0 && !hasAvailableRequestedSize(item.SizeStocks, request.Filters.Sizes) {
			continue
		}
		result.Products = append(result.Products, toProduct(item, searchURL, collectedAt))
		if len(result.Products) >= request.Limit {
			break
		}
	}

	if len(request.Filters.Categories) > 0 || len(request.Filters.Colors) > 0 || len(request.Filters.Attributes) > 0 {
		result.Status = collector.StatusPartial
		result.Warnings = append(result.Warnings, collector.Issue{
			Code: "FILTERS_PARTIALLY_SUPPORTED", Message: "현재 ABC마트 검색은 가격, 재고, 신발 사이즈 조건만 지원합니다",
			Retryable: false, SourceURL: &searchURL,
		})
	}
	return result
}

// normalizeItem은 ABC마트 문자열 가격·리뷰 수·사이즈 재고를 검증 가능한 공통 값으로 바꾼다.
func normalizeItem(item searchItem) (normalizedItem, error) {
	if item.ProductNo == "" || item.Name == "" {
		return normalizedItem{}, fmt.Errorf("ABC마트 상품의 번호 또는 이름이 비어 있습니다")
	}
	price, err := parseNonNegativeInt(item.DiscountPrice)
	if err != nil {
		return normalizedItem{}, fmt.Errorf("ABC마트 상품 %s의 가격 해석 실패: %w", item.ProductNo, err)
	}
	stocks, err := parseSizeStocks(item.ProductOptions, item.SizeList)
	if err != nil {
		return normalizedItem{}, fmt.Errorf("ABC마트 상품 %s의 사이즈 재고 해석 실패: %w", item.ProductNo, err)
	}
	var reviewCount *int
	if item.ReviewCount != "" {
		value, parseErr := parseNonNegativeInt(item.ReviewCount)
		if parseErr != nil {
			return normalizedItem{}, fmt.Errorf("ABC마트 상품 %s의 리뷰 수 해석 실패: %w", item.ProductNo, parseErr)
		}
		reviewCount = &value
	}
	return normalizedItem{
		ProductNo: item.ProductNo, Name: item.Name, Brand: item.Brand, ImageURL: item.ImageURL,
		Price: price, Category: splitCategoryPath(item.Category), Color: item.ColorID,
		IsSoldOut: strings.EqualFold(item.SoldOut, "y"), ReviewCount: reviewCount, SizeStocks: stocks,
	}, nil
}

// parseNonNegativeInt는 공백을 제거한 0 이상의 정수 문자열을 변환한다.
func parseNonNegativeInt(value string) (int, error) {
	parsed, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || parsed < 0 {
		return 0, fmt.Errorf("0 이상의 정수가 아닙니다: %q", value)
	}
	return parsed, nil
}

// parseSizeStocks는 옵션 표시 순서를 유지하며 SIZE_LIST의 공개 수량을 구조화한다.
func parseSizeStocks(optionValues string, sizeList map[string]string) ([]sizeStock, error) {
	sizes := make([]string, 0, len(sizeList))
	seen := make(map[string]bool, len(sizeList))
	for _, size := range strings.Split(optionValues, ",") {
		size = strings.TrimSpace(size)
		if size != "" && !seen[size] {
			sizes = append(sizes, size)
			seen[size] = true
		}
	}
	leftovers := make([]string, 0)
	for size := range sizeList {
		if !seen[size] {
			leftovers = append(leftovers, size)
		}
	}
	sort.Strings(leftovers)
	sizes = append(sizes, leftovers...)

	stocks := make([]sizeStock, 0, len(sizes))
	for _, size := range sizes {
		quantityText, exists := sizeList[size]
		if !exists {
			continue
		}
		quantity, err := parseNonNegativeInt(quantityText)
		if err != nil {
			return nil, fmt.Errorf("사이즈 %s: %w", size, err)
		}
		stocks = append(stocks, sizeStock{Size: size, Quantity: quantity})
	}
	return stocks, nil
}

// splitCategoryPath는 `신발 > 구두 > 로퍼` 문자열을 빈 값 없는 카테고리 경로로 바꾼다.
func splitCategoryPath(value string) []string {
	parts := strings.Split(value, ">")
	path := make([]string, 0, len(parts))
	for _, part := range parts {
		if part = strings.TrimSpace(part); part != "" {
			path = append(path, part)
		}
	}
	return path
}

// hasAvailableRequestedSize는 요청 사이즈 중 공개 수량이 1개 이상인 사이즈가 있는지 검사한다.
func hasAvailableRequestedSize(stocks []sizeStock, requested []string) bool {
	for _, stock := range stocks {
		if stock.Quantity <= 0 {
			continue
		}
		for _, size := range requested {
			if stock.Size == size {
				return true
			}
		}
	}
	return false
}

// waitForTurn은 한 Searcher가 ABC마트에 보내는 요청 사이에 최소 간격을 보장한다.
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

// buildSearchURL은 검색 요청을 ABC마트 공개 상품 JSON URL로 변환한다.
func buildSearchURL(request collector.SearchRequest) string {
	values := url.Values{}
	pageSize := request.Limit
	if pageSize < 30 {
		pageSize = 30
	}
	values.Set("sort", "point")
	values.Set("page", "1")
	values.Set("perPage", strconv.Itoa(pageSize))
	values.Set("pageColumn", "3")
	values.Set("smartSearchCheck", "true")
	values.Set("deviceCode", "10000")
	values.Set("searchWord", request.Query)
	values.Set("firstSearchWord", request.Query)
	values.Set("tabGubun", "total")
	values.Set("searchPageGubun", "product")
	values.Set("firstSearchYn", "Y")
	values.Set("channel", "10001")
	values.Set("resultChannel", "10001")
	values.Set("memberTypeCode", "10002")
	return searchEndpoint + "?" + values.Encode()
}

// checkRedirect는 ABC마트 또는 A-RT host 안에서 최대 5회까지만 redirect를 허용한다.
func checkRedirect(request *http.Request, previous []*http.Request) error {
	if len(previous) >= 5 {
		return fmt.Errorf("ABC마트 redirect 횟수가 5회를 넘었습니다")
	}
	switch request.URL.Hostname() {
	case "abcmart.a-rt.com", "www.a-rt.com", "a-rt.com":
		return nil
	default:
		return fmt.Errorf("허용되지 않은 redirect host입니다: %s", request.URL.Hostname())
	}
}

// toProduct는 검증한 ABC마트 검색 상품을 Collector 공통 상품으로 변환한다.
func toProduct(item normalizedItem, sourceURL string, collectedAt time.Time) collector.Product {
	productURL := productEndpoint + "?prdtNo=" + url.QueryEscape(item.ProductNo)
	provenance := collector.Provenance{SourceURL: sourceURL, CollectedAt: collectedAt, CollectorVersion: collectorVersion}
	price := collector.Money{Amount: item.Price, Currency: "KRW"}
	options := make([]collector.Option, 0, len(item.SizeStocks))
	for _, stock := range item.SizeStocks {
		size := stock.Size
		optionID := item.ProductNo + "-" + stock.Size
		stockStatus := collector.StockOut
		if stock.Quantity > 0 {
			stockStatus = collector.StockAvailable
		}
		var color *string
		label := stock.Size
		if item.Color != "" {
			value := item.Color
			color = &value
			label += " / " + value
		}
		options = append(options, collector.Option{
			ExternalID: &optionID, Label: label, Size: &size, Color: color,
			Stock: stockStatus, Price: &price, Provenance: provenance,
		})
	}

	var brand *string
	if item.Brand != "" {
		value := item.Brand
		brand = &value
	}
	imageURLs := []string{}
	if item.ImageURL != "" {
		imageURLs = append(imageURLs, item.ImageURL)
	}
	stockStatus := collector.StockAvailable
	if item.IsSoldOut {
		stockStatus = collector.StockOut
	}

	return collector.Product{
		ExternalID: item.ProductNo, Name: item.Name, Brand: brand, CategoryPath: item.Category,
		ProductURL: productURL, ImageURLs: imageURLs, Price: &price,
		Shipping:    collector.Shipping{Fee: nil, Summary: nil, Provenance: provenance},
		StockStatus: stockStatus, Rating: nil, ReviewCount: item.ReviewCount, Options: options,
		Measurements: map[string]collector.MeasurementValue{}, Reviews: []collector.Review{}, Provenance: provenance,
	}
}

// newResult는 페이지 정보를 아직 모르는 빈 ABC마트 검색 결과를 생성한다.
func newResult(request collector.SearchRequest, collectedAt time.Time) collector.SearchResult {
	return collector.SearchResult{
		RequestID: request.RequestID, Operation: collector.OperationSearch, Status: collector.StatusSuccess,
		Merchant: request.Merchant, Query: request.Query, Filters: request.Filters, TotalCount: nil, HasNext: nil,
		CollectedAt: collectedAt, CollectorVersion: collectorVersion,
		Products: []collector.Product{}, Warnings: []collector.Issue{}, Errors: []collector.Issue{},
	}
}

// failedResult는 ABC마트 수집 실패를 빈 정상 결과로 숨기지 않고 오류 상태로 변환한다.
func failedResult(result collector.SearchResult, code, message string, retryable bool, sourceURL string) collector.SearchResult {
	result.Status = collector.StatusUnsupported
	if retryable {
		result.Status = collector.StatusTemporarilyUnavailable
	}
	result.Errors = append(result.Errors, collector.Issue{Code: code, Message: message, Retryable: retryable, SourceURL: &sourceURL})
	return result
}
