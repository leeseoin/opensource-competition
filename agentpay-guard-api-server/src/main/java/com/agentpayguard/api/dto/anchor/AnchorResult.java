package com.agentpayguard.api.dto.anchor;

/**
 * Audit anchoring 결과를 API 응답과 저장 로직에 전달하기 위한 값 객체이다.
 * txHash가 있으면 온체인 기록 트랜잭션까지 전송된 상태로 판단한다.
 */
public record AnchorResult(
        String verifyStatus,
        String chainId,
        String contractAddress,
        String txHash
) {
}
