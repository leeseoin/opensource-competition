package com.purchasesearch.product_backend.product.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param productIds 비교할 서로 다른 판매처 상품 ID 2개 이상 5개 이하 */
public record ProductComparisonRequest(
		@NotNull @Size(min = 2, max = 5) List<@NotNull Long> productIds) {
}
