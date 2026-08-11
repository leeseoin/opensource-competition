package com.purchasesearch.product_backend.collection.exception;

/**
 * CollectionTaskPublishException은 RabbitMQ가 수집 작업을 확인하지 못한 일시적 발행 실패를 나타낸다.
 */
public class CollectionTaskPublishException extends RuntimeException {

	/**
	 * 발행 실패 설명과 원인으로 예외를 생성한다.
	 *
	 * @param message 안전한 발행 실패 설명
	 * @param cause RabbitMQ 또는 대기 실패 원인
	 */
	public CollectionTaskPublishException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * broker NACK 또는 반환 메시지의 설명으로 예외를 생성한다.
	 *
	 * @param message 안전한 발행 실패 설명
	 */
	public CollectionTaskPublishException(String message) {
		super(message);
	}
}
