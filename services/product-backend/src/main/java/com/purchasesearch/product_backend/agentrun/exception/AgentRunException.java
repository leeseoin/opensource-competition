package com.purchasesearch.product_backend.agentrun.exception;

import org.springframework.http.HttpStatus;

/** AgentRunException은 실행 없음과 허용되지 않은 상태 전이를 HTTP 상태와 함께 전달한다. */
public class AgentRunException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	/** @param status HTTP 상태 @param code 안정적인 오류 코드 @param message 안전한 오류 설명 */
	public AgentRunException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	/** @return 응답에 사용할 HTTP 상태 */
	public HttpStatus getStatus() {
		return status;
	}

	/** @return 호출자가 분기할 안정적인 오류 코드 */
	public String getCode() {
		return code;
	}
}
