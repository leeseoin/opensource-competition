package com.purchaseresearch.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * purchase-research-agent(FastAPI) /api/v1/search 응답의 items[] 항목.
 * FastAPI 쪽은 snake_case, 가격은 "19,000원" 같은 문자열이라 그대로 옮겨 받는다.
 * reviews/options는 detail_limit으로 상세를 붙인 상위 상품에만 존재한다(그 외엔 null).
 */
public record FastApiProductItem(
		@JsonProperty("source_product_id") String sourceProductId,
		String title,
		String brand,
		String price,
		@JsonProperty("price_original") String priceOriginal,
		@JsonProperty("discount_percent") Integer discountPercent,
		@JsonProperty("image_url") String imageUrl,
		@JsonProperty("style_code") String styleCode,
		String link,
		String site,
		@JsonProperty("review_count") Integer reviewCount,
		List<FastApiReviewItem> reviews,
		FastApiOptionsItem options
) {
}
