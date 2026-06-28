package com.agentpayguard.api.policy;

import com.agentpayguard.api.payment.CreatePaymentRequestRequest;

public interface PolicyEngine {

    PolicyDecisionResult evaluate(CreatePaymentRequestRequest request);
}
