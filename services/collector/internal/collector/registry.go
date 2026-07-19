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
	searcher, ok := r.searchers[request.Merchant]
	if ok {
		return searcher.Search(ctx, request)
	}

	return SearchResult{
		RequestID:        request.RequestID,
		Operation:        OperationSearch,
		Status:           StatusUnsupported,
		Merchant:         request.Merchant,
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
