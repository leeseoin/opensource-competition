package com.purchaseresearch.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "product_review",
		uniqueConstraints = @UniqueConstraint(name = "uk_product_review_source", columnNames = { "product_id", "review_source_id" })
)
@Getter
@Setter
@NoArgsConstructor
public class ProductReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	// 리뷰 자체의 일련번호(예: ABC마트 rvwId). 작성자 식별 정보(닉네임/회원ID 등)는 저장하지 않는다.
	@Column(name = "review_source_id", nullable = false, length = 50)
	private String reviewSourceId;

	@Column(columnDefinition = "TEXT")
	private String content;

	private BigDecimal score;

	@Column(name = "review_date")
	private LocalDate reviewDate;

	@Column(length = 20)
	private String size;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
