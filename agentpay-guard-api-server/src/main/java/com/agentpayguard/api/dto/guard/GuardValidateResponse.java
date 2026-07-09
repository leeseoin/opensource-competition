package com.agentpayguard.api.dto.guard;

import java.util.UUID;

/**
 * Agent가 다음 행동을 결정하는 데 필요한 정책 판단과 감사 기록 요약이다.
 * sample-agent는 decision 값을 기준으로 Claude 호출 여부를 결정한다.
 */
public record GuardValidateResponse(
        String decision,
        String reasonCode,
        String reasonMessage,
        String policyVersion,
        UUID paymentRequestId,
        String eventHash,
        String anchorStatus,
        String chainId,
        String contractAddress,
        String txHash
) {
}
