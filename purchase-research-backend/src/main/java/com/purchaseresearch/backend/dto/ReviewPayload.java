package com.purchaseresearch.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record ReviewPayload(
		@NotBlank String reviewSourceId,
		String content,
		BigDecimal score,
		LocalDate reviewDate,
		String size
) {
}
