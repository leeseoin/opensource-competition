// Package musinsa는 무신사 공개 검색 페이지와 리뷰 응답을 공통 수집 결과로 변환한다.
package musinsa

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
	merchantName       = "musinsa"
	collectorVersion   = "musinsa-search-v1"
	searchEndpoint     = "https://www.musinsa.com/search/goods"
	maxSearchBodyBytes = 4 * 1024 * 1024
	minRequestInterval = time.Second
)

var (
	nextDataID     = []byte(`id="__NEXT_DATA__"`)
	scriptCloseTag = []byte(`</script>`)
)

// Searcher는 무신사 공개 검색 페이지의 서버 렌더링 JSON에서 상품 목록을 수집한다.
type Searcher struct {
	client          *http.Client
	now             func() time.Time
	minInterval     time.Duration
	rateMu          sync.Mutex
	nextRequestTime time.Time
}

// searchItem은 무신사 검색 JSON에서 사용하는 상품 기본 필드를 표현한다.
type searchItem struct {
	GoodsNo      int    `json:"goodsNo"`
	GoodsName    string `json:"goodsName"`
	GoodsLinkURL string `json:"goodsLinkUrl"`
	Thumbnail    string `json:"thumbnail"`
	IsSoldOut    bool   `json:"isSoldOut"`
	Price        int    `json:"price"`
	BrandName    string `json:"brandName"`
	ReviewCount  int    `json:"reviewCount"`
	ReviewScore  int    `json:"reviewScore"`
}

// nextData는 검색 HTML의 __NEXT_DATA__ 안에서 상품 목록이 들어 있는 경로만 표현한다.
type nextData struct {
	Props struct {
		PageProps struct {
			DehydratedState struct {
				Queries []struct {
					State struct {
						Data struct {
							Pages []struct {
								Items []searchItem `json:"items"`
							} `json:"pages"`
						} `json:"data"`
					} `json:"state"`
				} `json:"queries"`
			} `json:"dehydratedState"`
		} `json:"pageProps"`
	} `json:"props"`
}

// NewSearcher는 timeout과 redirect 제한이 설정된 HTTP client로 무신사 검색기를 생성한다.
func NewSearcher(timeout time.Duration) *Searcher {
	return NewSearcherWithClient(&http.Client{
		Timeout:       timeout,
		CheckRedirect: checkRedirect,
	}, time.Now, minRequestInterval)
}

// NewSearcherWithClient는 테스트용 HTTP client, 시계, 요청 간격을 주입해 검색기를 생성한다.
func NewSearcherWithClient(client *http.Client, now func() time.Time, minInterval time.Duration) *Searcher {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second, CheckRedirect: checkRedirect}
	}
	if now == nil {
		now = time.Now
	}
	return &Searcher{client: client, now: now, minInterval: minInterval}
}

// Search는 검색어를 무신사 공개 검색 페이지에 전달하고 초기 JSON 상품을 공통 결과로 변환한다.
func (s *Searcher) Search(ctx context.Context, request collector.SearchRequest) collector.SearchResult {
	collectedAt := s.now()
	searchURL := buildSearchURL(request)
	result := newResult(request, collectedAt)

	if request.Currency != "KRW" {
		return failedResult(result, "CURRENCY_UNSUPPORTED", "무신사 검색 가격은 KRW만 지원합니다", false, searchURL)
	}
	if err := s.waitForTurn(ctx); err != nil {
		return failedResult(result, "MUSINSA_REQUEST_CANCELED", err.Error(), true, searchURL)
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
		return failedResult(result, "MUSINSA_REQUEST_FAILED", err.Error(), true, searchURL)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		retryable := response.StatusCode == http.StatusTooManyRequests || response.StatusCode >= 500
		return failedResult(result, "MUSINSA_HTTP_ERROR", fmt.Sprintf("무신사 검색 결과가 HTTP %d를 반환했습니다", response.StatusCode), retryable, searchURL)
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, maxSearchBodyBytes+1))
	if err != nil {
		return failedResult(result, "MUSINSA_RESPONSE_READ_FAILED", err.Error(), true, searchURL)
	}
	if len(body) > maxSearchBodyBytes {
		return failedResult(result, "MUSINSA_RESPONSE_TOO_LARGE", "무신사 검색 응답이 허용 크기를 넘었습니다", false, searchURL)
	}

	items, err := parseSearchItems(body)
	if err != nil {
		return failedResult(result, "MUSINSA_PAGE_CHANGED", err.Error(), false, searchURL)
	}
	for _, item := range items {
		if item.GoodsNo <= 0 || item.GoodsName == "" || item.Price < 0 {
			continue
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
		result.Products = append(result.Products, toProduct(item, searchURL, collectedAt))
		if len(result.Products) >= request.Limit {
			break
		}
	}

	if len(request.Filters.Categories) > 0 || len(request.Filters.Colors) > 0 || len(request.Filters.Attributes) > 0 {
		result.Status = collector.StatusPartial
		result.Warnings = append(result.Warnings, collector.Issue{
			Code:      "FILTERS_PARTIALLY_SUPPORTED",
			Message:   "현재 무신사 검색은 가격, 재고, 신발 사이즈 조건만 지원합니다",
			Retryable: false,
			SourceURL: &searchURL,
		})
	}
	return result
}

// waitForTurn은 한 Searcher가 무신사에 보내는 요청 사이에 최소 간격을 보장한다.
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

// buildSearchURL은 공통 검색 요청을 무신사 공개 검색 URL로 변환한다.
func buildSearchURL(request collector.SearchRequest) string {
	values := url.Values{}
	values.Set("keyword", request.Query)
	values.Set("gf", "A")
	for _, size := range request.Filters.Sizes {
		values.Add("shoeSizeOption", size)
	}
	return searchEndpoint + "?" + values.Encode()
}

// checkRedirect는 무신사 도메인 안에서 최대 5회까지만 redirect를 허용한다.
func checkRedirect(request *http.Request, previous []*http.Request) error {
	if len(previous) >= 5 {
		return fmt.Errorf("무신사 redirect 횟수가 5회를 넘었습니다")
	}
	switch request.URL.Hostname() {
	case "www.musinsa.com", "musinsa.com":
		return nil
	default:
		return fmt.Errorf("허용되지 않은 redirect host입니다: %s", request.URL.Hostname())
	}
}

// parseSearchItems는 검색 HTML의 __NEXT_DATA__ JSON에서 첫 상품 목록을 반환한다.
func parseSearchItems(body []byte) ([]searchItem, error) {
	idIndex := bytes.Index(body, nextDataID)
	if idIndex < 0 {
		return nil, fmt.Errorf("무신사 검색 JSON script를 찾지 못했습니다")
	}
	openOffset := bytes.IndexByte(body[idIndex:], '>')
	if openOffset < 0 {
		return nil, fmt.Errorf("무신사 검색 JSON 시작 위치를 찾지 못했습니다")
	}
	jsonStart := idIndex + openOffset + 1
	closeOffset := bytes.Index(body[jsonStart:], scriptCloseTag)
	if closeOffset < 0 {
		return nil, fmt.Errorf("무신사 검색 JSON 종료 위치를 찾지 못했습니다")
	}

	var data nextData
	if err := json.Unmarshal(body[jsonStart:jsonStart+closeOffset], &data); err != nil {
		return nil, fmt.Errorf("무신사 검색 JSON 해석 실패: %w", err)
	}
	for _, query := range data.Props.PageProps.DehydratedState.Queries {
		for _, page := range query.State.Data.Pages {
			if len(page.Items) > 0 {
				return page.Items, nil
			}
		}
	}
	return nil, fmt.Errorf("무신사 상품 목록을 찾지 못했습니다")
}

// toProduct는 무신사 검색 상품을 개인정보가 없는 Collector 공통 상품으로 변환한다.
func toProduct(item searchItem, sourceURL string, collectedAt time.Time) collector.Product {
	provenance := collector.Provenance{SourceURL: sourceURL, CollectedAt: collectedAt, CollectorVersion: collectorVersion}
	price := collector.Money{Amount: item.Price, Currency: "KRW"}
	productURL := item.GoodsLinkURL
	if productURL == "" {
		productURL = "https://www.musinsa.com/products/" + strconv.Itoa(item.GoodsNo)
	}
	brand := item.BrandName
	var brandPointer *string
	if brand != "" {
		brandPointer = &brand
	}
	imageURLs := []string{}
	if item.Thumbnail != "" {
		imageURLs = append(imageURLs, item.Thumbnail)
	}
	stockStatus := collector.StockAvailable
	if item.IsSoldOut {
		stockStatus = collector.StockOut
	}
	var rating *float64
	if item.ReviewCount > 0 && item.ReviewScore > 0 {
		value := float64(item.ReviewScore) / 20
		rating = &value
	}
	reviewCount := item.ReviewCount

	return collector.Product{
		ExternalID:   strconv.Itoa(item.GoodsNo),
		Name:         item.GoodsName,
		Brand:        brandPointer,
		CategoryPath: []string{},
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

// newResult는 오류가 없고 상품이 비어 있는 무신사 검색 결과를 생성한다.
func newResult(request collector.SearchRequest, collectedAt time.Time) collector.SearchResult {
	return collector.SearchResult{
		RequestID: request.RequestID, Operation: collector.OperationSearch, Status: collector.StatusSuccess,
		Merchant: merchantName, Query: request.Query, Filters: request.Filters,
		CollectedAt: collectedAt, CollectorVersion: collectorVersion,
		Products: []collector.Product{}, Warnings: []collector.Issue{}, Errors: []collector.Issue{},
	}
}

// failedResult는 무신사 수집 실패를 빈 정상 결과로 숨기지 않고 오류 상태로 변환한다.
func failedResult(result collector.SearchResult, code, message string, retryable bool, sourceURL string) collector.SearchResult {
	result.Status = collector.StatusUnsupported
	if retryable {
		result.Status = collector.StatusTemporarilyUnavailable
	}
	result.Errors = append(result.Errors, collector.Issue{Code: code, Message: message, Retryable: retryable, SourceURL: &sourceURL})
	return result
}
