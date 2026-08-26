package com.purchasesearch.product_backend.research.exception;

/** ResearchSessionException은 조사 세션 없음 또는 미확정 검색 요청을 표현한다. */
public class ResearchSessionException extends RuntimeException {

	/**
	 * 클라이언트가 해결할 수 있는 조사 세션 오류를 생성한다.
	 *
	 * @param message 사용자에게 전달할 안전한 오류 설명
	 */
	public ResearchSessionException(String message) {
		super(message);
	}
}
