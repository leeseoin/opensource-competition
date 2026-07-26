package com.purchaseresearch.backend.dto;

import java.util.List;

public record ProductBatchResponse(
		int totalCount,
		int successCount,
		int failCount,
		List<FailureItem> failures
) {
	public record FailureItem(String sourceProductId, String reason) {
	}
}
