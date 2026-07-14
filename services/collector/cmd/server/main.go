package main

import (
	"context"
	"log"
	"os/signal"
	"syscall"

	"github.com/leeseoin/opensource-competition/services/collector/internal/config"
	collectorhttp "github.com/leeseoin/opensource-competition/services/collector/internal/transport/http"
)

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

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
