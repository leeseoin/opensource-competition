package com.purchaseresearch.backend.dto;

import java.util.List;

public record OptionsPayload(
		List<String> colors,
		List<String> sizes
) {
}
