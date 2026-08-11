package messaging

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

const (
	// CollectionExchange는 작업과 결과 routing에 사용하는 durable direct exchange다.
	CollectionExchange = "purchase-research.collection.v1"
	// DeadLetterExchange는 처리할 수 없는 작업과 결과를 격리하는 exchange다.
	DeadLetterExchange = "purchase-research.collection.dlx.v1"
	// SearchTaskQueue는 검색 작업을 Go Worker에 전달하는 Queue다.
	SearchTaskQueue = "purchase-research.collection.search.v1"
	// SearchRetryQueue는 일시 오류 검색 작업을 5초 뒤 다시 전달하는 Queue다.
	SearchRetryQueue = "purchase-research.collection.search.retry.v1"
	// SearchDeadLetterQueue는 재시도 불가 또는 소진 작업을 보관하는 Queue다.
	SearchDeadLetterQueue = "purchase-research.collection.search.dlq.v1"
	// ResultQueue는 Collector 실행 결과를 Product Backend Worker에 전달하는 Queue다.
	ResultQueue = "purchase-research.collection.result.v1"
	// ResultDeadLetterQueue는 Product Backend가 검증할 수 없는 결과를 보관하는 Queue다.
	ResultDeadLetterQueue = "purchase-research.collection.result.dlq.v1"

	searchRoutingKey    = "collection.search"
	searchRetryKey      = "collection.search.retry"
	searchDeadLetterKey = "collection.search.dead"
	resultRoutingKey    = "collection.result"
	resultDeadLetterKey = "collection.result.dead"
)

// RabbitWorker는 RabbitMQ 검색 작업 소비, retry·DLQ 처리와 결과 발행을 담당한다.
type RabbitWorker struct {
	url       string
	processor *Processor
	logger    *log.Logger
}

// NewRabbitWorker는 AMQP URL과 작업 처리기를 입력받아 Worker를 생성한다.
// URL이 비었거나 processor가 nil이면 Run이 오류를 반환한다.
func NewRabbitWorker(url string, processor *Processor, logger *log.Logger) *RabbitWorker {
	if logger == nil {
		logger = log.Default()
	}
	return &RabbitWorker{url: url, processor: processor, logger: logger}
}

// Run은 context가 취소될 때까지 검색 작업을 처리한다.
// once가 true면 메시지 하나를 ACK·retry·DLQ 처리한 뒤 종료하며 연결·계약·발행 실패를 반환한다.
func (w *RabbitWorker) Run(ctx context.Context, once bool) error {
	if w.url == "" {
		return fmt.Errorf("PURCHASE_RESEARCH_RABBITMQ_URL이 비어 있습니다")
	}
	if w.processor == nil {
		return fmt.Errorf("CollectionTask Processor가 필요합니다")
	}

	connection, err := amqp.Dial(w.url)
	if err != nil {
		return fmt.Errorf("RabbitMQ 연결 실패: %w", err)
	}
	defer connection.Close()

	channel, err := connection.Channel()
	if err != nil {
		return fmt.Errorf("RabbitMQ channel 생성 실패: %w", err)
	}
	defer channel.Close()

	if err := declareTopology(channel); err != nil {
		return err
	}
	if err := channel.Qos(1, 0, false); err != nil {
		return fmt.Errorf("RabbitMQ prefetch 설정 실패: %w", err)
	}
	if err := channel.Confirm(false); err != nil {
		return fmt.Errorf("RabbitMQ publisher confirm 설정 실패: %w", err)
	}

	deliveries, err := channel.ConsumeWithContext(ctx, SearchTaskQueue, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("RabbitMQ 검색 작업 소비 시작 실패: %w", err)
	}

	for {
		select {
		case <-ctx.Done():
			return nil
		case delivery, ok := <-deliveries:
			if !ok {
				return fmt.Errorf("RabbitMQ 검색 작업 channel이 닫혔습니다")
			}
			if err := w.handleDelivery(ctx, channel, delivery); err != nil {
				return err
			}
			if once {
				return nil
			}
		}
	}
}

// handleDelivery는 메시지 하나를 검증하고 결과 발행 성공 뒤에만 원본을 확인 처리한다.
func (w *RabbitWorker) handleDelivery(
	ctx context.Context,
	channel *amqp.Channel,
	delivery amqp.Delivery,
) error {
	task, err := decodeTask(delivery.Body)
	if err != nil {
		w.logger.Printf("유효하지 않은 CollectionTask를 DLQ로 이동합니다: %v", err)
		return delivery.Nack(false, false)
	}

	result := w.processor.Process(ctx, task)
	if result.Status == TaskStatusFailed && result.Error != nil && result.Error.Retryable && task.CanRetry() {
		next := task.NextAttempt()
		body, marshalErr := json.Marshal(next)
		if marshalErr != nil {
			return fmt.Errorf("재시도 작업 JSON 생성 실패: %w", marshalErr)
		}
		if publishErr := publishConfirmed(ctx, channel, searchRetryKey, next.TaskID, body); publishErr != nil {
			_ = delivery.Nack(false, true)
			return publishErr
		}
		w.logger.Printf("작업 %s를 재시도 Queue로 이동했습니다(attempt=%d)", task.TaskID, next.Attempt)
		return delivery.Ack(false)
	}

	body, err := json.Marshal(result)
	if err != nil {
		_ = delivery.Nack(false, true)
		return fmt.Errorf("CollectionResult JSON 생성 실패: %w", err)
	}
	if err := publishConfirmed(ctx, channel, resultRoutingKey, task.TaskID, body); err != nil {
		_ = delivery.Nack(false, true)
		return err
	}

	if result.Status == TaskStatusFailed {
		w.logger.Printf("작업 %s가 실패해 원본 작업을 DLQ로 이동했습니다", task.TaskID)
		return delivery.Nack(false, false)
	}
	w.logger.Printf("작업 %s 처리 완료(status=%s)", task.TaskID, result.Status)
	return delivery.Ack(false)
}

// decodeTask는 알 수 없는 필드와 뒤따르는 JSON 값을 거부하며 CollectionTask를 검사한다.
func decodeTask(body []byte) (CollectionTask, error) {
	var task CollectionTask
	decoder := json.NewDecoder(bytes.NewReader(body))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&task); err != nil {
		return CollectionTask{}, fmt.Errorf("CollectionTask JSON 해석 실패: %w", err)
	}
	var extra any
	if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
		return CollectionTask{}, fmt.Errorf("CollectionTask에는 JSON 객체 하나만 허용됩니다")
	}
	if err := task.Validate(); err != nil {
		return CollectionTask{}, err
	}
	return task, nil
}

// publishConfirmed는 persistent 메시지를 발행하고 broker 확인을 받은 경우에만 성공한다.
func publishConfirmed(
	ctx context.Context,
	channel *amqp.Channel,
	routingKey string,
	messageID string,
	body []byte,
) error {
	confirmation, err := channel.PublishWithDeferredConfirmWithContext(
		ctx,
		CollectionExchange,
		routingKey,
		false,
		false,
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			MessageId:    messageID,
			Timestamp:    time.Now(),
			Body:         body,
		},
	)
	if err != nil {
		return fmt.Errorf("RabbitMQ 메시지 발행 실패: %w", err)
	}
	if confirmation == nil {
		return fmt.Errorf("RabbitMQ publisher confirm을 받지 못했습니다")
	}
	acknowledged, err := confirmation.WaitContext(ctx)
	if err != nil {
		return fmt.Errorf("RabbitMQ publisher confirm 대기 실패: %w", err)
	}
	if !acknowledged {
		return fmt.Errorf("RabbitMQ broker가 메시지를 확인하지 않았습니다")
	}
	return nil
}

// declareTopology는 작업·retry·결과·DLQ exchange와 Queue를 멱등하게 선언한다.
func declareTopology(channel *amqp.Channel) error {
	if err := channel.ExchangeDeclare(CollectionExchange, "direct", true, false, false, false, nil); err != nil {
		return fmt.Errorf("Collection exchange 선언 실패: %w", err)
	}
	if err := channel.ExchangeDeclare(DeadLetterExchange, "direct", true, false, false, false, nil); err != nil {
		return fmt.Errorf("Dead-letter exchange 선언 실패: %w", err)
	}

	queues := []struct {
		name      string
		bindKey   string
		exchange  string
		arguments amqp.Table
	}{
		{
			name: SearchTaskQueue, bindKey: searchRoutingKey, exchange: CollectionExchange,
			arguments: amqp.Table{
				"x-dead-letter-exchange":    DeadLetterExchange,
				"x-dead-letter-routing-key": searchDeadLetterKey,
				"x-max-priority":            int32(100),
			},
		},
		{
			name: SearchRetryQueue, bindKey: searchRetryKey, exchange: CollectionExchange,
			arguments: amqp.Table{
				"x-message-ttl":             int32(5000),
				"x-dead-letter-exchange":    CollectionExchange,
				"x-dead-letter-routing-key": searchRoutingKey,
			},
		},
		{name: SearchDeadLetterQueue, bindKey: searchDeadLetterKey, exchange: DeadLetterExchange},
		{
			name: ResultQueue, bindKey: resultRoutingKey, exchange: CollectionExchange,
			arguments: amqp.Table{
				"x-dead-letter-exchange":    DeadLetterExchange,
				"x-dead-letter-routing-key": resultDeadLetterKey,
			},
		},
		{name: ResultDeadLetterQueue, bindKey: resultDeadLetterKey, exchange: DeadLetterExchange},
	}
	for _, definition := range queues {
		if _, err := channel.QueueDeclare(
			definition.name,
			true,
			false,
			false,
			false,
			definition.arguments,
		); err != nil {
			return fmt.Errorf("RabbitMQ Queue %s 선언 실패: %w", definition.name, err)
		}
		if err := channel.QueueBind(
			definition.name,
			definition.bindKey,
			definition.exchange,
			false,
			nil,
		); err != nil {
			return fmt.Errorf("RabbitMQ Queue %s binding 실패: %w", definition.name, err)
		}
	}
	return nil
}
