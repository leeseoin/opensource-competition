// Package render는 JavaScript가 실행된 공개 검색 화면 HTML을 headless Chrome으로 읽는다.
package render

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"time"
)

const maxRenderedHTMLBytes = 12 * 1024 * 1024

// Renderer는 공개 URL의 JavaScript 실행 후 DOM HTML을 반환하는 구현 계약이다.
type Renderer interface {
	Render(ctx context.Context, targetURL string) ([]byte, error)
}

// ChromeRenderer는 로컬 Chrome 실행 파일을 headless 모드로 호출한다.
type ChromeRenderer struct {
	binary            string
	virtualTimeBudget time.Duration
}

// NewChromeRenderer는 COLLECTOR_CHROME_BIN 또는 일반적인 OS별 경로에서 Chrome을 찾는 renderer를 만든다.
func NewChromeRenderer() *ChromeRenderer {
	return &ChromeRenderer{binary: findChromeBinary(), virtualTimeBudget: 3 * time.Second}
}

// NewChromeRendererWithBinary는 테스트와 배포 환경이 지정한 Chrome 경로로 renderer를 만든다.
func NewChromeRendererWithBinary(binary string, virtualTimeBudget time.Duration) *ChromeRenderer {
	if virtualTimeBudget <= 0 {
		virtualTimeBudget = 3 * time.Second
	}
	return &ChromeRenderer{binary: binary, virtualTimeBudget: virtualTimeBudget}
}

// Render는 headless Chrome의 dump-dom 결과를 크기 상한 내에서 반환한다.
func (r *ChromeRenderer) Render(ctx context.Context, targetURL string) ([]byte, error) {
	if r == nil || strings.TrimSpace(r.binary) == "" {
		return nil, fmt.Errorf("Chrome 실행 파일을 찾지 못했습니다. COLLECTOR_CHROME_BIN을 설정하세요")
	}
	budgetMS := r.virtualTimeBudget.Milliseconds()
	command := exec.CommandContext(ctx, r.binary,
		"--headless=new",
		"--disable-gpu",
		"--disable-background-networking",
		fmt.Sprintf("--virtual-time-budget=%d", budgetMS),
		"--dump-dom",
		targetURL,
	)
	output, err := command.Output()
	if err != nil {
		return nil, fmt.Errorf("Chrome HTML rendering 실패: %w", err)
	}
	if len(output) == 0 {
		return nil, fmt.Errorf("Chrome가 빈 HTML을 반환했습니다")
	}
	if len(output) > maxRenderedHTMLBytes {
		return nil, fmt.Errorf("rendering HTML이 %d bytes 상한을 넘었습니다", maxRenderedHTMLBytes)
	}
	return output, nil
}

// findChromeBinary는 환경변수, PATH, OS 기본 경로 순서로 Chrome 실행 파일을 찾는다.
func findChromeBinary() string {
	if configured := strings.TrimSpace(os.Getenv("COLLECTOR_CHROME_BIN")); configured != "" {
		return configured
	}
	for _, name := range []string{"google-chrome", "google-chrome-stable", "chromium", "chromium-browser"} {
		if path, err := exec.LookPath(name); err == nil {
			return path
		}
	}
	if runtime.GOOS == "darwin" {
		path := "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	return ""
}
