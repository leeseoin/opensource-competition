// Package abcmart는 ABC마트의 공개 상품 페이지를 수집하는 판매처 adapter를 제공한다.
package abcmart

import (
	"context"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

const (
	merchantName       = "abcmart"
	collectorVersion   = "abcmart-search-v1"
	searchEndpoint     = "https://abcmart.a-rt.com/display/search-word/result/list"
	productEndpoint    = "https://abcmart.a-rt.com/product"
	maxSearchBodyBytes = 2 * 1024 * 1024
	minRequestInterval = time.Second
)

var (
	productStartPattern = regexp.MustCompile(`<li class="([^"]*smart-search-product-item[^"]*)" data-product-no="([^"]+)"`)
	imagePattern        = regexp.MustCompile(`<img src="([^"]+)" class="search-prod-image"`)
	brandPattern        = regexp.MustCompile(`(?s)<span class="prod-brand">\s*(.*?)\s*</span>`)
	namePattern         = regexp.MustCompile(`(?s)<span class="prod-name">\s*(?:<span[^>]*>.*?</span>)?\s*(.*?)\s*</span>`)
	pricePattern        = regexp.MustCompile(`(?s)<span class="price-cost">\s*([0-9,]+)\s*</span>`)
	optionPattern       = regexp.MustCompile(`<ol class="prod-size-list[^"]*" data-option="([^"]*)" data-option-info="([^"]*)"`)
	tagPattern          = regexp.MustCompile(`<[^>]+>`)
)

// Searcher는 ABC마트 공개 검색 결과를 요청하고 상품 HTML을 공통 결과로 변환한다.
type Searcher struct {
	client          *http.Client
	now             func() time.Time
	minInterval     time.Duration
	rateMu          sync.Mutex
	nextRequestTime time.Time
}

// searchItem은 ABC마트 검색 목록에서 읽은 상품과 사이즈별 공개 재고를 표현한다.
type searchItem struct {
	ProductNo  string
	Name       string
	Brand      string
	ImageURL   string
	Price      int
	IsSoldOut  bool
	SizeStocks []sizeStock
}

// sizeStock은 하나의 신발 사이즈와 검색 페이지에 공개된 수량을 표현한다.
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
	return &Searcher{client: client, now: now, minInterval: minInterval}
}

// Search는 ABC마트 공개 검색 결과에서 상품과 사이즈별 공개 재고를 수집한다.
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
	httpRequest.Header.Set("Accept", "text/html,application/xhtml+xml")
	httpRequest.Header.Set("Accept-Language", request.Locale)

	response, err := s.client.Do(httpRequest)
	if err != nil {
		return failedResult(result, "ABCMART_REQUEST_FAILED", err.Error(), true, searchURL)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		retryable := response.StatusCode == http.StatusTooManyRequests || response.StatusCode >= 500
		return failedResult(
			result,
			"ABCMART_HTTP_ERROR",
			fmt.Sprintf("ABC마트 검색 결과가 HTTP %d를 반환했습니다", response.StatusCode),
			retryable,
			searchURL,
		)
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, maxSearchBodyBytes+1))
	if err != nil {
		return failedResult(result, "ABCMART_RESPONSE_READ_FAILED", err.Error(), true, searchURL)
	}
	if len(body) > maxSearchBodyBytes {
		return failedResult(result, "ABCMART_RESPONSE_TOO_LARGE", "ABC마트 검색 응답이 허용 크기를 넘었습니다", false, searchURL)
	}

	items, err := parseSearchItems(body)
	if err != nil {
		return failedResult(result, "ABCMART_PAGE_CHANGED", err.Error(), false, searchURL)
	}

	for _, item := range items {
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
			Code:      "FILTERS_PARTIALLY_SUPPORTED",
			Message:   "현재 ABC마트 검색은 가격, 재고, 신발 사이즈 조건만 지원합니다",
			Retryable: false,
			SourceURL: &searchURL,
		})
	}
	return result
}

// waitForTurn은 한 Searcher가 ABC마트에 보내는 요청 사이에 최소 간격을 보장한다.
// 대기 중 context가 취소되면 원격 요청을 보내지 않고 context 오류를 반환한다.
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

// buildSearchURL은 검색 요청을 ABC마트의 공개 검색 결과 목록 URL로 변환한다.
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

// parseSearchItems는 ABC마트 검색 결과 HTML에서 상품 block을 찾아 필요한 필드를 읽는다.
func parseSearchItems(body []byte) ([]searchItem, error) {
	text := string(body)
	indexes := productStartPattern.FindAllStringSubmatchIndex(text, -1)
	if len(indexes) == 0 {
		return nil, fmt.Errorf("ABC마트 상품 목록을 찾지 못했습니다")
	}

	items := make([]searchItem, 0, len(indexes))
	for index, location := range indexes {
		end := len(text)
		if index+1 < len(indexes) {
			end = indexes[index+1][0]
		}
		block := text[location[0]:end]
		item, err := parseSearchItem(block, text[location[4]:location[5]], text[location[2]:location[3]])
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, nil
}

// parseSearchItem은 상품 block 하나에서 번호, 이름, 브랜드, 가격, 이미지, 사이즈 재고를 읽는다.
func parseSearchItem(block, productNo, classes string) (searchItem, error) {
	name, ok := firstTextMatch(namePattern, block)
	if !ok {
		return searchItem{}, fmt.Errorf("ABC마트 상품 %s의 이름을 찾지 못했습니다", productNo)
	}
	priceText, ok := firstTextMatch(pricePattern, block)
	if !ok {
		return searchItem{}, fmt.Errorf("ABC마트 상품 %s의 가격을 찾지 못했습니다", productNo)
	}
	price, err := strconv.Atoi(strings.ReplaceAll(priceText, ",", ""))
	if err != nil {
		return searchItem{}, fmt.Errorf("ABC마트 상품 %s의 가격 해석 실패: %w", productNo, err)
	}
	brand, _ := firstTextMatch(brandPattern, block)
	imageURL, _ := firstRawMatch(imagePattern, block)

	var stocks []sizeStock
	if matches := optionPattern.FindStringSubmatch(block); len(matches) == 3 {
		stocks = parseSizeStocks(matches[1], matches[2])
	}

	return searchItem{
		ProductNo:  productNo,
		Name:       name,
		Brand:      brand,
		ImageURL:   html.UnescapeString(imageURL),
		Price:      price,
		IsSoldOut:  !strings.Contains(classes, "selling"),
		SizeStocks: stocks,
	}, nil
}

// firstTextMatch는 첫 정규식 capture에서 HTML tag와 공백을 제거한 문자열을 반환한다.
func firstTextMatch(pattern *regexp.Regexp, block string) (string, bool) {
	value, ok := firstRawMatch(pattern, block)
	if !ok {
		return "", false
	}
	value = tagPattern.ReplaceAllString(value, "")
	value = html.UnescapeString(value)
	return strings.Join(strings.Fields(value), " "), true
}

// firstRawMatch는 첫 정규식 capture를 가공하지 않고 반환한다.
func firstRawMatch(pattern *regexp.Regexp, block string) (string, bool) {
	matches := pattern.FindStringSubmatch(block)
	if len(matches) < 2 {
		return "", false
	}
	return matches[1], true
}

// parseSizeStocks는 공개된 사이즈 목록과 사이즈별 수량 문자열을 구조화한다.
func parseSizeStocks(optionValues, optionInfo string) []sizeStock {
	quantities := make(map[string]int)
	for _, entry := range strings.Split(optionInfo, "/") {
		parts := strings.Split(entry, ",")
		if len(parts) < 2 {
			continue
		}
		quantity, err := strconv.Atoi(parts[1])
		if err == nil {
			quantities[parts[0]] = quantity
		}
	}

	stocks := make([]sizeStock, 0)
	for _, size := range strings.Split(optionValues, ",") {
		size = strings.TrimSpace(size)
		if size == "" {
			continue
		}
		stocks = append(stocks, sizeStock{Size: size, Quantity: quantities[size]})
	}
	return stocks
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

// toProduct는 ABC마트 검색 상품을 Collector 공통 상품으로 변환한다.
func toProduct(item searchItem, sourceURL string, collectedAt time.Time) collector.Product {
	productURL := productEndpoint + "?prdtNo=" + url.QueryEscape(item.ProductNo)
	provenance := collector.Provenance{
		SourceURL:        sourceURL,
		CollectedAt:      collectedAt,
		CollectorVersion: collectorVersion,
	}
	price := collector.Money{Amount: item.Price, Currency: "KRW"}
	options := make([]collector.Option, 0, len(item.SizeStocks))
	for _, stock := range item.SizeStocks {
		size := stock.Size
		optionID := item.ProductNo + "-" + stock.Size
		stockStatus := collector.StockOut
		if stock.Quantity > 0 {
			stockStatus = collector.StockAvailable
		}
		options = append(options, collector.Option{
			ExternalID: &optionID,
			Label:      stock.Size,
			Size:       &size,
			Color:      nil,
			Stock:      stockStatus,
			Price:      &price,
			Provenance: provenance,
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
		ExternalID:   item.ProductNo,
		Name:         item.Name,
		Brand:        brand,
		CategoryPath: []string{},
		ProductURL:   productURL,
		ImageURLs:    imageURLs,
		Price:        &price,
		Shipping: collector.Shipping{
			Fee:        nil,
			Summary:    nil,
			Provenance: provenance,
		},
		StockStatus:  stockStatus,
		Rating:       nil,
		ReviewCount:  nil,
		Options:      options,
		Measurements: map[string]collector.MeasurementValue{},
		Reviews:      []collector.Review{},
		Provenance:   provenance,
	}
}

// newResult는 오류가 없고 상품이 비어 있는 ABC마트 검색 결과를 생성한다.
func newResult(request collector.SearchRequest, collectedAt time.Time) collector.SearchResult {
	return collector.SearchResult{
		RequestID:        request.RequestID,
		Operation:        collector.OperationSearch,
		Status:           collector.StatusSuccess,
		Merchant:         request.Merchant,
		CollectedAt:      collectedAt,
		CollectorVersion: collectorVersion,
		Products:         []collector.Product{},
		Warnings:         []collector.Issue{},
		Errors:           []collector.Issue{},
	}
}

// failedResult는 ABC마트 수집 실패를 빈 정상 결과로 숨기지 않고 오류 상태로 변환한다.
func failedResult(result collector.SearchResult, code, message string, retryable bool, sourceURL string) collector.SearchResult {
	result.Status = collector.StatusUnsupported
	if retryable {
		result.Status = collector.StatusTemporarilyUnavailable
	}
	result.Errors = append(result.Errors, collector.Issue{
		Code:      code,
		Message:   message,
		Retryable: retryable,
		SourceURL: &sourceURL,
	})
	return result
}
