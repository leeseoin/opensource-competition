package com.purchasesearch.product_backend.research.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PurchaseCondition은 AI가 해석하고 사용자가 확인할 구조화된 구매 조건이다.
 *
 * @param productType 검색할 상품 종류와 필수/선호 강도
 * @param usage 상품을 사용할 상황
 * @param price 최소/최대 가격과 통화
 * @param colors 선호 색상
 * @param sizes 필요한 사이즈
 * @param requirements 그 밖의 필수 조건
 * @param merchant 선택 판매처
 * @param missingConditions AI가 추가 질문이 필요하다고 판단한 조건
 * @param assumptions AI가 사용자 발화에서 추론한 내용
 * @param confidence AI 해석 신뢰도
 * @param requiresConfirmation 사용자 확인이 필요함을 나타내는 고정값
 */
public record PurchaseCondition(
		@NotNull @Valid PrioritizedText productType,
		@NotNull @Size(max = 20) List<@NotNull @Valid PrioritizedShortText> usage,
		@NotNull @Valid PriceCondition price,
		@NotNull @Size(max = 20) List<@NotNull @Valid PrioritizedShortText> colors,
		@NotNull @Size(max = 20) List<@NotNull @Valid PrioritizedShortText> sizes,
		@NotNull @Size(max = 30) List<@NotNull @Valid PrioritizedText> requirements,
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$") @Size(max = 64) String merchant,
		@NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> missingConditions,
		@NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> assumptions,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
		@NotNull Boolean requiresConfirmation) {

	/**
	 * PriceCondition은 사용자가 허용한 가격 범위와 통화를 표현한다.
	 *
	 * @param min 최소 가격
	 * @param max 최대 가격
	 * @param currency ISO 4217 통화 코드
	 * @param priority 가격 조건의 필수/선호 강도
	 */
	public record PriceCondition(
			@PositiveOrZero Long min,
			@PositiveOrZero Long max,
			@NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
			@NotNull ConditionPriority priority) {
	}

	/**
	 * PrioritizedText는 조건 값과 사용자가 확인할 필수/선호 강도를 함께 표현한다.
	 *
	 * @param value 조건 문자열
	 * @param priority 조건의 필수/선호 강도
	 */
	public record PrioritizedText(
			@NotBlank @Size(max = 200) String value,
			@NotNull ConditionPriority priority) {
	}

	/**
	 * PrioritizedShortText는 짧은 조건 값과 사용자가 확인할 필수/선호 강도를 함께 표현한다.
	 *
	 * @param value 최대 100자의 조건 문자열
	 * @param priority 조건의 필수/선호 강도
	 */
	public record PrioritizedShortText(
			@NotBlank @Size(max = 100) String value,
			@NotNull ConditionPriority priority) {
	}

	/** ConditionPriority는 후보를 제외하는 필수 조건과 점수에 반영할 선호 조건을 구분한다. */
	public enum ConditionPriority {
		required,
		preferred
	}
}
