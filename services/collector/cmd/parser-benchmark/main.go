// Command parser-benchmark는 저장된 동일 JSON fixture의 Go parser/normalizer 성능을 측정한다.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"syscall"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/comparison"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

// benchmarkResult는 fixture 반복 수와 wall/CPU/메모리/상품 처리량을 기록한다.
type benchmarkResult struct {
	Language              string  `json:"language"`
	Merchant              string  `json:"merchant"`
	FixtureBytes          int     `json:"fixture_bytes"`
	Iterations            int     `json:"iterations"`
	WarmupIterations      int     `json:"warmup_iterations"`
	ProductsPerIteration  int     `json:"products_per_iteration"`
	ProcessedProducts     int     `json:"processed_products"`
	WallSeconds           float64 `json:"wall_seconds"`
	CPUSeconds            float64 `json:"cpu_seconds"`
	ProductsPerWallSecond float64 `json:"products_per_wall_second"`
	PeakMemoryKiB         int64   `json:"peak_memory_kib"`
}

// fixtureConfig는 판매처별 공통 fixture 경로와 순수 변환 함수를 반환한다.
func fixtureConfig(merchant, fixtureRoot string) (string, func([]byte) (int, error), error) {
	request := collector.SearchRequest{
		RequestID: "parser-benchmark", Merchant: merchant, Query: "구두",
		RequestedAt: time.Unix(0, 0).UTC(), Limit: 50, Locale: "ko-KR", Currency: "KRW",
		Filters: collector.SearchFilters{},
	}
	collectedAt := time.Unix(0, 0).UTC()
	switch merchant {
	case "abcmart":
		path := filepath.Join(fixtureRoot, "abcmart", "search-products.json")
		return path, func(body []byte) (int, error) {
			products, _, _, err := abcmart.ParseSearchResponse(body, request, 1, "https://abcmart.a-rt.com/search", collectedAt)
			return validateProducts(products, merchant, err)
		}, nil
	case "29cm":
		path := filepath.Join(fixtureRoot, "twentyninecm", "search-items.json")
		return path, func(body []byte) (int, error) {
			products, _, _, err := twentyninecm.ParseSearchResponse(body, request, "https://www.29cm.co.kr/store/search", collectedAt)
			return validateProducts(products, merchant, err)
		}, nil
	default:
		return "", nil, fmt.Errorf("merchant는 abcmart 또는 29cm여야 합니다")
	}
}

// validateProducts는 운영 상품을 비교 계약으로 바꾸고 모든 상품을 검증한다.
func validateProducts(products []collector.Product, merchant string, parseErr error) (int, error) {
	if parseErr != nil {
		return 0, parseErr
	}
	for _, product := range products {
		if err := comparison.FromCollectorProduct(product, merchant).Validate(); err != nil {
			return 0, fmt.Errorf("Contract 검증 실패: %w", err)
		}
	}
	return len(products), nil
}

// runBenchmark는 JSON decode/판매처 정규화/Contract 검증을 지정 횟수만큼 측정한다.
func runBenchmark(merchant, fixtureRoot string, iterations, warmup int) (benchmarkResult, error) {
	if iterations < 1 || warmup < 0 {
		return benchmarkResult{}, fmt.Errorf("iterations는 1 이상이고 warmup은 0 이상이어야 합니다")
	}
	fixturePath, executeOnce, err := fixtureConfig(merchant, fixtureRoot)
	if err != nil {
		return benchmarkResult{}, err
	}
	body, err := os.ReadFile(fixturePath)
	if err != nil {
		return benchmarkResult{}, fmt.Errorf("fixture 읽기 실패: %w", err)
	}
	for index := 0; index < warmup; index++ {
		if _, err := executeOnce(body); err != nil {
			return benchmarkResult{}, err
		}
	}
	startedWall := time.Now()
	startedCPU := cpuTime()
	productsPerIteration := 0
	for index := 0; index < iterations; index++ {
		productsPerIteration, err = executeOnce(body)
		if err != nil {
			return benchmarkResult{}, err
		}
	}
	wall := time.Since(startedWall)
	cpu := cpuTime() - startedCPU
	processedProducts := productsPerIteration * iterations
	return benchmarkResult{
		Language: "go", Merchant: merchant, FixtureBytes: len(body), Iterations: iterations,
		WarmupIterations: warmup, ProductsPerIteration: productsPerIteration,
		ProcessedProducts: processedProducts, WallSeconds: wall.Seconds(), CPUSeconds: cpu.Seconds(),
		ProductsPerWallSecond: float64(processedProducts) / wall.Seconds(), PeakMemoryKiB: peakMemoryKiB(),
	}, nil
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

// main은 parser benchmark 인자를 읽고 결과를 JSON으로 출력한다.
func main() {
	merchant := flag.String("merchant", "", "판매처: abcmart 또는 29cm")
	iterations := flag.Int("iterations", 1_000, "실제 측정 반복 횟수")
	warmup := flag.Int("warmup", 100, "측정 전 준비 반복 횟수")
	fixtureRoot := flag.String("fixture-root", "testdata", "공통 저장 fixture 루트")
	flag.Parse()

	result, err := runBenchmark(*merchant, *fixtureRoot, *iterations, *warmup)
	if err != nil {
		log.Fatalf("parser benchmark 실패: %v", err)
	}
	body, err := json.MarshalIndent(result, "", "  ")
	if err != nil {
		log.Fatalf("benchmark JSON 변환 실패: %v", err)
	}
	fmt.Println(string(body))
}
