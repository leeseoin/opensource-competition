package com.purchasesearch.product_backend.evidence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.evidence.entity.Evidence;

/**
 * EvidenceRepository는 판매처 상품과 snapshot의 공개 출처 근거를 저장한다.
 */
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
}
