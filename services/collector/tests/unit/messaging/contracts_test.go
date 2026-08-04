package messaging_test

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/messaging"
)

type stubSearcher struct {
	result collector.SearchResult
}

func (s stubSearcher) Search(_ context.Context, request collector.SearchRequest) collector.SearchResult {
	result := s.result
	result.RequestID = request.RequestID
	result.Merchant = request.Merchant
	return result
}

// stubPageSearcher는 Processor가 Queue page를 SearchPage에 전달했는지 기록한다.
type stubPageSearcher struct {
	stubSearcher
	requestedPage int
}

// SearchPage는 요청 페이지를 기록하고 준비된 검색 결과를 반환한다.
func (s *stubPageSearcher) SearchPage(_ context.Context, request collector.SearchRequest, page int) collector.SearchResult {
	s.requestedPage = page
	result := s.result
	result.RequestID = request.RequestID
	result.Merchant = request.Merchant
	return result
}

func validTask() messaging.CollectionTask {
	return messaging.CollectionTask{
		SchemaVersion:  messaging.SchemaVersion,
		TaskID:         "task-001",
		JobID:          "job-001",
		Merchant:       "abcmart",
		Operation:      messaging.OperationSearch,
		Priority:       20,
		Attempt:        0,
		MaxAttempts:    2,
		RequestedAt:    time.Date(2026, 7, 26, 16, 0, 0, 0, time.FixedZone("KST", 9*60*60)),
		IdempotencyKey: "collection:v1:" + strings.Repeat("a", 64),
		Payload: messaging.SearchPayload{
			Query: "구두", Page: 1, Limit: 3, Locale: "ko-KR", Currency: "KRW",
			Filters: collector.SearchFilters{},
		},
	}
}

func TestCollectionTaskValidationAndRetry(t *testing.T) {
	task := validTask()
	if err := task.Validate(); err != nil {
		t.Fatalf("유효한 작업이 거부됐습니다: %v", err)
	}
	if !task.CanRetry() {
		t.Fatal("최초 실패는 한 번 재시도할 수 있어야 합니다")
	}

	next := task.NextAttempt()
	if next.Attempt != 1 {
		t.Fatalf("재시도 attempt가 1이어야 합니다: %d", next.Attempt)
	}
	if next.CanRetry() {
		t.Fatal("maxAttempts=2에서 두 번째 실행 뒤에는 재시도하면 안 됩니다")
	}
}

// TestCollectionTaskAcceptsBoundedPage는 page=2를 허용하고 안전 상한 초과는 거절하는지 검증한다.
func TestCollectionTaskAcceptsBoundedPage(t *testing.T) {
	task := validTask()
	task.Payload.Page = 2
	if err := task.Validate(); err != nil {
		t.Fatalf("page=2 작업이 거부됐습니다: %v", err)
	}

	task.Payload.Page = messaging.MaxSearchPage + 1
	if err := task.Validate(); err == nil || !strings.Contains(err.Error(), "page는") {
		t.Fatalf("페이지 안전 상한 오류가 필요합니다: %v", err)
	}
}

// TestProcessorRoutesSelectedPage는 page=2 작업이 SearchPage로 한 번만 전달되는지 검증한다.
func TestProcessorRoutesSelectedPage(t *testing.T) {
	searcher := &stubPageSearcher{stubSearcher: stubSearcher{result: collector.SearchResult{
		Operation: collector.OperationSearch, Status: collector.StatusSuccess,
		CollectorVersion: "test-v1", Products: []collector.Product{},
		Warnings: []collector.Issue{}, Errors: []collector.Issue{},
	}}}
	processor := messaging.NewProcessor(searcher, time.Second, time.Now)
	task := validTask()
	task.Payload.Page = 2

	result := processor.Process(context.Background(), task)

	if result.Status != messaging.TaskStatusSuccess || searcher.requestedPage != 2 {
		t.Fatalf("result=%#v requestedPage=%d", result, searcher.requestedPage)
	}
}

func TestProcessorReturnsSuccessfulEnvelope(t *testing.T) {
	startedAt := time.Date(2026, 7, 26, 7, 0, 0, 0, time.UTC)
	times := []time.Time{
		startedAt,
		startedAt.Add(25 * time.Millisecond),
	}
	index := 0
	now := func() time.Time {
		value := times[index]
		index++
		return value
	}
	processor := messaging.NewProcessor(stubSearcher{result: collector.SearchResult{
		Operation:        collector.OperationSearch,
		Status:           collector.StatusSuccess,
		CollectorVersion: "test-v1",
		Products:         []collector.Product{},
		Warnings:         []collector.Issue{},
		Errors:           []collector.Issue{},
	}}, time.Second, now)

	result := processor.Process(context.Background(), validTask())

	if result.Status != messaging.TaskStatusSuccess {
		t.Fatalf("성공 상태가 필요합니다: %s", result.Status)
	}
	if result.DurationMS != 25 {
		t.Fatalf("25ms 실행 시간이 필요합니다: %d", result.DurationMS)
	}
	if result.CollectorResult == nil || result.Error != nil {
		t.Fatal("성공 결과에는 CollectorResult만 있어야 합니다")
	}
}

func TestProcessorMarksRetryableCollectorFailure(t *testing.T) {
	now := time.Now
	processor := messaging.NewProcessor(stubSearcher{result: collector.SearchResult{
		Operation:        collector.OperationSearch,
		Status:           collector.StatusTemporarilyUnavailable,
		CollectorVersion: "test-v1",
		Products:         []collector.Product{},
		Warnings:         []collector.Issue{},
		Errors: []collector.Issue{{
			Code: "REMOTE_TIMEOUT", Message: "timeout", Retryable: true,
		}},
	}}, time.Second, now)

	result := processor.Process(context.Background(), validTask())

	if result.Status != messaging.TaskStatusFailed || result.Error == nil || !result.Error.Retryable {
		t.Fatalf("재시도 가능한 실패가 필요합니다: %#v", result)
	}
}
