package com.agentpayguard.api.merchant;

import java.math.BigDecimal;

public record QuoteResponse(
        String quoteId,
        String merchant,
        String resource,
        String category,
        BigDecimal amount,
        String currency,
        String quoteMessage
) {
}
