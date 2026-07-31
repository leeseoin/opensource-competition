package com.purchasesearch.product_backend.product.entity;

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
 * ProductOption은 하나의 offer snapshot에서 확인한 사이즈와 색상별 가격 및 재고를
 * 저장한다.
 */
@Entity
@Table(name = "product_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "offer_snapshot_id", nullable = false)
	private OfferSnapshot offerSnapshot;

	@Column(name = "external_id", length = 200)
	private String externalId;

	@Column(nullable = false, length = 300)
	private String label;

	@Column(length = 2000)
	private String size;

	@Column(length = 2000)
	private String color;

	@Column(name = "stock_status", nullable = false, length = 32)
	private String stockStatus;

	@Column(name = "price_amount")
	private Long priceAmount;

	@Column(length = 3)
	private String currency;

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
	 * 수집 결과에서 상품 옵션 snapshot을 생성한다.
	 *
	 * @param offerSnapshot 옵션이 속한 offer snapshot
	 * @param externalId 판매처 옵션 식별자
	 * @param label 옵션 표시명
	 * @param size 사이즈
	 * @param color 색상
	 * @param stockStatus 재고 상태
	 * @param priceAmount 옵션 가격
	 * @param currency 옵션 가격 통화
	 * @param sourceUrl 옵션 사실 출처
	 * @param collectedAt 옵션 수집 시각
	 * @param collectorVersion Collector 버전
	 * @return 저장 전 옵션 entity
	 */
	public static ProductOption create(
			OfferSnapshot offerSnapshot,
			String externalId,
			String label,
			String size,
			String color,
			String stockStatus,
			Long priceAmount,
			String currency,
			String sourceUrl,
			OffsetDateTime collectedAt,
			String collectorVersion) {
		ProductOption option = new ProductOption();
		option.offerSnapshot = offerSnapshot;
		option.externalId = externalId;
		option.label = label;
		option.size = size;
		option.color = color;
		option.stockStatus = stockStatus;
		option.priceAmount = priceAmount;
		option.currency = currency;
		option.sourceUrl = sourceUrl;
		option.collectedAt = collectedAt;
		option.collectorVersion = collectorVersion;
		return option;
	}
}
