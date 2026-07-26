package com.purchaseresearch.backend.dto;

import java.util.List;

public record ProductSearchResponse(
		int totalCount,
		List<ProductSummary> products
) {
	public record ProductSummary(
			String title,
			String brand,
			Integer price,
			Integer discountPercent,
			String imageUrl,
			String link
	) {
	}
}
