package com.purchasesearch.product_backend.agentrun.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param sessionId 사용자가 조건을 확인한 조사 세션 ID
 * @param merchants 판매처 조건이 없을 때 조회할 판매처 목록
 */
public record StartAgentRunRequest(
		@NotNull UUID sessionId,
		@Size(max = 4) List<@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$") @Size(max = 64) String> merchants) {

	/** null 목록을 빈 목록으로 바꿔 기본 판매처 정책을 적용한다. */
	public StartAgentRunRequest {
		merchants = merchants == null ? List.of() : List.copyOf(merchants);
	}
}
