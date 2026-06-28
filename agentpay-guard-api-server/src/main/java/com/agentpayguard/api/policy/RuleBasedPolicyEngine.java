package com.agentpayguard.api.policy;

import com.agentpayguard.api.payment.CreatePaymentRequestRequest;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedPolicyEngine implements PolicyEngine {

    private static final String POLICY_VERSION = "poc-rule-v1";
    private static final Set<String> BLOCKED_MERCHANTS = Set.of("blocked-merchant", "unknown-risky-api");
    private static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("50.00");
    private static final BigDecimal HARD_LIMIT = new BigDecimal("100.00");

    @Override
    public PolicyDecisionResult evaluate(CreatePaymentRequestRequest request) {
        String merchant = request.merchant().toLowerCase();

        if (BLOCKED_MERCHANTS.contains(merchant)) {
            return new PolicyDecisionResult(
                    PolicyDecision.DENY,
                    "MERCHANT_BLOCKED",
                    "Merchant is blocked by PoC policy.",
                    POLICY_VERSION
            );
        }

        if (request.amount().compareTo(HARD_LIMIT) > 0) {
            return new PolicyDecisionResult(
                    PolicyDecision.DENY,
                    "AMOUNT_OVER_HARD_LIMIT",
                    "Amount is over the PoC hard limit.",
                    POLICY_VERSION
            );
        }

        if (request.amount().compareTo(APPROVAL_THRESHOLD) > 0) {
            return new PolicyDecisionResult(
                    PolicyDecision.REQUIRE_APPROVAL,
                    "APPROVAL_REQUIRED",
                    "Amount requires user approval.",
                    POLICY_VERSION
            );
        }

        return new PolicyDecisionResult(
                PolicyDecision.ALLOW,
                "RULE_ALLOW",
                "Request is allowed by PoC rule policy.",
                POLICY_VERSION
        );
    }
}
