package com.agentpayguard.api.anchor;

/**
 * Audit event hash를 외부 감사 저장소에 기록하는 경계 인터페이스이다.
 * 현재 PoC에서는 Noop 구현과 web3j 기반 AuditAnchor 컨트랙트 구현을 이 인터페이스로 교체한다.
 */
public interface AuditAnchorClient {

    /**
     * canonical audit event에서 생성한 SHA-256 eventHash를 기록하고 검증/트랜잭션 상태를 반환한다.
     */
    AnchorResult anchor(String eventHash);
}
