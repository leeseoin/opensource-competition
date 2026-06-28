package com.agentpayguard.api.policy;

public record PolicyDecisionResult(
        PolicyDecision decision,
        String reasonCode,
        String reasonMessage,
        String policyVersion
) {
}
