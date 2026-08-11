package com.purchasesearch.product_backend.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param merchant 수집할 판매처
 * @param query 표준 상품 검색어
 * @param force 최신 데이터가 있어도 강제로 수집할지 여부
 */
public record CollectionRefreshRequest(
		@NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$") @Size(max = 64) String merchant,
		@NotBlank @Size(max = 200) String query,
		Boolean force) {
}
