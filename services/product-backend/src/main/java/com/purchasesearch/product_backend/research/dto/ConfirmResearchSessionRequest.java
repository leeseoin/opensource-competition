package com.purchasesearch.product_backend.research.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * ConfirmResearchSessionRequest는 사용자가 최종 확인하거나 수정한 구매 조건을 전달한다.
 *
 * @param conditions 확정할 구매 조건
 */
public record ConfirmResearchSessionRequest(@NotNull @Valid PurchaseCondition conditions) {
}
