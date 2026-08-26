package com.purchasesearch.product_backend.collection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * BulkCollectionTaskRequest는 같은 판매처와 검색어를 연속된 여러 페이지 작업으로 등록하는 조건이다.
 *
 * @param merchant 판매처 식별자
 * @param query 검색어
 * @param startPage 수집을 시작할 페이지
 * @param pageCount 연속해서 수집할 페이지 수
 * @param limit 페이지당 상품 최대 개수
 * @param locale 검색 지역 형식
 * @param currency 통화 코드
 * @param priority RabbitMQ 작업 우선순위
 * @param maxAttempts 최초 실행을 포함한 최대 시도 횟수
 * @param filters 가격, 카테고리, 사이즈, 색상 및 재고 필터
 */
public record BulkCollectionTaskRequest(
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
		String merchant,
		@NotBlank
		@Size(max = 200)
		String query,
		@Min(1)
		@Max(200)
		Integer startPage,
		@Min(1)
		@Max(200)
		Integer pageCount,
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
		CollectionTaskRequest.SearchFilters filters) {

	/**
	 * 지정한 페이지를 기존 단일 페이지 요청 형식으로 변환한다.
	 *
	 * @param page RabbitMQ 작업에 넣을 페이지
	 * @return 공통 발행 로직이 처리할 단일 페이지 요청
	 */
	public CollectionTaskRequest toPageRequest(int page) {
		return new CollectionTaskRequest(
				merchant,
				query,
				page,
				limit,
				locale,
				currency,
				priority,
				maxAttempts,
				filters);
	}
}
