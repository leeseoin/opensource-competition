package com.purchasesearch.product_backend.collection.messaging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService;
import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService.ProcessingOutcome;
import com.rabbitmq.client.Channel;

/**
 * CollectionResultConsumer는 RabbitMQ 결과를 수동 ACK하고 처리할 수 없는 메시지를 DLQ로 보낸다.
 */
@Component
public class CollectionResultConsumer {

	private static final Logger log = LoggerFactory.getLogger(CollectionResultConsumer.class);

	private final CollectionResultMessageService messageService;

	/**
	 * Queue message 처리 서비스를 Consumer에 연결한다.
	 *
	 * @param messageService Queue 결과 검증과 저장 서비스
	 */
	public CollectionResultConsumer(CollectionResultMessageService messageService) {
		this.messageService = messageService;
	}

	/**
	 * 결과 메시지를 처리하고 성공 시 ACK, 계약 위반이나 저장 실패 시 reject 처리한다.
	 * 결과 Queue의 dead-letter 설정에 따라 reject된 메시지는 결과 DLQ로 이동한다.
	 *
	 * @param message RabbitMQ 원본 메시지
	 * @param channel 수동 ACK에 사용할 RabbitMQ channel
	 * @throws IOException broker ACK 또는 reject 전송에 실패한 경우
	 */
	@RabbitListener(queues = CollectionQueueNames.RESULT_QUEUE, ackMode = "MANUAL")
	public void consume(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		ProcessingOutcome outcome;
		try {
			outcome = messageService.process(message.getBody());
		} catch (RuntimeException exception) {
			log.warn("CollectionResult 처리 실패로 메시지를 DLQ로 이동합니다: {}", exception.getMessage());
			channel.basicReject(deliveryTag, false);
			return;
		}

		channel.basicAck(deliveryTag, false);
		log.info("CollectionResult 처리 완료: outcome={}", outcome);
	}
}
