package com.purchasesearch.product_backend.evidence.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;

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
 * ProductVerification은 JSON 기본 수집값과 렌더링 HTML 표시값의 상품별 비교 결과를
 * 저장한다.
 */
@Entity
@Table(name = "product_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_product_id", nullable = false)
	private MerchantProduct merchantProduct;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "offer_snapshot_id", nullable = false)
	private OfferSnapshot offerSnapshot;

	@Column(nullable = false, length = 32)
	private String status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "compared_fields", nullable = false, columnDefinition = "jsonb")
	private List<String> comparedFields;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> differences;

	@Column(name = "json_source_url", nullable = false, length = 2048)
	private String jsonSourceUrl;

	@Column(name = "html_source_url", nullable = false, length = 2048)
	private String htmlSourceUrl;

	@Column(name = "verified_at", nullable = false)
	private OffsetDateTime verifiedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * CollectorResult의 JSON/HTML 비교 결과를 저장 전 entity로 생성한다.
	 *
	 * @param merchantProduct 검증 대상 판매처 상품
	 * @param offerSnapshot 검증한 시점의 상품 snapshot
	 * @param status 검증 상태
	 * @param comparedFields 비교한 필드 목록
	 * @param differences 불일치 필드와 양쪽 값
	 * @param jsonSourceUrl JSON 검색 응답 URL
	 * @param htmlSourceUrl 렌더링 HTML 페이지 URL
	 * @param verifiedAt 비교 완료 시각
	 * @return 저장 전 상품 검증 entity
	 */
	public static ProductVerification create(
			MerchantProduct merchantProduct,
			OfferSnapshot offerSnapshot,
			String status,
			List<String> comparedFields,
			List<Map<String, Object>> differences,
			String jsonSourceUrl,
			String htmlSourceUrl,
			OffsetDateTime verifiedAt) {
		ProductVerification verification = new ProductVerification();
		verification.merchantProduct = merchantProduct;
		verification.offerSnapshot = offerSnapshot;
		verification.status = status;
		verification.comparedFields = List.copyOf(comparedFields);
		verification.differences = List.copyOf(differences);
		verification.jsonSourceUrl = jsonSourceUrl;
		verification.htmlSourceUrl = htmlSourceUrl;
		verification.verifiedAt = verifiedAt;
		return verification;
	}
}
