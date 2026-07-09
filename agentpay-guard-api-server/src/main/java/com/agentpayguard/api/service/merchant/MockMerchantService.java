package com.agentpayguard.api.service.merchant;

import com.agentpayguard.api.dto.merchant.QuoteRequest;
import com.agentpayguard.api.dto.merchant.QuoteResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MockMerchantService {

    public QuoteResponse quote(QuoteRequest request) {
        BigDecimal amount = request.amount() == null ? new BigDecimal("10.00") : request.amount();

        return new QuoteResponse(
                "quote_" + UUID.randomUUID(),
                request.merchant(),
                request.resource(),
                request.category(),
                amount,
                request.currency() == null ? "USD" : request.currency(),
                "mock quote for PoC"
        );
    }
}
