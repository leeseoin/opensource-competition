package com.purchasesearch.product_backend.agentrun.entity;

/** AgentRunStatus는 구매 조사 실행의 검색, 수집, 재검증과 종료 단계를 구분한다. */
public enum AgentRunStatus {
	SEARCHING,
	COLLECTING,
	READY,
	VERIFYING,
	COMPLETED,
	NO_RESULTS,
	FAILED;

	/** @return 더 진행할 필요가 없는 최종 상태인지 여부 */
	public boolean isTerminal() {
		return this == COMPLETED || this == NO_RESULTS || this == FAILED;
	}
}
