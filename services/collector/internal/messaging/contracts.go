// Package messaging은 RabbitMQ 작업·결과 계약과 Collector Worker 실행을 제공한다.
package messaging

import (
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

const (
	// SchemaVersion은 현재 Collection Queue 메시지 계약 버전이다.
	SchemaVersion = "1"
	// OperationSearch는 판매처 상품 검색 작업을 나타낸다.
	OperationSearch = "search"
	// TaskStatusSuccess는 Collector가 검색 범위를 정상 수집했음을 나타낸다.
	TaskStatusSuccess = "success"
	// TaskStatusPartial은 Collector가 검색 범위의 일부만 수집했음을 나타낸다.
	TaskStatusPartial = "partial"
	// TaskStatusFailed는 작업을 저장 가능한 결과로 완료하지 못했음을 나타낸다.
	TaskStatusFailed = "failed"
)

var (
	queueIdentifierPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]*$`)
	idempotencyKeyPattern  = regexp.MustCompile(`^collection:v1:[a-f0-9]{64}$`)
)

// CollectionTask는 Product Backend가 RabbitMQ에 등록하는 검색 작업 봉투다.
type CollectionTask struct {
	SchemaVersion  string        `json:"schemaVersion"`
	TaskID         string        `json:"taskId"`
	JobID          string        `json:"jobId"`
	Merchant       string        `json:"merchant"`
	Operation      string        `json:"operation"`
	Priority       int           `json:"priority"`
	Attempt        int           `json:"attempt"`
	MaxAttempts    int           `json:"maxAttempts"`
	RequestedAt    time.Time     `json:"requestedAt"`
	IdempotencyKey string        `json:"idempotencyKey"`
	Payload        SearchPayload `json:"payload"`
}

// SearchPayload는 판매처 Adapter가 검색 URL을 만들 때 사용할 Queue v1 조건이다.
// Adapter의 대량 수집용 SearchPage와 별개로 Queue v1은 Page가 1이 아니면 검증에 실패한다.
type SearchPayload struct {
	Query    string                  `json:"query"`
	Page     int                     `json:"page"`
	Limit    int                     `json:"limit"`
	Locale   string                  `json:"locale"`
	Currency string                  `json:"currency"`
	Filters  collector.SearchFilters `json:"filters"`
}

// TaskError는 작업 실패 코드, 안전한 설명과 재시도 가능 여부를 전달한다.
type TaskError struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Retryable bool   `json:"retryable"`
}

// CollectionResultEnvelope는 Go Worker가 Product Backend 저장 Worker에 반환하는 실행 결과다.
type CollectionResultEnvelope struct {
	SchemaVersion   string                  `json:"schemaVersion"`
	TaskID          string                  `json:"taskId"`
	JobID           string                  `json:"jobId"`
	Status          string                  `json:"status"`
	StartedAt       time.Time               `json:"startedAt"`
	CompletedAt     time.Time               `json:"completedAt"`
	DurationMS      int64                   `json:"durationMs"`
	CollectorResult *collector.SearchResult `json:"collectorResult"`
	Error           *TaskError              `json:"error"`
}

// Validate는 작업 봉투와 검색 조건이 Collection Queue v1 계약을 만족하는지 검사한다.
func (t CollectionTask) Validate() error {
	if t.SchemaVersion != SchemaVersion {
		return fmt.Errorf("schemaVersion은 %s이어야 합니다", SchemaVersion)
	}
	if !validQueueIdentifier(t.TaskID) {
		return fmt.Errorf("taskId 형식이 올바르지 않습니다")
	}
	if !validQueueIdentifier(t.JobID) {
		return fmt.Errorf("jobId 형식이 올바르지 않습니다")
	}
	if t.Operation != OperationSearch {
		return fmt.Errorf("현재 operation은 search만 지원합니다")
	}
	if t.Priority < 0 || t.Priority > 100 {
		return fmt.Errorf("priority는 0 이상 100 이하여야 합니다")
	}
	if t.Attempt < 0 || t.MaxAttempts < 1 || t.MaxAttempts > 5 || t.Attempt >= t.MaxAttempts {
		return fmt.Errorf("attempt와 maxAttempts 범위가 올바르지 않습니다")
	}
	if t.RequestedAt.IsZero() {
		return fmt.Errorf("requestedAt이 필요합니다")
	}
	if !idempotencyKeyPattern.MatchString(t.IdempotencyKey) {
		return fmt.Errorf("idempotencyKey 형식이 올바르지 않습니다")
	}
	if t.Payload.Page != 1 {
		return fmt.Errorf("현재 Queue v1 검색 Worker는 page=1만 지원합니다")
	}
	return t.SearchRequest().Validate()
}

// SearchRequest는 Queue 작업을 기존 Collector 검색 요청으로 변환한다.
func (t CollectionTask) SearchRequest() collector.SearchRequest {
	return collector.SearchRequest{
		RequestID:   t.TaskID,
		Merchant:    t.Merchant,
		Query:       strings.TrimSpace(t.Payload.Query),
		RequestedAt: t.RequestedAt,
		Limit:       t.Payload.Limit,
		Locale:      t.Payload.Locale,
		Currency:    t.Payload.Currency,
		Filters:     t.Payload.Filters,
	}
}

// CanRetry는 현재 실행 실패 후 retry Queue로 보낼 수 있는지 반환한다.
func (t CollectionTask) CanRetry() bool {
	return t.Attempt+1 < t.MaxAttempts
}

// NextAttempt는 재시도 횟수를 하나 증가시킨 새 작업 값을 반환한다.
func (t CollectionTask) NextAttempt() CollectionTask {
	t.Attempt++
	return t
}

// validQueueIdentifier는 taskId와 jobId가 길이와 허용 문자 규칙을 만족하는지 확인한다.
func validQueueIdentifier(value string) bool {
	return len(value) >= 1 && len(value) <= 128 && queueIdentifierPattern.MatchString(value)
}
