package com.agentpayguard.api.service.anchor;

import com.agentpayguard.api.dto.anchor.AnchorResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 블록체인 연동이 비활성화된 기본 구현이다.
 * 로컬 개발과 API 단독 테스트에서 eventHash 생성 흐름을 유지하되 실제 tx는 보내지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "agentpay.audit-anchor", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopAuditAnchorClient implements AuditAnchorClient {

    /**
     * 실제 anchoring을 수행하지 않고 PENDING 상태를 반환한다.
     */
    @Override
    public AnchorResult anchor(String eventHash) {
        return new AnchorResult("PENDING", null, null, null);
    }
}
