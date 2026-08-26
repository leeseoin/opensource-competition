package com.purchasesearch.product_backend.product.embedding;

/** EmbeddingProviderException은 선택적 embedding provider 실패를 전문 검색 fallback과 구분한다. */
public class EmbeddingProviderException extends RuntimeException {

	/**
	 * 안전한 embedding provider 오류를 생성한다.
	 *
	 * @param message provider 응답 원문과 인증정보를 포함하지 않은 설명
	 * @param cause 원래 예외
	 */
	public EmbeddingProviderException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * 원인 예외가 없는 model 계약 오류를 생성한다.
	 *
	 * @param message 안전한 계약 오류 설명
	 */
	public EmbeddingProviderException(String message) {
		super(message);
	}
}
