package com.agentpayguard.api.audit;

import java.util.UUID;

public record AuditEventResult(
        String eventType,
        UUID subjectId,
        String canonicalJson,
        String eventHash,
        String anchorStatus
) {
}
