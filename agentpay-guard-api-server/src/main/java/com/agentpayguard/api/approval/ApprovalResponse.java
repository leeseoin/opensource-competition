package com.agentpayguard.api.approval;

import java.util.UUID;

public record ApprovalResponse(
        UUID paymentRequestId,
        String decision,
        String message
) {
}
