package com.agentpayguard.api.dto.guard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 외부 Agent가 유료 리소스 사용 전에 Guard에 보내는 최소 검증 요청이다.
 * Agent는 결제 상세 모델을 알 필요 없이 작업 의도와 예상 비용만 전달한다.
 */
public record GuardValidateRequest(
        @NotBlank String agentId,
        @NotBlank String intent,
        @NotNull @Positive BigDecimal estimatedCost
) {
}
