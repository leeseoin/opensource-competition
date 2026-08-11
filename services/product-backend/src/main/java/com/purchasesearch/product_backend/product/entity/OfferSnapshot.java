package com.purchasesearch.product_backend.product.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OfferSnapshot은 특정 수집 시각의 가격, 배송, 재고 및 평점 정보를 보존한다.
 */
@Entity
@Table(name = "offer_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_product_id", nullable = false)
	private MerchantProduct merchantProduct;

	@Column(name = "request_id", nullable = false, length = 128)
	private String requestId;

	@Column(name = "price_amount")
	private Long priceAmount;

	@Column(length = 3)
	private String currency;

	@Column(name = "shipping_fee_amount")
	private Long shippingFeeAmount;

	@Column(name = "shipping_fee_currency", length = 3)
	private String shippingFeeCurrency;

	@Column(name = "shipping_summary", length = 2000)
	private String shippingSummary;

	@Column(name = "stock_status", nullable = false, length = 32)
	private String stockStatus;

	@Column(precision = 3, scale = 2)
	private BigDecimal rating;

	@Column(name = "review_count")
	private Integer reviewCount;

	@Column(name = "source_url", nullable = false, length = 2048)
	private String sourceUrl;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	@Column(name = "collector_version", nullable = false, length = 100)
	private String collectorVersion;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 수집 결과에서 가격과 재고 snapshot을 생성한다.
	 *
	 * @param merchantProduct 판매처 상품
	 * @param requestId 수집 요청 식별자
	 * @param priceAmount 상품 가격
	 * @param currency 상품 가격 통화
	 * @param shippingFeeAmount 배송비
	 * @param shippingFeeCurrency 배송비 통화
	 * @param shippingSummary 배송 안내
	 * @param stockStatus 재고 상태
	 * @param rating 평점
	 * @param reviewCount 리뷰 수
	 * @param sourceUrl 상품 사실 출처
	 * @param collectedAt 수집 시각
	 * @param collectorVersion Collector 버전
	 * @return 저장 전 snapshot entity
	 */
	public static OfferSnapshot create(
			MerchantProduct merchantProduct,
			String requestId,
			Long priceAmount,
			String currency,
			Long shippingFeeAmount,
			String shippingFeeCurrency,
			String shippingSummary,
			String stockStatus,
			BigDecimal rating,
			Integer reviewCount,
			String sourceUrl,
			OffsetDateTime collectedAt,
			String collectorVersion) {
		OfferSnapshot snapshot = new OfferSnapshot();
		snapshot.merchantProduct = merchantProduct;
		snapshot.requestId = requestId;
		snapshot.priceAmount = priceAmount;
		snapshot.currency = currency;
		snapshot.shippingFeeAmount = shippingFeeAmount;
		snapshot.shippingFeeCurrency = shippingFeeCurrency;
		snapshot.shippingSummary = shippingSummary;
		snapshot.stockStatus = stockStatus;
		snapshot.rating = rating;
		snapshot.reviewCount = reviewCount;
		snapshot.sourceUrl = sourceUrl;
		snapshot.collectedAt = collectedAt;
		snapshot.collectorVersion = collectorVersion;
		return snapshot;
	}
}
