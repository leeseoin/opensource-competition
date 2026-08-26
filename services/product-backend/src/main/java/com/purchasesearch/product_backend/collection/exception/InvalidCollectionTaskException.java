package com.purchasesearch.product_backend.collection.exception;

/**
 * InvalidCollectionTaskException은 필드 단위 검증을 통과했지만 의미상 발행할 수 없는 검색 조건을 나타낸다.
 */
public class InvalidCollectionTaskException extends RuntimeException {

	/**
	 * 잘못된 작업 조건 설명으로 예외를 생성한다.
	 *
	 * @param message 사용자에게 반환할 검증 실패 설명
	 */
	public InvalidCollectionTaskException(String message) {
		super(message);
	}
}
