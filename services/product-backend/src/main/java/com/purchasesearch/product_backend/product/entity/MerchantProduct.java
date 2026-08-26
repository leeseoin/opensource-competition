package com.purchasesearch.product_backend.product.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MerchantProduct는 판매처와 판매처 상품번호 조합을 하나의 상품에 연결한다.
 */
@Entity
@Table(
		name = "merchant_products",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_merchant_products_merchant_external_id",
				columnNames = {"merchant", "external_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false, length = 64)
	private String merchant;

	@Column(name = "external_id", nullable = false, length = 200)
	private String externalId;

	@Column(name = "product_url", nullable = false, length = 2048)
	private String productUrl;

	@Column(name = "last_collected_at", nullable = false)
	private OffsetDateTime lastCollectedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/**
	 * 판매처 상품 식별 정보를 생성한다.
	 *
	 * @param product 공통 상품
	 * @param merchant 판매처 식별자
	 * @param externalId 판매처 상품번호
	 * @param productUrl 공개 상품 URL
	 * @param collectedAt 마지막 수집 시각
	 * @return 저장 전 판매처 상품 entity
	 */
	public static MerchantProduct create(
			Product product,
			String merchant,
			String externalId,
			String productUrl,
			OffsetDateTime collectedAt) {
		MerchantProduct merchantProduct = new MerchantProduct();
		merchantProduct.product = product;
		merchantProduct.merchant = merchant;
		merchantProduct.externalId = externalId;
		merchantProduct.updateCollection(productUrl, collectedAt);
		return merchantProduct;
	}

	/**
	 * 재수집한 판매처 상품의 URL과 마지막 수집 시각을 갱신한다.
	 *
	 * @param productUrl 최신 공개 상품 URL
	 * @param collectedAt 최신 수집 시각
	 */
	public void updateCollection(String productUrl, OffsetDateTime collectedAt) {
		this.productUrl = productUrl;
		this.lastCollectedAt = collectedAt;
	}
}
