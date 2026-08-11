package com.purchasesearch.product_backend.dashboard.exception;

/**
 * InvalidDashboardWindowException은 since가 until보다 늦는 등 집계 시간 창이 잘못된 경우를 나타낸다.
 */
public class InvalidDashboardWindowException extends RuntimeException {

	/**
	 * 잘못된 시간 창 설명으로 예외를 생성한다.
	 *
	 * @param message 사용자에게 반환할 검증 실패 설명
	 */
	public InvalidDashboardWindowException(String message) {
		super(message);
	}
}
