// Package config는 Collector 실행 설정을 불러오고 유효성을 검증한다.
package config

import (
	"fmt"
	"os"
	"time"
)

const (
	defaultHTTPAddress     = ":8090"
	defaultReadTimeout     = 5 * time.Second
	defaultWriteTimeout    = 10 * time.Second
	defaultIdleTimeout     = 60 * time.Second
	defaultShutdownTimeout = 10 * time.Second
)

// Config는 Collector process의 HTTP 주소와 lifecycle timeout 설정을 보관한다.
type Config struct {
	HTTPAddress     string
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
	IdleTimeout     time.Duration
	ShutdownTimeout time.Duration
}

// Load는 환경변수에서 Collector 설정을 읽고 누락된 값에 기본값을 적용한다.
// HTTP 주소가 비어 있거나 timeout이 "5s" 같은 양의 Go duration이 아니면 오류를 반환한다.
func Load() (Config, error) {
	cfg := Config{
		HTTPAddress:     envOrDefault("COLLECTOR_HTTP_ADDRESS", defaultHTTPAddress),
		ReadTimeout:     defaultReadTimeout,
		WriteTimeout:    defaultWriteTimeout,
		IdleTimeout:     defaultIdleTimeout,
		ShutdownTimeout: defaultShutdownTimeout,
	}

	if cfg.HTTPAddress == "" {
		return Config{}, fmt.Errorf("COLLECTOR_HTTP_ADDRESS must not be empty")
	}

	timeouts := []struct {
		name   string
		target *time.Duration
	}{
		{name: "COLLECTOR_READ_TIMEOUT", target: &cfg.ReadTimeout},
		{name: "COLLECTOR_WRITE_TIMEOUT", target: &cfg.WriteTimeout},
		{name: "COLLECTOR_IDLE_TIMEOUT", target: &cfg.IdleTimeout},
		{name: "COLLECTOR_SHUTDOWN_TIMEOUT", target: &cfg.ShutdownTimeout},
	}

	for _, timeout := range timeouts {
		value, ok := os.LookupEnv(timeout.name)
		if !ok {
			continue
		}

		duration, err := time.ParseDuration(value)
		if err != nil || duration <= 0 {
			return Config{}, fmt.Errorf("%s must be a positive duration: %q", timeout.name, value)
		}
		*timeout.target = duration
	}

	return cfg, nil
}

// envOrDefault는 환경변수가 없을 때 fallback을 반환하며, 명시된 빈 값은 그대로 보존한다.
func envOrDefault(name, fallback string) string {
	value, ok := os.LookupEnv(name)
	if !ok {
		return fallback
	}
	return value
}
