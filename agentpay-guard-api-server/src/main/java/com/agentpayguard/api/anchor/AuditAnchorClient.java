package com.agentpayguard.api.anchor;

public interface AuditAnchorClient {

    AnchorResult anchor(String eventHash);
}
