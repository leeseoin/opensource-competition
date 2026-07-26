package com.purchaseresearch.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ProductPayload(
		@NotBlank String sourceProductId,
		@NotBlank String title,
		String brand,
		Integer price,
		Integer priceOriginal,
		Integer discountPercent,
		String imageUrl,
		String styleCode,
		@NotBlank String link,
		Integer reviewCount,
		OptionsPayload options,
		List<ReviewPayload> reviews
) {
}
