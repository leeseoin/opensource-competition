package com.agentpayguard.api.audit;

import com.agentpayguard.api.anchor.AnchorResult;
import java.util.UUID;

/**
 * 감사 이벤트 생성 결과를 API 응답에 포함하기 위한 값 객체이다.
 * canonicalJson과 eventHash는 재검증용이고, anchor는 블록체인 기록 상태를 표현한다.
 */
public record AuditEventResult(
        String eventType,
        UUID subjectId,
        String canonicalJson,
        String eventHash,
        String anchorStatus,
        AnchorResult anchor
) {
}
