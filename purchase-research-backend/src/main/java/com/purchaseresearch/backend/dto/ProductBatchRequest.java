package com.purchaseresearch.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ProductBatchRequest(
		@NotBlank String site,
		String keyword,
		OffsetDateTime collectedAt,
		@NotEmpty @Valid List<ProductPayload> products
) {
}
