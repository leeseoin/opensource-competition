package com.purchasesearch.product_backend.evidence.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * BulkOfferVerificationRequest는 관리자 화면에서 선택한 여러 판매처 상품을 한 번에
 * 재검증(재수집) 요청하는 조건이다. 상품 하나당 Queue 발행에 broker confirm 대기가
 * 포함돼 수천 건이면 처리에 수 분이 걸릴 수 있으므로, 접수는
 * {@link com.purchasesearch.product_backend.evidence.service.BulkOfferVerificationRunner}가
 * 배경 스레드에서 순차 처리하고 진행 상태는 별도 조회 API로 확인한다.
 *
 * @param productIds 재검증할 판매처 상품 ID 목록(최대 10,000개)
 */
public record BulkOfferVerificationRequest(
		@NotEmpty
		@Size(max = 10_000)
		List<@NotNull @Positive Long> productIds) {
}
