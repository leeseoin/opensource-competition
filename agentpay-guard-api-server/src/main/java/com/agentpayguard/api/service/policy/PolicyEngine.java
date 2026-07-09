package com.agentpayguard.api.service.policy;

import com.agentpayguard.api.dto.payment.CreatePaymentRequestRequest;
import com.agentpayguard.api.dto.policy.PolicyDecisionResult;

public interface PolicyEngine {

    PolicyDecisionResult evaluate(CreatePaymentRequestRequest request);
}
