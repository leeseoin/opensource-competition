// Package main은 Collector HTTP process의 실행 진입점을 제공한다.
package main

import (
	"context"
	"log"
	"os/signal"
	"syscall"

	"github.com/leeseoin/opensource-competition/services/collector/internal/config"
	collectorhttp "github.com/leeseoin/opensource-competition/services/collector/internal/transport/http"
)

// main은 Collector를 실행하고 시작 또는 종료 오류가 발생하면 process를 실패로 종료한다.
func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

// run은 설정을 불러오고 종료 signal과 HTTP server lifecycle을 연결하며 발생한 오류를 반환한다.
func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	server := collectorhttp.NewServer(
		cfg.HTTPAddress,
		cfg.ReadTimeout,
		cfg.WriteTimeout,
		cfg.IdleTimeout,
		cfg.ShutdownTimeout,
	)
	return server.Run(ctx)
}
