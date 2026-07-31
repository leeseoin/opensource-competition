package com.purchasesearch.product_backend.product.entity;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Product는 판매처와 관계없이 화면에 표시할 상품 기본 정보를 저장한다.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 500)
	private String name;

	@Column(length = 2000)
	private String brand;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "category_path", nullable = false, columnDefinition = "jsonb")
	private List<String> categoryPath;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "image_urls", nullable = false, columnDefinition = "jsonb")
	private List<String> imageUrls;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/**
	 * 새 상품 기본 정보를 생성한다.
	 *
	 * @param name 상품명
	 * @param brand 브랜드명
	 * @param categoryPath 카테고리 경로
	 * @param imageUrls 상품 이미지 URL 목록
	 * @return 저장 전 상품 entity
	 */
	public static Product create(
			String name,
			String brand,
			List<String> categoryPath,
			List<String> imageUrls) {
		Product product = new Product();
		product.update(name, brand, categoryPath, imageUrls);
		return product;
	}

	/**
	 * 같은 판매처 상품을 다시 수집했을 때 최신 기본 정보로 갱신한다.
	 *
	 * @param name 상품명
	 * @param brand 브랜드명
	 * @param categoryPath 카테고리 경로
	 * @param imageUrls 상품 이미지 URL 목록
	 */
	public void update(
			String name,
			String brand,
			List<String> categoryPath,
			List<String> imageUrls) {
		this.name = name;
		this.brand = brand;
		this.categoryPath = List.copyOf(categoryPath);
		this.imageUrls = List.copyOf(imageUrls);
	}
}
