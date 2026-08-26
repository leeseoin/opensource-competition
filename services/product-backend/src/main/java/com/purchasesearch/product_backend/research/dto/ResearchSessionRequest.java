package com.purchasesearch.product_backend.research.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ResearchSessionRequest는 AI 실행 결과를 확인 전 조사 세션으로 저장한다.
 *
 * @param question 사용자 원문 질문
 * @param runtime 허용된 AI 실행 환경
 * @param pluginId 적용한 Plugin 식별자
 * @param conditions AI가 구조화한 구매 조건
 */
public record ResearchSessionRequest(
		@NotBlank @Size(max = 1000) String question,
		@NotBlank @Pattern(regexp = "^(codex|claude)$") String runtime,
		@NotBlank @Pattern(regexp = "^purchase-research-agent$") String pluginId,
		@NotNull @Valid PurchaseCondition conditions) {
}
