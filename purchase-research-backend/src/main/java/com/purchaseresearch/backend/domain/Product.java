package com.purchaseresearch.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "product",
		uniqueConstraints = @UniqueConstraint(name = "uk_product_site_type_source_id", columnNames = { "site_type", "source_product_id" })
)
@Getter
@Setter
@NoArgsConstructor
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "site_type", nullable = false)
	private SiteType siteType;

	@Column(name = "source_product_id", nullable = false, length = 50)
	private String sourceProductId;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(length = 100)
	private String brand;

	private Integer price;

	@Column(name = "price_original")
	private Integer priceOriginal;

	@Column(name = "discount_percent")
	private Integer discountPercent;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "style_code", length = 50)
	private String styleCode;

	@Column(nullable = false, length = 500)
	private String link;

	@Column(name = "review_count", nullable = false)
	private Integer reviewCount = 0;

	@Column(name = "collected_at", nullable = false)
	private LocalDateTime collectedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
