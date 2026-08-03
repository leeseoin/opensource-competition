// Command batch는 ABC마트/29CM 공개 검색을 단계별 비교용 gzip NDJSON으로 수집한다.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/bulk"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

// queryFlags는 여러 -query 값을 입력 순서대로 보관한다.
type queryFlags []string

// String은 flag help에 표시할 검색어 목록을 반환한다.
func (q *queryFlags) String() string {
	return strings.Join(*q, ",")
}

// Set은 반복 입력한 검색어 하나를 목록에 추가한다.
func (q *queryFlags) Set(value string) error {
	*q = append(*q, value)
	return nil
}

// main은 대량 수집 설정을 읽고 안전 중단 결과를 JSON으로 출력한다.
func main() {
	var queries queryFlags
	merchant := flag.String("merchant", "", "판매처: abcmart 또는 29cm")
	flag.Var(&queries, "query", "검색어이며 여러 번 지정할 수 있음")
	maxItems := flag.Int("max-items", 100, "최대 고유 상품 수, 상한 10000")
	pageSize := flag.Int("page-size", 50, "페이지당 상품 수, 상한 50")
	requestBudget := flag.Int("request-budget", 10, "retry를 포함한 최대 요청 수")
	minInterval := flag.Duration("min-interval", time.Second, "페이지 요청 사이 최소 간격")
	maxRetries := flag.Int("max-retries", 1, "일시 오류 최대 재시도 횟수")
	maxWallTime := flag.Duration("max-wall-time", time.Hour, "한 작업의 최대 실행 시간")
	timeout := flag.Duration("timeout", 15*time.Second, "판매처 HTTP timeout")
	outputDir := flag.String("output-dir", "", "gzip NDJSON/checkpoint/summary 저장 디렉토리")
	resume := flag.Bool("resume", false, "기존 checkpoint에서 재개")
	flag.Parse()

	var searcher bulk.PageSearcher
	switch *merchant {
	case "abcmart":
		searcher = abcmart.NewSearcher(*timeout)
	case "29cm":
		searcher = twentyninecm.NewSearcher(*timeout)
	default:
		log.Fatalf("지원하지 않는 merchant입니다: %s", *merchant)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	stats, err := bulk.NewRunner(searcher).Run(ctx, bulk.Config{
		Merchant: *merchant, Queries: queries, OutputDir: *outputDir, MaxItems: *maxItems,
		PageSize: *pageSize, RequestBudget: *requestBudget, MinInterval: *minInterval,
		MaxRetries: *maxRetries, MaxWallTime: *maxWallTime, Resume: *resume,
	})
	if err != nil {
		log.Fatalf("대량 수집 실패: %v", err)
	}
	body, err := json.MarshalIndent(stats, "", "  ")
	if err != nil {
		log.Fatalf("수집 요약 JSON 변환 실패: %v", err)
	}
	fmt.Println(string(body))
	if stats.ContractFailCount > 0 || strings.HasPrefix(stats.StopReason, "http_") {
		os.Exit(2)
	}
}
