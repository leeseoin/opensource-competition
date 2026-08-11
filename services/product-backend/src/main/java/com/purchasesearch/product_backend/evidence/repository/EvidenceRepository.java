package com.purchasesearch.product_backend.evidence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.evidence.entity.Evidence;

/**
 * EvidenceRepository는 판매처 상품과 snapshot의 공개 출처 근거를 저장한다.
 */
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

	/** @param merchantProductId 판매처 상품 ID @return 최신 수집 순서의 공개 근거 */
	List<Evidence> findAllByMerchantProductIdOrderByCollectedAtDescIdDesc(Long merchantProductId);
}
