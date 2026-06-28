package com.agentpayguard.api.anchor;

public record AnchorResult(
        String verifyStatus,
        String chainId,
        String contractAddress,
        String txHash
) {
}
