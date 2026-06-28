package com.agentpayguard.api.anchor;

import org.springframework.stereotype.Component;

@Component
public class NoopAuditAnchorClient implements AuditAnchorClient {

    @Override
    public AnchorResult anchor(String eventHash) {
        return new AnchorResult("PENDING", null, null, null);
    }
}
