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
 * @param attributes 상품군과 무관하게 확장 가능한 범용 속성 조건
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
		@NotNull @Size(max = 50) List<@NotNull @Valid AttributeCondition> attributes,
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$") @Size(max = 64) String merchant,
		@NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> missingConditions,
		@NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> assumptions,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
		@NotNull Boolean requiresConfirmation) {

	/** 누락된 선택형 범용 속성을 빈 목록으로 보정해 기존 JSON 세션을 계속 읽는다. */
	public PurchaseCondition {
		attributes = attributes == null ? List.of() : List.copyOf(attributes);
	}

	/** 기존 v1 생성 코드를 범용 속성 없는 호환 입력으로 유지한다. */
	public PurchaseCondition(
			PrioritizedText productType,
			List<PrioritizedShortText> usage,
			PriceCondition price,
			List<PrioritizedShortText> colors,
			List<PrioritizedShortText> sizes,
			List<PrioritizedText> requirements,
			String merchant,
			List<String> missingConditions,
			List<String> assumptions,
			Double confidence,
			Boolean requiresConfirmation) {
		this(productType, usage, price, colors, sizes, requirements, List.of(), merchant,
				missingConditions, assumptions, confidence, requiresConfirmation);
	}

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
			@NotNull ConditionPriority priority,
			@Size(max = 200) String normalizedValue,
			@Pattern(regexp = "^[a-z][a-z0-9-]*:[a-z0-9][a-z0-9._-]*$") @Size(max = 160) String canonicalId,
			@DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
			DerivedBy derivedBy,
			Boolean requiresConfirmation) {

		/** 기존 value/priority 입력을 정규화 전 조건으로 만든다. */
		public PrioritizedText(String value, ConditionPriority priority) {
			this(value, priority, null, null, null, null, false);
		}

		/** @return 정규화 값이 있으면 해당 값을, 없으면 사용자 원문 값을 반환한다. */
		public String effectiveValue() {
			return normalizedValue == null || normalizedValue.isBlank() ? value : normalizedValue;
		}
	}

	/**
	 * PrioritizedShortText는 짧은 조건 값과 사용자가 확인할 필수/선호 강도를 함께 표현한다.
	 *
	 * @param value 최대 100자의 조건 문자열
	 * @param priority 조건의 필수/선호 강도
	 */
	public record PrioritizedShortText(
			@NotBlank @Size(max = 100) String value,
			@NotNull ConditionPriority priority,
			@Size(max = 100) String normalizedValue,
			@Pattern(regexp = "^[a-z][a-z0-9-]*:[a-z0-9][a-z0-9._-]*$") @Size(max = 160) String canonicalId,
			@DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
			DerivedBy derivedBy,
			Boolean requiresConfirmation) {

		/** 기존 value/priority 입력을 정규화 전 조건으로 만든다. */
		public PrioritizedShortText(String value, ConditionPriority priority) {
			this(value, priority, null, null, null, null, false);
		}

		/** @return 정규화 값이 있으면 해당 값을, 없으면 사용자 원문 값을 반환한다. */
		public String effectiveValue() {
			return normalizedValue == null || normalizedValue.isBlank() ? value : normalizedValue;
		}
	}

	/**
	 * AttributeCondition은 신발 사이즈부터 전자제품 용량까지 같은 계약으로 표현한다.
	 *
	 * @param key 범용 속성 key
	 * @param value 사용자 또는 AI가 제공한 원문 값
	 * @param priority 필수/선호 강도
	 * @param normalizedValue 검색에 사용할 표준 값
	 * @param canonicalId namespace를 포함한 표준 식별자
	 * @param confidence 정규화 신뢰도
	 * @param derivedBy 정규화 근거 종류
	 * @param requiresConfirmation 사용자 재확인이 필요한지 여부
	 */
	public record AttributeCondition(
			@NotBlank @Pattern(regexp = "^[a-z][a-z0-9._-]*$") @Size(max = 100) String key,
			@NotBlank @Size(max = 200) String value,
			@NotNull ConditionPriority priority,
			@Size(max = 200) String normalizedValue,
			@Pattern(regexp = "^[a-z][a-z0-9-]*:[a-z0-9][a-z0-9._-]*$") @Size(max = 160) String canonicalId,
			@DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
			DerivedBy derivedBy,
			Boolean requiresConfirmation) {

		/** @return 정규화 값이 있으면 해당 값을, 없으면 원문 값을 반환한다. */
		public String effectiveValue() {
			return normalizedValue == null || normalizedValue.isBlank() ? value : normalizedValue;
		}
	}

	/** DerivedBy는 조건값이 생성 또는 정규화된 근거를 구분한다. */
	public enum DerivedBy {
		original,
		rule,
		dictionary,
		wiki,
		fuzzy,
		llm
	}

	/** ConditionPriority는 후보를 제외하는 필수 조건과 점수에 반영할 선호 조건을 구분한다. */
	public enum ConditionPriority {
		required,
		preferred
	}
}
