package com.purchaseresearch.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchaseresearch.backend.domain.ProductReview;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

	boolean existsByProductIdAndReviewSourceId(Long productId, String reviewSourceId);
}
