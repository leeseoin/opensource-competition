package com.agentpayguard.api.dto.approval;

import java.util.UUID;

public record ApprovalResponse(
        UUID paymentRequestId,
        String decision,
        String message
) {
}
