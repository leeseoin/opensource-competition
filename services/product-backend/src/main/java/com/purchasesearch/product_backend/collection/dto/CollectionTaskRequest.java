package com.purchasesearch.product_backend.collection.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CollectionTaskRequest는 관리 화면이나 Swagger에서 받는 판매처 검색 수집 조건이다.
 *
 * @param merchant 판매처 식별자
 * @param query 검색어
 * @param page 검색 페이지이며 현재는 1만 지원
 * @param limit 수집할 상품 최대 개수
 * @param locale 검색 지역 형식
 * @param currency 통화 코드
 * @param priority RabbitMQ 작업 우선순위
 * @param maxAttempts 최초 실행을 포함한 최대 시도 횟수
 * @param filters 가격, 카테고리, 사이즈, 색상 및 재고 필터
 */
public record CollectionTaskRequest(
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
		String merchant,
		@NotBlank
		@Size(max = 200)
		String query,
		@Min(1)
		Integer page,
		@Min(1)
		@Max(50)
		Integer limit,
		@Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$")
		String locale,
		@Pattern(regexp = "^[A-Z]{3}$")
		String currency,
		@Min(0)
		@Max(100)
		Integer priority,
		@Min(1)
		@Max(5)
		Integer maxAttempts,
		@Valid
		SearchFilters filters) {

	/**
	 * SearchFilters는 판매처 검색에 전달할 공통 필터 입력이다.
	 *
	 * @param priceMin 최소 가격
	 * @param priceMax 최대 가격
	 * @param categories 카테고리 조건
	 * @param sizes 사이즈 조건
	 * @param colors 색상 조건
	 * @param inStockOnly 재고 보유 상품만 요청할지 여부
	 * @param attributes 판매처 확장 검색 속성
	 */
	public record SearchFilters(
			@Min(0)
			Integer priceMin,
			@Min(0)
			Integer priceMax,
			@Size(max = 50)
			List<@NotBlank @Size(max = 100) String> categories,
			@Size(max = 50)
			List<@NotBlank @Size(max = 100) String> sizes,
			@Size(max = 50)
			List<@NotBlank @Size(max = 100) String> colors,
			Boolean inStockOnly,
			@Size(max = 30)
			Map<@NotBlank @Size(max = 100) String, Object> attributes) {
	}
}
