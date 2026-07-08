package com.agentpayguard.api.dto.policy;

public record PolicyDecisionResult(
        PolicyDecision decision,
        String reasonCode,
        String reasonMessage,
        String policyVersion
) {
}
