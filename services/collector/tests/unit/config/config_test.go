package config_test

import (
	"os"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/config"
)

var collectorEnvironmentNames = []string{
	"COLLECTOR_HTTP_ADDRESS",
	"COLLECTOR_READ_TIMEOUT",
	"COLLECTOR_WRITE_TIMEOUT",
	"COLLECTOR_IDLE_TIMEOUT",
	"COLLECTOR_SHUTDOWN_TIMEOUT",
}

// TestLoadDefaults는 관련 환경변수가 없을 때 모든 기본 설정이 적용되는지 검증한다.
func TestLoadDefaults(t *testing.T) {
	clearCollectorEnvironment(t)
	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.HTTPAddress != ":8090" ||
		cfg.ReadTimeout != 5*time.Second ||
		cfg.WriteTimeout != 90*time.Second ||
		cfg.IdleTimeout != 60*time.Second ||
		cfg.ShutdownTimeout != 10*time.Second {
		t.Fatalf("default config = %#v", cfg)
	}
}

// TestLoadOverrides는 환경변수로 모든 설정을 덮어쓸 수 있는지 검증한다.
func TestLoadOverrides(t *testing.T) {
	clearCollectorEnvironment(t)
	t.Setenv("COLLECTOR_HTTP_ADDRESS", "127.0.0.1:18090")
	t.Setenv("COLLECTOR_READ_TIMEOUT", "1s")
	t.Setenv("COLLECTOR_WRITE_TIMEOUT", "2s")
	t.Setenv("COLLECTOR_IDLE_TIMEOUT", "3s")
	t.Setenv("COLLECTOR_SHUTDOWN_TIMEOUT", "4s")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.HTTPAddress != "127.0.0.1:18090" ||
		cfg.ReadTimeout != time.Second ||
		cfg.WriteTimeout != 2*time.Second ||
		cfg.IdleTimeout != 3*time.Second ||
		cfg.ShutdownTimeout != 4*time.Second {
		t.Fatalf("override config = %#v", cfg)
	}
}

// TestLoadRejectsInvalidValues는 빈 주소와 잘못된 timeout을 거부하는지 검증한다.
func TestLoadRejectsInvalidValues(t *testing.T) {
	testCases := []struct {
		name  string
		value string
	}{
		{name: "COLLECTOR_HTTP_ADDRESS", value: ""},
		{name: "COLLECTOR_READ_TIMEOUT", value: "invalid"},
		{name: "COLLECTOR_WRITE_TIMEOUT", value: "0s"},
		{name: "COLLECTOR_IDLE_TIMEOUT", value: "-1s"},
		{name: "COLLECTOR_SHUTDOWN_TIMEOUT", value: "1"},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			clearCollectorEnvironment(t)
			t.Setenv(testCase.name, testCase.value)
			if _, err := config.Load(); err == nil {
				t.Fatalf("Load() error = nil for %s=%q", testCase.name, testCase.value)
			}
		})
	}
}

// clearCollectorEnvironment는 설정 테스트가 외부 환경변수에 의존하지 않도록 관련 값을 임시 해제한다.
func clearCollectorEnvironment(t *testing.T) {
	t.Helper()
	for _, name := range collectorEnvironmentNames {
		value, ok := os.LookupEnv(name)
		if err := os.Unsetenv(name); err != nil {
			t.Fatalf("os.Unsetenv(%q): %v", name, err)
		}
		t.Cleanup(func() {
			if ok {
				if err := os.Setenv(name, value); err != nil {
					t.Errorf("os.Setenv(%q): %v", name, err)
				}
				return
			}
			if err := os.Unsetenv(name); err != nil {
				t.Errorf("os.Unsetenv(%q): %v", name, err)
			}
		})
	}
}
