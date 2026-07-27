package messaging

import (
	"context"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

// Processor는 검증된 Queue 작업을 기존 Collector Searcher에 전달한다.
type Processor struct {
	searcher collector.Searcher
	timeout  time.Duration
	now      func() time.Time
}

// NewProcessor는 Searcher, 작업 timeout과 시계를 주입받아 작업 처리기를 생성한다.
// timeout이 0 이하이면 15초, 시계가 nil이면 time.Now를 사용한다.
func NewProcessor(searcher collector.Searcher, timeout time.Duration, now func() time.Time) *Processor {
	if timeout <= 0 {
		timeout = 15 * time.Second
	}
	if now == nil {
		now = time.Now
	}
	return &Processor{searcher: searcher, timeout: timeout, now: now}
}

// Process는 검색 작업을 제한 시간 안에 실행하고 Queue 결과 봉투로 변환한다.
// 입력 작업이 유효하지 않거나 Searcher가 없으면 실패 결과를 반환하며 panic을 발생시키지 않는다.
func (p *Processor) Process(ctx context.Context, task CollectionTask) CollectionResultEnvelope {
	startedAt := p.now()
	if err := task.Validate(); err != nil {
		return p.failed(task, startedAt, "INVALID_TASK", err.Error(), false, nil)
	}
	if p.searcher == nil {
		return p.failed(task, startedAt, "SEARCHER_UNAVAILABLE", "Collector Searcher가 준비되지 않았습니다", true, nil)
	}

	taskCtx, cancel := context.WithTimeout(ctx, p.timeout)
	defer cancel()
	result := p.searcher.Search(taskCtx, task.SearchRequest())

	switch result.Status {
	case collector.StatusSuccess:
		return p.completed(task, startedAt, TaskStatusSuccess, &result)
	case collector.StatusPartial:
		return p.completed(task, startedAt, TaskStatusPartial, &result)
	default:
		code, message, retryable := summarizeCollectorFailure(result)
		return p.failed(task, startedAt, code, message, retryable, &result)
	}
}

// completed는 성공 또는 부분 성공 CollectorResult가 포함된 Queue 결과를 만든다.
func (p *Processor) completed(
	task CollectionTask,
	startedAt time.Time,
	status string,
	result *collector.SearchResult,
) CollectionResultEnvelope {
	completedAt := p.now()
	return CollectionResultEnvelope{
		SchemaVersion:   SchemaVersion,
		TaskID:          task.TaskID,
		JobID:           task.JobID,
		Status:          status,
		StartedAt:       startedAt,
		CompletedAt:     completedAt,
		DurationMS:      max(completedAt.Sub(startedAt).Milliseconds(), 0),
		CollectorResult: result,
		Error:           nil,
	}
}

// failed는 오류 정보와 선택적인 CollectorResult가 포함된 실패 결과를 만든다.
func (p *Processor) failed(
	task CollectionTask,
	startedAt time.Time,
	code string,
	message string,
	retryable bool,
	result *collector.SearchResult,
) CollectionResultEnvelope {
	completedAt := p.now()
	return CollectionResultEnvelope{
		SchemaVersion:   SchemaVersion,
		TaskID:          task.TaskID,
		JobID:           task.JobID,
		Status:          TaskStatusFailed,
		StartedAt:       startedAt,
		CompletedAt:     completedAt,
		DurationMS:      max(completedAt.Sub(startedAt).Milliseconds(), 0),
		CollectorResult: result,
		Error:           &TaskError{Code: code, Message: message, Retryable: retryable},
	}
}

// summarizeCollectorFailure는 Collector 오류 목록에서 Queue 재시도 판단에 필요한 대표 오류를 고른다.
func summarizeCollectorFailure(result collector.SearchResult) (string, string, bool) {
	if len(result.Errors) == 0 {
		return "COLLECTOR_FAILED", "Collector가 저장 가능한 결과를 반환하지 않았습니다", false
	}
	first := result.Errors[0]
	retryable := false
	for _, issue := range result.Errors {
		if issue.Retryable {
			retryable = true
			break
		}
	}
	return first.Code, first.Message, retryable
}
