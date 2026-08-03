package bulk_test

import (
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/bulk"
	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

// fakeSearcher는 페이지별 CollectorResult를 반환하고 호출 페이지를 기록한다.
type fakeSearcher struct {
	results map[int]collector.SearchResult
	pages   []int
}

// SearchPage는 지정 페이지의 테스트 결과를 반환한다.
func (f *fakeSearcher) SearchPage(_ context.Context, _ collector.SearchRequest, page int) collector.SearchResult {
	f.pages = append(f.pages, page)
	return f.results[page]
}

// TestRunnerDeduplicatesAndReachesTarget은 페이지 사이 중복을 제거하고 목표에서 중단하는지 검증한다.
func TestRunnerDeduplicatesAndReachesTarget(t *testing.T) {
	first := product("1")
	second := product("2")
	third := product("3")
	searcher := &fakeSearcher{results: map[int]collector.SearchResult{
		1: successResult([]collector.Product{first, second}, true),
		2: successResult([]collector.Product{second, third}, false),
	}}
	output := t.TempDir()
	stats, err := bulk.NewRunner(searcher).Run(context.Background(), config(output, 3, 3, false))
	if err != nil {
		t.Fatalf("run: %v", err)
	}
	if stats.UniqueCount != 3 || stats.DuplicateCount != 1 || stats.ContractPassCount != 3 || stats.StopReason != "target_reached" {
		t.Fatalf("stats = %#v", stats)
	}
	if count := countGzipLines(t, filepath.Join(output, "products.ndjson.gz")); count != 3 {
		t.Fatalf("saved lines = %d", count)
	}
}

// TestRunnerResumesFromCheckpoint는 요청 예산으로 중단한 작업이 다음 페이지에서 재개되는지 검증한다.
func TestRunnerResumesFromCheckpoint(t *testing.T) {
	output := t.TempDir()
	firstSearcher := &fakeSearcher{results: map[int]collector.SearchResult{
		1: successResult([]collector.Product{product("1"), product("2")}, true),
	}}
	firstStats, err := bulk.NewRunner(firstSearcher).Run(context.Background(), config(output, 3, 1, false))
	if err != nil {
		t.Fatalf("first run: %v", err)
	}
	if firstStats.StopReason != "request_budget_exhausted" {
		t.Fatalf("first stats = %#v", firstStats)
	}

	secondSearcher := &fakeSearcher{results: map[int]collector.SearchResult{
		2: successResult([]collector.Product{product("3")}, false),
	}}
	secondStats, err := bulk.NewRunner(secondSearcher).Run(context.Background(), config(output, 3, 1, true))
	if err != nil {
		t.Fatalf("second run: %v", err)
	}
	if !secondStats.CheckpointResumed || secondStats.UniqueCount != 3 || len(secondSearcher.pages) != 1 || secondSearcher.pages[0] != 2 {
		t.Fatalf("second stats=%#v pages=%v", secondStats, secondSearcher.pages)
	}
	if count := countGzipLines(t, filepath.Join(output, "products.ndjson.gz")); count != 3 {
		t.Fatalf("saved lines = %d", count)
	}
}

// TestRunnerStopsHTTP429WithoutRetry는 요청 제한 오류를 재시도하지 않고 즉시 중단하는지 검증한다.
func TestRunnerStopsHTTP429WithoutRetry(t *testing.T) {
	searcher := &fakeSearcher{results: map[int]collector.SearchResult{
		1: {
			Status: collector.StatusTemporarilyUnavailable,
			Errors: []collector.Issue{{Code: "HTTP_ERROR", Message: "판매처가 HTTP 429를 반환했습니다", Retryable: true}},
		},
	}}
	stats, err := bulk.NewRunner(searcher).Run(context.Background(), config(t.TempDir(), 100, 10, false))
	if err != nil {
		t.Fatalf("run: %v", err)
	}
	if stats.StopReason != "http_429" || stats.RequestCount != 1 || stats.HTTP429Count != 1 {
		t.Fatalf("stats = %#v", stats)
	}
}

// TestRunnerRejectsResumeWithoutCheckpoint는 이전 결과만 있는 불완전 상태에 이어 쓰지 않는지 검증한다.
func TestRunnerRejectsResumeWithoutCheckpoint(t *testing.T) {
	output := t.TempDir()
	if err := os.WriteFile(filepath.Join(output, "products.ndjson.gz"), []byte("stale"), 0o644); err != nil {
		t.Fatalf("write stale result: %v", err)
	}
	searcher := &fakeSearcher{results: map[int]collector.SearchResult{}}
	if _, err := bulk.NewRunner(searcher).Run(context.Background(), config(output, 100, 10, true)); err == nil {
		t.Fatal("checkpoint 없는 resume가 성공했습니다")
	}
}

// config는 runner 테스트의 정상 대량 수집 설정을 생성한다.
func config(output string, maxItems, budget int, resume bool) bulk.Config {
	return bulk.Config{
		Merchant: "abcmart", Queries: []string{"구두"}, OutputDir: output,
		MaxItems: maxItems, PageSize: 50, RequestBudget: budget,
		MinInterval: 0, MaxRetries: 1, MaxWallTime: time.Minute, Resume: resume,
	}
}

// product는 비교 계약을 통과할 최소 운영 상품을 생성한다.
func product(id string) collector.Product {
	return collector.Product{
		ExternalID: id, Name: "상품 " + id, ProductURL: "https://example.com/" + id,
		ImageURLs: []string{}, Price: &collector.Money{Amount: 10_000, Currency: "KRW"},
		StockStatus: collector.StockAvailable, Options: []collector.Option{}, Reviews: []collector.Review{},
	}
}

// successResult는 다음 페이지 여부가 있는 정상 CollectorResult를 생성한다.
func successResult(products []collector.Product, hasNext bool) collector.SearchResult {
	return collector.SearchResult{Status: collector.StatusSuccess, Products: products, HasNext: &hasNext, Errors: []collector.Issue{}}
}

// countGzipLines는 연결된 gzip stream의 NDJSON 줄 수와 JSON 형식을 검증한다.
func countGzipLines(t *testing.T, path string) int {
	t.Helper()
	file, err := os.Open(path)
	if err != nil {
		t.Fatalf("open gzip: %v", err)
	}
	defer file.Close()
	reader, err := gzip.NewReader(file)
	if err != nil {
		t.Fatalf("new gzip reader: %v", err)
	}
	defer reader.Close()
	decoder := json.NewDecoder(reader)
	count := 0
	for {
		var value map[string]any
		err := decoder.Decode(&value)
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatalf("decode line %d: %v", count, err)
		}
		if value["source_product_id"] != fmt.Sprintf("%v", value["source_product_id"]) {
			t.Fatalf("invalid product: %#v", value)
		}
		count++
	}
	return count
}
