package com.agentpayguard.api.service.guard;

import com.agentpayguard.api.dto.audit.AuditEventResult;
import com.agentpayguard.api.dto.guard.GuardValidateRequest;
import com.agentpayguard.api.dto.guard.GuardValidateResponse;
import com.agentpayguard.api.dto.payment.CreatePaymentRequestRequest;
import com.agentpayguard.api.dto.payment.PaymentRequestResponse;
import com.agentpayguard.api.dto.policy.PolicyDecisionResult;
import com.agentpayguard.api.service.payment.PaymentService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Agent용 v1 Guard API를 기존 Payment Request 평가 흐름에 연결하는 adapter service이다.
 * sample-agent가 사용하는 단순 요청 형식을 내부 payment request 모델로 변환한다.
 */
@Service
public class GuardValidateService {

    private static final String DEFAULT_MERCHANT = "anthropic";
    private static final String DEFAULT_RESOURCE = "claude-api";
    private static final String DEFAULT_CATEGORY = "llm";
    private static final String DEFAULT_CURRENCY = "USD";

    private final PaymentService paymentService;

    public GuardValidateService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Agent의 예상 비용 검증 요청을 payment request로 생성하고 정책/audit/anchor 결과를 Agent 친화 응답으로 축약한다.
     */
    public GuardValidateResponse validate(GuardValidateRequest request) {
        PaymentRequestResponse paymentRequest = paymentService.create(new CreatePaymentRequestRequest(
                stableUuid("intent:" + request.intent()),
                stableUuid("agent:" + request.agentId()),
                null,
                DEFAULT_MERCHANT,
                DEFAULT_RESOURCE,
                DEFAULT_CATEGORY,
                request.estimatedCost(),
                DEFAULT_CURRENCY,
                request.intent()
        ));

        PolicyDecisionResult policyDecision = paymentRequest.policyDecision();
        AuditEventResult auditEvent = paymentRequest.auditEvent();

        return new GuardValidateResponse(
                policyDecision.decision().name(),
                policyDecision.reasonCode(),
                policyDecision.reasonMessage(),
                policyDecision.policyVersion(),
                paymentRequest.id(),
                auditEvent.eventHash(),
                auditEvent.anchorStatus(),
                auditEvent.anchor().chainId(),
                auditEvent.anchor().contractAddress(),
                auditEvent.anchor().txHash()
        );
    }

    /**
     * 문자열 agentId/intent를 현재 UUID 기반 내부 모델에 안정적으로 매핑한다.
     */
    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
