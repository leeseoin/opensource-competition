package com.purchasesearch.product_backend.evidence.entity;

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

import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;

/**
 * Evidence는 상품 사실을 확인한 공개 URL과 수집 시각 및 Collector 버전을 저장한다.
 */
@Entity
@Table(name = "evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_product_id", nullable = false)
	private MerchantProduct merchantProduct;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "offer_snapshot_id")
	private OfferSnapshot offerSnapshot;

	@Column(name = "evidence_type", nullable = false, length = 50)
	private String evidenceType;

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
	 * 상품 또는 배송 사실의 근거를 생성한다.
	 *
	 * @param merchantProduct 판매처 상품
	 * @param offerSnapshot 관련 offer snapshot
	 * @param evidenceType 근거 종류
	 * @param sourceUrl 사실을 확인한 공개 URL
	 * @param collectedAt 수집 시각
	 * @param collectorVersion Collector 버전
	 * @return 저장 전 근거 entity
	 */
	public static Evidence create(
			MerchantProduct merchantProduct,
			OfferSnapshot offerSnapshot,
			String evidenceType,
			String sourceUrl,
			OffsetDateTime collectedAt,
			String collectorVersion) {
		Evidence evidence = new Evidence();
		evidence.merchantProduct = merchantProduct;
		evidence.offerSnapshot = offerSnapshot;
		evidence.evidenceType = evidenceType;
		evidence.sourceUrl = sourceUrl;
		evidence.collectedAt = collectedAt;
		evidence.collectorVersion = collectorVersion;
		return evidence;
	}
}
