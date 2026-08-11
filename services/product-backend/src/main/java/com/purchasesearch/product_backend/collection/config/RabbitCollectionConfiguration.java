package com.purchasesearch.product_backend.collection.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;

/**
 * RabbitCollectionConfiguration은 Spring과 Go Worker가 공유하는 검색/재시도/결과 Queue와 DLQ topology를 선언한다.
 */
@EnableRabbit
@Configuration(proxyBeanMethods = false)
public class RabbitCollectionConfiguration {

	/**
	 * Collector 작업과 결과가 사용하는 durable direct exchange를 생성한다.
	 *
	 * @return 수집 exchange
	 */
	@Bean
	DirectExchange collectionExchange() {
		return new DirectExchange(CollectionQueueNames.COLLECTION_EXCHANGE, true, false);
	}

	/**
	 * 처리할 수 없는 결과를 격리하는 durable direct exchange를 생성한다.
	 *
	 * @return dead-letter exchange
	 */
	@Bean
	DirectExchange collectionDeadLetterExchange() {
		return new DirectExchange(CollectionQueueNames.DEAD_LETTER_EXCHANGE, true, false);
	}

	/**
	 * 검색 작업을 Go Worker에 전달하며 처리 거절 시 검색 DLQ로 이동하는 Queue를 생성한다.
	 *
	 * @return 우선순위를 지원하는 검색 작업 Queue
	 */
	@Bean
	Queue collectionSearchTaskQueue() {
		return QueueBuilder.durable(CollectionQueueNames.SEARCH_TASK_QUEUE)
				.deadLetterExchange(CollectionQueueNames.DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(CollectionQueueNames.SEARCH_DEAD_LETTER_ROUTING_KEY)
				.maxPriority(100)
				.build();
	}

	/**
	 * 일시 실패 검색 작업을 5초 뒤 원래 검색 Queue로 돌려보내는 Queue를 생성한다.
	 *
	 * @return 검색 재시도 Queue
	 */
	@Bean
	Queue collectionSearchRetryQueue() {
		return QueueBuilder.durable(CollectionQueueNames.SEARCH_RETRY_QUEUE)
				.ttl(5000)
				.deadLetterExchange(CollectionQueueNames.COLLECTION_EXCHANGE)
				.deadLetterRoutingKey(CollectionQueueNames.SEARCH_ROUTING_KEY)
				.build();
	}

	/**
	 * 계약 위반이나 재시도 소진 검색 작업을 보관하는 Queue를 생성한다.
	 *
	 * @return 검색 작업 DLQ
	 */
	@Bean
	Queue collectionSearchDeadLetterQueue() {
		return QueueBuilder.durable(CollectionQueueNames.SEARCH_DEAD_LETTER_QUEUE).build();
	}

	/**
	 * Go Worker의 결과를 받으며 reject 시 결과 DLQ로 이동하는 Queue를 생성한다.
	 *
	 * @return Collector 결과 Queue
	 */
	@Bean
	Queue collectionResultQueue() {
		return QueueBuilder.durable(CollectionQueueNames.RESULT_QUEUE)
				.deadLetterExchange(CollectionQueueNames.DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(CollectionQueueNames.RESULT_DEAD_LETTER_ROUTING_KEY)
				.build();
	}

	/**
	 * 계약 위반 또는 저장 실패 결과를 보관하는 Queue를 생성한다.
	 *
	 * @return Collector 결과 DLQ
	 */
	@Bean
	Queue collectionResultDeadLetterQueue() {
		return QueueBuilder.durable(CollectionQueueNames.RESULT_DEAD_LETTER_QUEUE).build();
	}

	/**
	 * 검색 routing key를 Go Worker가 소비하는 작업 Queue에 연결한다.
	 *
	 * @param collectionSearchTaskQueue 검색 작업 Queue
	 * @param collectionExchange 수집 exchange
	 * @return 검색 작업 Queue binding
	 */
	@Bean
	Binding collectionSearchTaskBinding(
			@Qualifier("collectionSearchTaskQueue") Queue collectionSearchTaskQueue,
			@Qualifier("collectionExchange") DirectExchange collectionExchange) {
		return BindingBuilder.bind(collectionSearchTaskQueue)
				.to(collectionExchange)
				.with(CollectionQueueNames.SEARCH_ROUTING_KEY);
	}

	/**
	 * 검색 재시도 routing key를 지연 Queue에 연결한다.
	 *
	 * @param collectionSearchRetryQueue 검색 재시도 Queue
	 * @param collectionExchange 수집 exchange
	 * @return 검색 재시도 Queue binding
	 */
	@Bean
	Binding collectionSearchRetryBinding(
			@Qualifier("collectionSearchRetryQueue") Queue collectionSearchRetryQueue,
			@Qualifier("collectionExchange") DirectExchange collectionExchange) {
		return BindingBuilder.bind(collectionSearchRetryQueue)
				.to(collectionExchange)
				.with(CollectionQueueNames.SEARCH_RETRY_ROUTING_KEY);
	}

	/**
	 * 검색 dead-letter routing key를 검색 DLQ에 연결한다.
	 *
	 * @param collectionSearchDeadLetterQueue 검색 작업 DLQ
	 * @param collectionDeadLetterExchange dead-letter exchange
	 * @return 검색 작업 DLQ binding
	 */
	@Bean
	Binding collectionSearchDeadLetterBinding(
			@Qualifier("collectionSearchDeadLetterQueue") Queue collectionSearchDeadLetterQueue,
			@Qualifier("collectionDeadLetterExchange") DirectExchange collectionDeadLetterExchange) {
		return BindingBuilder.bind(collectionSearchDeadLetterQueue)
				.to(collectionDeadLetterExchange)
				.with(CollectionQueueNames.SEARCH_DEAD_LETTER_ROUTING_KEY);
	}

	/**
	 * 정상 결과 routing key를 결과 Queue에 연결한다.
	 *
	 * @param collectionResultQueue 결과 Queue
	 * @param collectionExchange 수집 exchange
	 * @return 결과 Queue binding
	 */
	@Bean
	Binding collectionResultBinding(
			@Qualifier("collectionResultQueue") Queue collectionResultQueue,
			@Qualifier("collectionExchange") DirectExchange collectionExchange) {
		return BindingBuilder.bind(collectionResultQueue)
				.to(collectionExchange)
				.with(CollectionQueueNames.RESULT_ROUTING_KEY);
	}

	/**
	 * 결과 dead-letter routing key를 결과 DLQ에 연결한다.
	 *
	 * @param collectionResultDeadLetterQueue 결과 DLQ
	 * @param collectionDeadLetterExchange dead-letter exchange
	 * @return 결과 DLQ binding
	 */
	@Bean
	Binding collectionResultDeadLetterBinding(
			@Qualifier("collectionResultDeadLetterQueue") Queue collectionResultDeadLetterQueue,
			@Qualifier("collectionDeadLetterExchange") DirectExchange collectionDeadLetterExchange) {
		return BindingBuilder.bind(collectionResultDeadLetterQueue)
				.to(collectionDeadLetterExchange)
				.with(CollectionQueueNames.RESULT_DEAD_LETTER_ROUTING_KEY);
	}
}
