package com.purchaseresearch.backend.dto;

import java.util.List;

public record FastApiOptionsItem(
		List<String> colors,
		List<String> sizes
) {
}
