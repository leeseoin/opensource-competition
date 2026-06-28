package com.agentpayguard.api.payment;

import com.agentpayguard.api.audit.AuditEventResult;
import com.agentpayguard.api.audit.AuditEventService;
import com.agentpayguard.api.policy.PolicyDecision;
import com.agentpayguard.api.policy.PolicyDecisionResult;
import com.agentpayguard.api.policy.PolicyEngine;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PolicyEngine policyEngine;
    private final AuditEventService auditEventService;
    private final Map<UUID, PaymentRequestResponse> paymentRequests = new ConcurrentHashMap<>();

    public PaymentService(PolicyEngine policyEngine, AuditEventService auditEventService) {
        this.policyEngine = policyEngine;
        this.auditEventService = auditEventService;
    }

    public PaymentRequestResponse create(CreatePaymentRequestRequest request) {
        UUID id = UUID.randomUUID();
        PolicyDecisionResult policyDecision = policyEngine.evaluate(request);
        PaymentRequestStatus status = toStatus(policyDecision.decision());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("agentId", request.agentId());
        auditPayload.put("amount", request.amount());
        auditPayload.put("currency", request.currency());
        auditPayload.put("decision", policyDecision.decision());
        auditPayload.put("intentId", request.intentId());
        auditPayload.put("merchant", request.merchant());
        auditPayload.put("paymentRequestId", id);
        auditPayload.put("resource", request.resource());

        AuditEventResult auditEvent = auditEventService.record("PAYMENT_REQUEST_EVALUATED", id, auditPayload);

        PaymentRequestResponse response = new PaymentRequestResponse(
                id,
                request.intentId(),
                request.agentId(),
                request.merchant(),
                request.resource(),
                request.category(),
                request.amount(),
                request.currency(),
                status,
                policyDecision,
                auditEvent,
                Instant.now()
        );

        paymentRequests.put(id, response);
        return response;
    }

    public PaymentRequestResponse get(UUID id) {
        PaymentRequestResponse response = paymentRequests.get(id);
        if (response == null) {
            throw new IllegalArgumentException("Payment request not found: " + id);
        }
        return response;
    }

    private PaymentRequestStatus toStatus(PolicyDecision decision) {
        return switch (decision) {
            case ALLOW -> PaymentRequestStatus.ALLOWED;
            case DENY -> PaymentRequestStatus.DENIED;
            case REQUIRE_APPROVAL -> PaymentRequestStatus.WAITING_APPROVAL;
        };
    }
}
