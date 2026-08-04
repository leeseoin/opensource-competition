package com.purchasesearch.product_backend.evidence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.evidence.entity.ProductVerification;

/**
 * ProductVerificationRepository는 JSON/HTML 상품별 전수 비교 결과를 저장한다.
 */
public interface ProductVerificationRepository extends JpaRepository<ProductVerification, Long> {
}
