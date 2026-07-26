package com.purchaseresearch.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiSearchResponse(
		String status,
		String site,
		String keyword,
		@JsonProperty("total_found") Integer totalFound,
		Integer returned,
		List<FastApiProductItem> items
) {
}
