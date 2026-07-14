// Package config loads and validates runtime configuration for the Collector.
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

// Config contains the Collector process configuration. Load returns a fully
// populated Config or an error when an environment value is invalid.
type Config struct {
	HTTPAddress     string
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
	IdleTimeout     time.Duration
	ShutdownTimeout time.Duration
}

// Load reads Collector configuration from environment variables and supplies
// defaults for omitted values. It fails when an address is empty or a timeout
// is not a positive Go duration such as "5s".
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

func envOrDefault(name, fallback string) string {
	value, ok := os.LookupEnv(name)
	if !ok {
		return fallback
	}
	return value
}
