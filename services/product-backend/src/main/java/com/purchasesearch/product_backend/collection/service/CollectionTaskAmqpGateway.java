package com.purchasesearch.product_backend.collection.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;

import tools.jackson.databind.ObjectMapper;

/**
 * CollectionTaskAmqpGateway는 작업 한 건을 RabbitMQ에 persistent 메시지로 발행하고 broker ACK를
 * 확인하는 저수준 동작만 담당한다. {@link CollectionTaskPublisher}(단건 API, HTTP 스레드에서 직접
 * 호출)와 {@link CollectionTaskPagePublishRunner}(다중 페이지 API, 배경 스레드에서 호출)가 이
 * 동작을 공유하기 위해 별도 Bean으로 분리했다 — 두 호출자가 서로를 의존하는 순환 참조를 만들지
 * 않기 위함이다.
 */
@Component
public class CollectionTaskAmqpGateway {

	private static final long CONFIRM_TIMEOUT_SECONDS = 5;

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * @param rabbitTemplate Spring AMQP 발행 도구
	 * @param objectMapper Spring Boot 공통 JSON mapper
	 */
	public CollectionTaskAmqpGateway(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 작업 한 건을 persistent 메시지로 발행하고 RabbitMQ broker ACK를 확인한다.
	 *
	 * @param task 발행할 단일 페이지 작업
	 * @throws CollectionTaskPublishException 직렬화, RabbitMQ 발행 또는 confirm에 실패한 경우
	 */
	public void publish(CollectionTaskMessage task) {
		CorrelationData correlationData = new CorrelationData(task.taskId());
		Message message = createMessage(task);

		try {
			rabbitTemplate.send(
					CollectionQueueNames.COLLECTION_EXCHANGE,
					CollectionQueueNames.SEARCH_ROUTING_KEY,
					message,
					correlationData);
			CorrelationData.Confirm confirm = correlationData.getFuture()
					.get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!confirm.ack()) {
				throw new CollectionTaskPublishException(
						"RabbitMQ가 수집 작업을 확인하지 않았습니다: " + confirm.reason());
			}
			if (correlationData.getReturned() != null) {
				throw new CollectionTaskPublishException("수집 작업을 받을 RabbitMQ Queue가 없습니다.");
			}
		} catch (CollectionTaskPublishException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new CollectionTaskPublishException("RabbitMQ 작업 확인 대기가 중단됐습니다.", exception);
		} catch (Exception exception) {
			throw new CollectionTaskPublishException("RabbitMQ에 수집 작업을 발행하지 못했습니다.", exception);
		}
	}

	/**
	 * Queue 작업을 persistent JSON RabbitMQ 메시지로 직렬화한다.
	 *
	 * @param task 발행할 수집 작업
	 * @return 메시지 ID와 우선순위가 포함된 RabbitMQ 메시지
	 * @throws CollectionTaskPublishException JSON 직렬화에 실패한 경우
	 */
	private Message createMessage(CollectionTaskMessage task) {
		try {
			byte[] body = objectMapper.writeValueAsBytes(task);
			return MessageBuilder.withBody(body)
					.setContentType(MessageProperties.CONTENT_TYPE_JSON)
					.setContentEncoding(StandardCharsets.UTF_8.name())
					.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
					.setMessageId(task.taskId())
					.setPriority(task.priority())
					.setTimestamp(java.util.Date.from(task.requestedAt().toInstant()))
					.build();
		} catch (Exception exception) {
			throw new CollectionTaskPublishException("CollectionTask JSON을 생성하지 못했습니다.", exception);
		}
	}
}
