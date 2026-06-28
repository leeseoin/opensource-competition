package com.agentpayguard.api.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record QuoteRequest(
        @NotBlank String merchant,
        @NotBlank String resource,
        String category,
        @Positive BigDecimal amount,
        String currency
) {
}
