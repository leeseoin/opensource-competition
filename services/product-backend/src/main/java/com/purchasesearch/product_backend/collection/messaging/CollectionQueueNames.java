package com.purchasesearch.product_backend.collection.messaging;

/**
 * CollectionQueueNames는 Go Collector와 공유하는 RabbitMQ exchange, Queue와 routing key 이름을 제공한다.
 */
public final class CollectionQueueNames {

	public static final String COLLECTION_EXCHANGE = "purchase-research.collection.v1";
	public static final String DEAD_LETTER_EXCHANGE = "purchase-research.collection.dlx.v1";
	public static final String RESULT_QUEUE = "purchase-research.collection.result.v1";
	public static final String RESULT_DEAD_LETTER_QUEUE = "purchase-research.collection.result.dlq.v1";
	public static final String RESULT_ROUTING_KEY = "collection.result";
	public static final String RESULT_DEAD_LETTER_ROUTING_KEY = "collection.result.dead";

	/**
	 * 상수 전용 class의 외부 생성을 막는다.
	 */
	private CollectionQueueNames() {
	}
}
