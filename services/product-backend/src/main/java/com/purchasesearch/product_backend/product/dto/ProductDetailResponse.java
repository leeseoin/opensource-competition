package com.purchasesearch.product_backend.product.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;

/**
 * ProductDetailResponse는 상품 최신 사실과 그 사실을 뒷받침하는 공개 근거 및 검증을 반환한다.
 *
 * @param product 최신 가격/재고/옵션을 포함한 판매처 상품
 * @param freshness 최신성 판정
 * @param evidence 공개 근거 목록
 * @param verifications JSON/HTML 비교 검증 목록
 */
public record ProductDetailResponse(
		ProductSummary product,
		FreshnessView freshness,
		List<EvidenceView> evidence,
		List<VerificationView> verifications) {

	/** FreshnessStatus는 최신 상품 사실의 사용 가능 상태를 구분한다. */
	public enum FreshnessStatus {
		FRESH,
		STALE,
		MISSING
	}

	/** @param status 최신성 상태 @param ageHours 수집 후 경과 시간 @param staleAfterHours 만료 기준 */
	public record FreshnessView(FreshnessStatus status, long ageHours, long staleAfterHours) {
	}

	/**
	 * @param evidenceType 근거 종류
	 * @param sourceUrl 공개 출처 URL
	 * @param collectedAt 수집 시각
	 * @param collectorVersion 수집기 버전
	 */
	public record EvidenceView(
			String evidenceType,
			String sourceUrl,
			OffsetDateTime collectedAt,
			String collectorVersion) {
	}

	/**
	 * @param status 검증 상태
	 * @param comparedFields 비교 필드
	 * @param differences 확인된 차이
	 * @param jsonSourceUrl JSON 출처
	 * @param htmlSourceUrl HTML 출처
	 * @param verifiedAt 검증 시각
	 */
	public record VerificationView(
			String status,
			List<String> comparedFields,
			List<Map<String, Object>> differences,
			String jsonSourceUrl,
			String htmlSourceUrl,
			OffsetDateTime verifiedAt) {
	}
}
