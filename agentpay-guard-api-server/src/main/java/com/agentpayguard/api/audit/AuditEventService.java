package com.agentpayguard.api.audit;

import com.agentpayguard.api.anchor.AuditAnchorClient;
import com.agentpayguard.api.anchor.AnchorResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditEventService {

    private final EventHashService eventHashService;
    private final AuditAnchorClient auditAnchorClient;

    public AuditEventService(EventHashService eventHashService, AuditAnchorClient auditAnchorClient) {
        this.eventHashService = eventHashService;
        this.auditAnchorClient = auditAnchorClient;
    }

    public AuditEventResult record(String eventType, UUID subjectId, Map<String, Object> payload) {
        String canonicalJson = eventHashService.toCanonicalJson(payload);
        String eventHash = eventHashService.sha256Hex(canonicalJson);
        AnchorResult anchorResult = auditAnchorClient.anchor(eventHash);

        return new AuditEventResult(eventType, subjectId, canonicalJson, eventHash, anchorResult.verifyStatus());
    }
}
