package com.purchasesearch.product_backend.collection.exception;

/**
 * InvalidCollectionResultMessageException은 Queue 결과 JSON이나 상태 조합이 계약을 위반했음을 나타낸다.
 */
public class InvalidCollectionResultMessageException extends RuntimeException {

	/**
	 * 검증 실패 원인을 포함한 예외를 생성한다.
	 *
	 * @param message 운영 로그와 DLQ 조사에 사용할 안전한 오류 설명
	 */
	public InvalidCollectionResultMessageException(String message) {
		super(message);
	}

	/**
	 * JSON 해석 실패 원인을 보존한 예외를 생성한다.
	 *
	 * @param message 운영 로그에 사용할 안전한 오류 설명
	 * @param cause 원본 JSON 해석 예외
	 */
	public InvalidCollectionResultMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
