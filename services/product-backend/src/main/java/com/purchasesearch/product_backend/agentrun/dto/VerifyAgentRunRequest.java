package com.purchasesearch.product_backend.agentrun.dto;

import jakarta.validation.constraints.Positive;

/** @param productId 사용자가 선택한 판매처 상품 ID */
public record VerifyAgentRunRequest(@Positive long productId) {
}
