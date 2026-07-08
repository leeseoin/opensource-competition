package com.agentpayguard.api.service.audit;

import com.agentpayguard.api.dto.anchor.AnchorResult;
import com.agentpayguard.api.dto.audit.AuditEventResult;
import com.agentpayguard.api.service.anchor.AuditAnchorClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 도메인 이벤트 payload를 감사 가능한 canonical JSON과 SHA-256 eventHash로 변환한다.
 * 생성된 eventHash는 AuditAnchorClient를 통해 Noop 또는 블록체인 기록 흐름으로 전달된다.
 */
@Service
public class AuditEventService {

    private final EventHashService eventHashService;
    private final AuditAnchorClient auditAnchorClient;

    public AuditEventService(EventHashService eventHashService, AuditAnchorClient auditAnchorClient) {
        this.eventHashService = eventHashService;
        this.auditAnchorClient = auditAnchorClient;
    }

    /**
     * 이벤트 타입, 대상 id, payload를 하나의 감사 이벤트로 만들고 anchoring 결과까지 묶어 반환한다.
     */
    public AuditEventResult record(String eventType, UUID subjectId, Map<String, Object> payload) {
        String canonicalJson = eventHashService.toCanonicalJson(payload);
        String eventHash = eventHashService.sha256Hex(canonicalJson);
        AnchorResult anchorResult = auditAnchorClient.anchor(eventHash);

        return new AuditEventResult(eventType, subjectId, canonicalJson, eventHash, anchorResult.verifyStatus(), anchorResult);
    }
}
