package com.purchasesearch.product_backend.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * DashboardSummaryResponse는 지정한 시간 창 안의 수집 job/작업, 판매처 상품, 검증 현황을 집계한다.
 *
 * @param window 집계에 사용한 시간 창
 * @param jobs 수집 job 상태별 집계
 * @param tasks 페이지 작업 상태별 집계
 * @param products 판매처별 수집 상품 집계
 * @param verifications JSON/HTML 검증 상태별 집계
 * @param generatedAt 응답을 계산한 시각
 */
public record DashboardSummaryResponse(
		Window window,
		StatusCounts jobs,
		StatusCounts tasks,
		ProductCounts products,
		VerificationCounts verifications,
		OffsetDateTime generatedAt) {

	/**
	 * Window는 집계에 사용한 시간 범위를 반환한다.
	 *
	 * @param since 창 시작 시각(포함)
	 * @param until 창 끝 시각(미포함)
	 */
	public record Window(OffsetDateTime since, OffsetDateTime until) {
	}

	/**
	 * StatusCounts는 상태별 개수와 합계를 반환한다.
	 *
	 * @param total 전체 개수
	 * @param byStatus 상태 문자열별 개수(가능한 모든 상태를 0으로라도 포함)
	 */
	public record StatusCounts(long total, Map<String, Long> byStatus) {
	}

	/**
	 * ProductCounts는 판매처별로 창 안에서 수집된 상품 수를 반환한다.
	 *
	 * @param totalMerchantProductsCollected 전체 수집 상품 수
	 * @param byMerchant 판매처별 수집 상품 수
	 */
	public record ProductCounts(long totalMerchantProductsCollected, List<MerchantCount> byMerchant) {

		/**
		 * MerchantCount는 한 판매처가 창 안에서 수집한 상품 수다.
		 *
		 * @param merchant 판매처 식별자
		 * @param count 수집 상품 수
		 */
		public record MerchantCount(String merchant, long count) {
		}
	}

	/**
	 * VerificationCounts는 검증 상태별 개수와 일치율을 반환한다.
	 *
	 * @param total 전체 검증 수
	 * @param byStatus 검증 상태별 개수
	 * @param matchRate MATCHED 비율이며 total이 0이면 null
	 */
	public record VerificationCounts(long total, Map<String, Long> byStatus, Double matchRate) {
	}

	/** ErrorResponse는 잘못된 집계 요청을 code/message로 설명한다. */
	public record ErrorResponse(String code, String message) {
	}
}
