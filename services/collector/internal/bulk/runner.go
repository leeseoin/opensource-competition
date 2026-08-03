// Package bulk는 비교 실험용 대량 pagination, 중복 제거, checkpoint와 압축 저장을 제공한다.
package bulk

import (
	"bufio"
	"compress/gzip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"syscall"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/comparison"
)

// PageSearcher는 판매처의 지정 검색 페이지 한 건을 수집하는 대량 실행기 계약이다.
type PageSearcher interface {
	SearchPage(context.Context, collector.SearchRequest, int) collector.SearchResult
}

// Config는 한 판매처 대량 수집의 검색어, 안전 상한과 저장 위치를 정의한다.
type Config struct {
	Merchant      string
	Queries       []string
	OutputDir     string
	MaxItems      int
	PageSize      int
	RequestBudget int
	MinInterval   time.Duration
	MaxRetries    int
	MaxWallTime   time.Duration
	Resume        bool
}

// Validate는 대량 수집 설정이 프로젝트의 안전 상한을 만족하는지 검사한다.
func (c Config) Validate() error {
	if c.Merchant != "abcmart" && c.Merchant != "29cm" {
		return fmt.Errorf("merchant는 abcmart 또는 29cm여야 합니다")
	}
	if len(c.Queries) == 0 {
		return fmt.Errorf("query가 하나 이상 필요합니다")
	}
	for _, query := range c.Queries {
		if strings.TrimSpace(query) == "" {
			return fmt.Errorf("빈 query는 사용할 수 없습니다")
		}
	}
	if c.OutputDir == "" {
		return fmt.Errorf("output directory가 필요합니다")
	}
	if c.MaxItems < 1 || c.MaxItems > 10_000 {
		return fmt.Errorf("max items는 1 이상 10000 이하여야 합니다")
	}
	if c.PageSize < 1 || c.PageSize > 50 {
		return fmt.Errorf("page size는 1 이상 50 이하여야 합니다")
	}
	if c.RequestBudget < 1 {
		return fmt.Errorf("request budget은 1 이상이어야 합니다")
	}
	if c.MinInterval < 0 || c.MaxWallTime <= 0 {
		return fmt.Errorf("요청 간격은 0 이상이고 wall time은 0보다 커야 합니다")
	}
	if c.MaxRetries < 0 || c.MaxRetries > 3 {
		return fmt.Errorf("max retries는 0 이상 3 이하여야 합니다")
	}
	return nil
}

// Stats는 실제 수집 건수, 오류와 비교용 성능 지표를 기록한다.
type Stats struct {
	Merchant                string   `json:"merchant"`
	TargetCount             int      `json:"target_count"`
	UniqueCount             int      `json:"unique_count"`
	ReceivedCount           int      `json:"received_count"`
	SkippedAfterTargetCount int      `json:"skipped_after_target_count"`
	DuplicateCount          int      `json:"duplicate_count"`
	ContractPassCount       int      `json:"contract_pass_count"`
	ContractFailCount       int      `json:"contract_fail_count"`
	MissingFieldCount       int      `json:"missing_field_count"`
	RequestCount            int      `json:"request_count"`
	ErrorCount              int      `json:"error_count"`
	HTTP429Count            int      `json:"http_429_count"`
	WallSeconds             float64  `json:"wall_seconds"`
	CPUSeconds              float64  `json:"cpu_seconds"`
	PeakMemoryKiB           int64    `json:"peak_memory_kib"`
	StopReason              string   `json:"stop_reason"`
	CheckpointResumed       bool     `json:"checkpoint_resumed"`
	Errors                  []string `json:"errors"`
}

// checkpoint는 다음 검색어/페이지와 이미 저장한 상품 ID를 보관한다.
type checkpoint struct {
	Merchant   string   `json:"merchant"`
	Queries    []string `json:"queries"`
	QueryIndex int      `json:"query_index"`
	NextPage   int      `json:"next_page"`
	SeenIDs    []string `json:"seen_ids"`
}

// Runner는 판매처 PageSearcher를 순차 실행해 비교 상품을 저장한다.
type Runner struct {
	searcher PageSearcher
	now      func() time.Time
}

// NewRunner는 대량 수집에 사용할 판매처 PageSearcher를 연결한다.
func NewRunner(searcher PageSearcher) *Runner {
	return &Runner{searcher: searcher, now: time.Now}
}

// Run은 목표 또는 안전 중단 조건까지 수집하고 gzip NDJSON/checkpoint/summary를 저장한다.
func (r *Runner) Run(ctx context.Context, config Config) (Stats, error) {
	if r.searcher == nil {
		return Stats{}, fmt.Errorf("page searcher가 필요합니다")
	}
	if err := config.Validate(); err != nil {
		return Stats{}, err
	}
	if err := os.MkdirAll(config.OutputDir, 0o755); err != nil {
		return Stats{}, fmt.Errorf("output directory 생성 실패: %w", err)
	}

	checkpointPath := filepath.Join(config.OutputDir, "checkpoint.json")
	productsPath := filepath.Join(config.OutputDir, "products.ndjson.gz")
	summaryPath := filepath.Join(config.OutputDir, "summary.json")
	if !config.Resume {
		if err := os.Remove(checkpointPath); err != nil && !errors.Is(err, os.ErrNotExist) {
			return Stats{}, fmt.Errorf("이전 checkpoint 제거 실패: %w", err)
		}
	}
	state, err := loadCheckpoint(config, checkpointPath, productsPath)
	if err != nil {
		return Stats{}, err
	}
	seen := make(map[string]bool, len(state.SeenIDs))
	for _, id := range state.SeenIDs {
		seen[id] = true
	}
	stats := Stats{
		Merchant: config.Merchant, TargetCount: config.MaxItems, UniqueCount: len(seen),
		ContractPassCount: len(seen), CheckpointResumed: config.Resume && len(seen) > 0,
		Errors: []string{},
	}

	file, gzipWriter, bufferedWriter, err := openOutput(productsPath, config.Resume)
	if err != nil {
		return Stats{}, err
	}
	startedWall := time.Now()
	startedCPU := cpuTime()
	collectErr := r.collect(ctx, config, state, seen, &stats, bufferedWriter, gzipWriter, checkpointPath, startedWall)
	closeErr := errors.Join(bufferedWriter.Flush(), gzipWriter.Close(), file.Close())
	stats.WallSeconds = time.Since(startedWall).Seconds()
	stats.CPUSeconds = (cpuTime() - startedCPU).Seconds()
	stats.PeakMemoryKiB = peakMemoryKiB()
	summaryErr := writeJSONAtomic(summaryPath, stats)
	return stats, errors.Join(collectErr, closeErr, summaryErr)
}

// collect는 검색어/페이지를 돌며 계약을 통과한 고유 상품만 결과에 추가한다.
func (r *Runner) collect(
	ctx context.Context,
	config Config,
	state checkpoint,
	seen map[string]bool,
	stats *Stats,
	output *bufio.Writer,
	gzipWriter *gzip.Writer,
	checkpointPath string,
	startedWall time.Time,
) error {
	for queryIndex := state.QueryIndex; queryIndex < len(config.Queries); queryIndex++ {
		query := config.Queries[queryIndex]
		page := 1
		if queryIndex == state.QueryIndex {
			page = state.NextPage
		}
		for {
			if len(seen) >= config.MaxItems {
				stats.StopReason = "target_reached"
				return nil
			}
			if stats.RequestCount >= config.RequestBudget {
				stats.StopReason = "request_budget_exhausted"
				return nil
			}
			if time.Since(startedWall) >= config.MaxWallTime {
				stats.StopReason = "wall_time_exhausted"
				return nil
			}

			result, ok := r.fetchWithRetry(ctx, config, query, page, stats)
			if !ok {
				if stats.StopReason == "" {
					stats.StopReason = "request_failed"
				}
				return nil
			}
			stats.ReceivedCount += len(result.Products)
			for index, product := range result.Products {
				if seen[product.ExternalID] {
					stats.DuplicateCount++
					continue
				}
				unified := comparison.FromCollectorProduct(product, config.Merchant)
				if err := unified.Validate(); err != nil {
					stats.ContractFailCount++
					stats.ErrorCount++
					rememberError(stats, fmt.Sprintf("contract %s/%d/%s: %v", query, page, product.ExternalID, err))
					continue
				}
				encoded, err := json.Marshal(unified)
				if err != nil {
					return fmt.Errorf("상품 JSON 변환 실패: %w", err)
				}
				if _, err := output.Write(append(encoded, '\n')); err != nil {
					return fmt.Errorf("상품 결과 저장 실패: %w", err)
				}
				seen[product.ExternalID] = true
				stats.UniqueCount = len(seen)
				stats.ContractPassCount++
				stats.MissingFieldCount += unified.MissingFieldCount()
				if len(seen) >= config.MaxItems {
					stats.SkippedAfterTargetCount += len(result.Products) - index - 1
					break
				}
			}

			reachedTarget := len(seen) >= config.MaxItems
			nextPage := page + 1
			if reachedTarget {
				nextPage = page
			}
			if err := output.Flush(); err != nil {
				return fmt.Errorf("상품 buffer flush 실패: %w", err)
			}
			if err := gzipWriter.Flush(); err != nil {
				return fmt.Errorf("상품 gzip flush 실패: %w", err)
			}
			if err := saveCheckpoint(checkpointPath, config, queryIndex, nextPage, seen); err != nil {
				return err
			}
			if reachedTarget {
				stats.StopReason = "target_reached"
				return nil
			}
			if result.HasNext == nil || !*result.HasNext {
				break
			}
			page = nextPage
			if err := wait(ctx, config.MinInterval); err != nil {
				stats.StopReason = "context_canceled"
				return nil
			}
		}
		if err := saveCheckpoint(checkpointPath, config, queryIndex+1, 1, seen); err != nil {
			return err
		}
	}
	stats.StopReason = "queries_exhausted"
	return nil
}

// fetchWithRetry는 접근 제한을 즉시 중단하고 retryable 오류만 설정 상한 안에서 재시도한다.
func (r *Runner) fetchWithRetry(ctx context.Context, config Config, query string, page int, stats *Stats) (collector.SearchResult, bool) {
	for attempt := 0; attempt <= config.MaxRetries; attempt++ {
		stats.RequestCount++
		request := collector.SearchRequest{
			RequestID: fmt.Sprintf("bulk-%s-%d-%d", config.Merchant, r.now().UnixNano(), stats.RequestCount),
			Merchant:  config.Merchant, Query: query, RequestedAt: r.now(), Limit: config.PageSize,
			Locale: "ko-KR", Currency: "KRW", Filters: collector.SearchFilters{},
		}
		result := r.searcher.SearchPage(ctx, request, page)
		if (result.Status == collector.StatusSuccess || result.Status == collector.StatusPartial) && result.HasNext != nil {
			return result, true
		}
		stats.ErrorCount++
		message, retryable := resultError(result)
		rememberError(stats, fmt.Sprintf("%s/%d attempt=%d: %s", query, page, attempt+1, message))
		if strings.Contains(message, "HTTP 429") {
			stats.HTTP429Count++
			stats.StopReason = "http_429"
			return collector.SearchResult{}, false
		}
		if result.Status == collector.StatusBlocked || strings.Contains(message, "HTTP 401") || strings.Contains(message, "HTTP 403") {
			stats.StopReason = "http_blocked"
			return collector.SearchResult{}, false
		}
		if !retryable || attempt >= config.MaxRetries {
			stats.StopReason = "request_failed"
			return collector.SearchResult{}, false
		}
		if stats.RequestCount >= config.RequestBudget {
			stats.StopReason = "request_budget_exhausted"
			return collector.SearchResult{}, false
		}
		if err := wait(ctx, config.MinInterval); err != nil {
			stats.StopReason = "context_canceled"
			return collector.SearchResult{}, false
		}
	}
	return collector.SearchResult{}, false
}

// resultError는 CollectorResult 오류를 요약하고 재시도 가능 여부를 계산한다.
func resultError(result collector.SearchResult) (string, bool) {
	if len(result.Errors) == 0 {
		return fmt.Sprintf("status=%s, 오류 정보 없음", result.Status), false
	}
	messages := make([]string, 0, len(result.Errors))
	retryable := false
	for _, issue := range result.Errors {
		messages = append(messages, issue.Code+": "+issue.Message)
		retryable = retryable || issue.Retryable
	}
	return strings.Join(messages, "; "), retryable
}

// loadCheckpoint는 resume 상태와 결과 파일의 일관성을 확인해 시작 위치를 반환한다.
func loadCheckpoint(config Config, checkpointPath, productsPath string) (checkpoint, error) {
	empty := checkpoint{Merchant: config.Merchant, Queries: config.Queries, QueryIndex: 0, NextPage: 1, SeenIDs: []string{}}
	if !config.Resume {
		return empty, nil
	}
	body, err := os.ReadFile(checkpointPath)
	if errors.Is(err, os.ErrNotExist) {
		if info, statErr := os.Stat(productsPath); statErr == nil && info.Size() > 0 {
			return checkpoint{}, fmt.Errorf("resume 결과 파일은 있지만 checkpoint가 없습니다")
		} else if statErr != nil && !errors.Is(statErr, os.ErrNotExist) {
			return checkpoint{}, fmt.Errorf("resume 결과 파일 확인 실패: %w", statErr)
		}
		return empty, nil
	}
	if err != nil {
		return checkpoint{}, fmt.Errorf("checkpoint 읽기 실패: %w", err)
	}
	var saved checkpoint
	if err := json.Unmarshal(body, &saved); err != nil {
		return checkpoint{}, fmt.Errorf("checkpoint JSON 해석 실패: %w", err)
	}
	if saved.Merchant != config.Merchant || !equalStrings(saved.Queries, config.Queries) {
		return checkpoint{}, fmt.Errorf("checkpoint의 판매처 또는 검색어가 현재 설정과 다릅니다")
	}
	if len(saved.SeenIDs) > 0 {
		if _, err := os.Stat(productsPath); err != nil {
			return checkpoint{}, fmt.Errorf("checkpoint에 상품이 있지만 결과 파일을 확인할 수 없습니다: %w", err)
		}
	}
	return saved, nil
}

// openOutput은 새 실행은 결과를 비우고 resume 실행은 새 gzip stream을 이어 붙인다.
func openOutput(path string, resume bool) (*os.File, *gzip.Writer, *bufio.Writer, error) {
	flags := os.O_CREATE | os.O_WRONLY | os.O_TRUNC
	if resume {
		flags = os.O_CREATE | os.O_WRONLY | os.O_APPEND
	}
	file, err := os.OpenFile(path, flags, 0o644)
	if err != nil {
		return nil, nil, nil, fmt.Errorf("결과 파일 열기 실패: %w", err)
	}
	gzipWriter := gzip.NewWriter(file)
	return file, gzipWriter, bufio.NewWriterSize(gzipWriter, 64*1024), nil
}

// saveCheckpoint는 완료한 페이지 상태를 상품 ID 정렬 후 원자적으로 저장한다.
func saveCheckpoint(path string, config Config, queryIndex, nextPage int, seen map[string]bool) error {
	ids := make([]string, 0, len(seen))
	for id := range seen {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return writeJSONAtomic(path, checkpoint{
		Merchant: config.Merchant, Queries: config.Queries, QueryIndex: queryIndex, NextPage: nextPage, SeenIDs: ids,
	})
}

// writeJSONAtomic은 임시 파일을 같은 디렉토리에서 교체해 부분 JSON을 남기지 않는다.
func writeJSONAtomic(path string, value any) error {
	body, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return fmt.Errorf("JSON 변환 실패: %w", err)
	}
	body = append(body, '\n')
	temporary := path + ".tmp"
	if err := os.WriteFile(temporary, body, 0o644); err != nil {
		return fmt.Errorf("임시 JSON 저장 실패: %w", err)
	}
	if err := os.Rename(temporary, path); err != nil {
		return fmt.Errorf("JSON 원자 교체 실패: %w", err)
	}
	return nil
}

// wait는 context 취소를 존중하며 다음 요청 전 최소 간격을 기다린다.
func wait(ctx context.Context, duration time.Duration) error {
	if duration <= 0 {
		return nil
	}
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

// rememberError는 summary가 과도하게 커지지 않도록 최근 오류를 최대 100개 보관한다.
func rememberError(stats *Stats, message string) {
	stats.Errors = append(stats.Errors, message)
	if len(stats.Errors) > 100 {
		stats.Errors = stats.Errors[len(stats.Errors)-100:]
	}
}

// equalStrings는 checkpoint 검색어가 현재 실행과 순서까지 같은지 확인한다.
func equalStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
}

// cpuTime은 현재 process의 user와 system CPU 누적 시간을 반환한다.
func cpuTime() time.Duration {
	var usage syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_SELF, &usage); err != nil {
		return 0
	}
	return time.Duration(usage.Utime.Sec+usage.Stime.Sec)*time.Second + time.Duration(usage.Utime.Usec+usage.Stime.Usec)*time.Microsecond
}

// peakMemoryKiB는 운영체제 단위 차이를 보정한 process 최대 resident memory를 반환한다.
func peakMemoryKiB() int64 {
	var usage syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_SELF, &usage); err != nil {
		return 0
	}
	if runtime.GOOS == "darwin" {
		return usage.Maxrss / 1024
	}
	return usage.Maxrss
}
