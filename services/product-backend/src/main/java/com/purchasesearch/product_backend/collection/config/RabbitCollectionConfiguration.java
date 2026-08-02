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
 * RabbitCollectionConfiguration은 Spring과 Go Worker가 공유하는 결과 Queue와 DLQ topology를 선언한다.
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
