package com.agentpayguard.api.payment;

import com.agentpayguard.api.audit.AuditEventResult;
import com.agentpayguard.api.policy.PolicyDecisionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequestResponse(
        UUID id,
        UUID intentId,
        UUID agentId,
        String merchant,
        String resource,
        String category,
        BigDecimal amount,
        String currency,
        PaymentRequestStatus status,
        PolicyDecisionResult policyDecision,
        AuditEventResult auditEvent,
        Instant createdAt
) {
}
