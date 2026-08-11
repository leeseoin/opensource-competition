package com.purchasesearch.product_backend.product.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * ProductComparisonResponse는 후보 상세와 주요 비교축별 값을 한 응답으로 제공한다.
 *
 * @param comparedAt 비교 생성 시각
 * @param products 비교 상품 상세
 * @param fields 가격/재고/판매처/카테고리/수집 시각 비교축
 */
public record ProductComparisonResponse(
		OffsetDateTime comparedAt,
		List<ProductDetailResponse> products,
		List<ComparisonField> fields) {

	/** @param key 비교축 key @param values 상품 ID별 값 @param allEqual 모든 값이 같은지 여부 */
	public record ComparisonField(String key, Map<String, Object> values, boolean allEqual) {
	}
}
