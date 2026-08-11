// Package main은 RabbitMQ CollectionTask를 처리하는 Collector Worker 진입점을 제공한다.
package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/app"
	"github.com/leeseoin/opensource-competition/services/collector/internal/messaging"
)

const defaultRabbitMQURL = "amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research"

// main은 Worker 실행 오류를 process 실패 코드로 전달한다.
func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

// run은 환경변수와 CLI 옵션을 읽어 RabbitMQ Worker lifecycle을 실행한다.
func run() error {
	once := flag.Bool("once", false, "메시지 하나를 처리한 뒤 종료합니다")
	flag.Parse()

	rabbitURL := os.Getenv("PURCHASE_RESEARCH_RABBITMQ_URL")
	if rabbitURL == "" {
		rabbitURL = defaultRabbitMQURL
	}
	timeout := 15 * time.Second
	if value := os.Getenv("COLLECTOR_WORKER_TIMEOUT"); value != "" {
		parsed, err := time.ParseDuration(value)
		if err != nil || parsed <= 0 {
			log.Printf("COLLECTOR_WORKER_TIMEOUT이 올바르지 않아 기본값 %s를 사용합니다", timeout)
		} else {
			timeout = parsed
		}
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	processor := messaging.NewProcessor(app.NewSearchRegistry(timeout), timeout, time.Now)
	worker := messaging.NewRabbitWorker(rabbitURL, processor, log.Default())
	return worker.Run(ctx, *once)
}
