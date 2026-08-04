package collector

import (
	"context"
	"time"
)

// SearchRegistry는 판매처 이름과 판매처별 검색기를 연결해 공통 검색 진입점을 제공한다.
type SearchRegistry struct {
	searchers map[string]Searcher
	now       func() time.Time
}

// NewSearchRegistry는 판매처별 검색기를 복사해 운영용 Registry를 생성한다.
func NewSearchRegistry(searchers map[string]Searcher) *SearchRegistry {
	return NewSearchRegistryWithClock(searchers, time.Now)
}

// NewSearchRegistryWithClock은 테스트에서 수집 시각을 고정할 수 있는 Registry를 생성한다.
func NewSearchRegistryWithClock(searchers map[string]Searcher, now func() time.Time) *SearchRegistry {
	copied := make(map[string]Searcher, len(searchers))
	for merchant, searcher := range searchers {
		if merchant != "" && searcher != nil {
			copied[merchant] = searcher
		}
	}
	if now == nil {
		now = time.Now
	}
	return &SearchRegistry{searchers: copied, now: now}
}

// Search는 요청한 판매처의 검색기에 작업을 전달하고 미등록 판매처는 unsupported로 반환한다.
func (r *SearchRegistry) Search(ctx context.Context, request SearchRequest) SearchResult {
	return r.SearchPage(ctx, request, 1)
}

// SearchPage는 등록 판매처가 페이지 검색을 지원하면 지정 페이지로 작업을 전달한다.
// page=2 이상을 지원하지 않는 판매처는 외부 요청 없이 unsupported 결과를 반환한다.
func (r *SearchRegistry) SearchPage(ctx context.Context, request SearchRequest, page int) SearchResult {
	searcher, ok := r.searchers[request.Merchant]
	if ok {
		var result SearchResult
		if page == 1 {
			result = searcher.Search(ctx, request)
		} else if pageSearcher, supportsPage := searcher.(PageSearcher); supportsPage {
			result = pageSearcher.SearchPage(ctx, request, page)
		} else {
			return SearchResult{
				RequestID: request.RequestID, Operation: OperationSearch, Status: StatusUnsupported,
				Merchant: request.Merchant, Query: request.Query, Filters: request.Filters,
				CollectedAt: r.now(), CollectorVersion: "collector-registry-v1",
				Products: []Product{}, Warnings: []Issue{},
				Errors: []Issue{{Code: "PAGE_UNSUPPORTED", Message: "이 판매처는 page=2 이상 검색을 지원하지 않습니다", Retryable: false}},
			}
		}
		result.Query = request.Query
		result.Filters = request.Filters
		return result
	}

	return SearchResult{
		RequestID:        request.RequestID,
		Operation:        OperationSearch,
		Status:           StatusUnsupported,
		Merchant:         request.Merchant,
		Query:            request.Query,
		Filters:          request.Filters,
		CollectedAt:      r.now(),
		CollectorVersion: "collector-registry-v1",
		Products:         []Product{},
		Warnings:         []Issue{},
		Errors: []Issue{{
			Code:      "MERCHANT_UNSUPPORTED",
			Message:   "등록되지 않은 판매처입니다: " + request.Merchant,
			Retryable: false,
		}},
	}
}
