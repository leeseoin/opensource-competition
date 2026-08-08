package com.purchasesearch.product_backend.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ProductCandidateRequest는 사용자 질문과 DB 검색에 사용할 명시적 검색어를 분리해 전달한다.
 *
 * @param question 사용자가 입력한 원본 구매 질문
 * @param query PostgreSQL 상품 검색에 사용할 검색어
 * @param merchant 선택 판매처 식별자
	 * @param limit 최대 후보 상품군 수, 생략하면 5개이며 5개를 초과할 수 없음
 */
public record ProductCandidateRequest(
		@NotBlank
		@Size(max = 1000)
		String question,
		@NotBlank
		@Size(max = 200)
		String query,
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
		@Size(max = 64)
		String merchant,
		@Min(1)
		@Max(5)
		Integer limit) {
}
