package com.purchasesearch.product_backend.evidence.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OfferVerificationResponse는 기준 snapshot과 우선 수집 결과의 가격/재고 차이를 설명한다.
 *
 * @param verificationId 재검증 요청 ID
 * @param productId 대상 판매처 상품 ID
 * @param jobId RabbitMQ 수집 job ID
 * @param status 재검증 진행 또는 판정 상태
 * @param strategy 현재 크롤러 계약에서 사용하는 재검증 방식
 * @param baseline 요청 직전 snapshot
 * @param latest 새 수집에서 찾은 snapshot 또는 null
 * @param changes 변경된 사실 목록
 */
public record OfferVerificationResponse(
		UUID verificationId,
		long productId,
		String jobId,
		VerificationStatus status,
		String strategy,
		SnapshotView baseline,
		SnapshotView latest,
		List<String> changes) {

	/** VerificationStatus는 Queue 진행과 최종 offer 판정을 구분한다. */
	public enum VerificationStatus {
		QUEUED,
		RUNNING,
		VERIFIED,
		CHANGED,
		NOT_FOUND,
		FAILED
	}

	/**
	 * @param priceAmount 가격
	 * @param currency 통화
	 * @param stockStatus 재고 상태
	 * @param collectedAt 수집 시각
	 */
	public record SnapshotView(
			Long priceAmount,
			String currency,
			String stockStatus,
			OffsetDateTime collectedAt) {
	}
}
