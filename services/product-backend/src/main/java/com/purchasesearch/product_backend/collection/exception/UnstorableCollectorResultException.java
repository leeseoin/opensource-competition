package com.purchasesearch.product_backend.collection.exception;

/**
 * UnstorableCollectorResultException은 형식은 올바르지만 저장 대상이 아닌 Collector
 * 상태를 Product Backend가 거절할 때 발생한다.
 */
public class UnstorableCollectorResultException extends RuntimeException {

	/**
	 * 저장 거절 사유를 포함한 예외를 생성한다.
	 *
	 * @param message 사람이 확인할 저장 거절 사유
	 */
	public UnstorableCollectorResultException(String message) {
		super(message);
	}
}
