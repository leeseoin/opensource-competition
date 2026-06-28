package com.agentpayguard.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequestRequest(
        @NotNull UUID intentId,
        @NotNull UUID agentId,
        String quoteId,
        @NotBlank String merchant,
        @NotBlank String resource,
        String category,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        String reason
) {
}
