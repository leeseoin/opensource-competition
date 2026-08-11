package com.purchasesearch.product_backend.evidence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** OfferVerificationRequest는 선택 상품의 기준 snapshot과 우선 수집 job을 연결한다. */
@Entity
@Table(name = "offer_verification_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferVerificationRequest {

	@Id
	@Column(name = "verification_id")
	private UUID verificationId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_product_id", nullable = false)
	private MerchantProduct merchantProduct;

	@Column(name = "job_id", nullable = false, length = 128)
	private String jobId;

	@Column(name = "baseline_price_amount")
	private Long baselinePriceAmount;

	@Column(name = "baseline_currency", length = 3)
	private String baselineCurrency;

	@Column(name = "baseline_stock_status", nullable = false, length = 32)
	private String baselineStockStatus;

	@Column(name = "baseline_collected_at", nullable = false)
	private OffsetDateTime baselineCollectedAt;

	@Column(name = "requested_at", nullable = false)
	private OffsetDateTime requestedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * @param merchantProduct 검증 대상 판매처 상품
	 * @param baseline 요청 직전 최신 상품 snapshot
	 * @param jobId 우선 수집 job ID
	 * @param requestedAt 요청 시각
	 * @return 저장 전 재검증 요청
	 */
	public static OfferVerificationRequest create(
			MerchantProduct merchantProduct,
			ProductSummary baseline,
			String jobId,
			OffsetDateTime requestedAt) {
		OfferVerificationRequest request = new OfferVerificationRequest();
		request.verificationId = UUID.randomUUID();
		request.merchantProduct = merchantProduct;
		request.jobId = jobId;
		request.baselinePriceAmount = baseline.price() == null ? null : baseline.price().amount();
		request.baselineCurrency = baseline.price() == null ? null : baseline.price().currency();
		request.baselineStockStatus = baseline.stockStatus();
		request.baselineCollectedAt = baseline.source().collectedAt();
		request.requestedAt = requestedAt;
		return request;
	}
}
