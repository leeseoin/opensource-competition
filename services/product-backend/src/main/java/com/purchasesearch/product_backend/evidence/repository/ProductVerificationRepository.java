package com.purchasesearch.product_backend.evidence.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.evidence.entity.ProductVerification;

/**
 * ProductVerificationRepository는 JSON/HTML 상품별 전수 비교 결과를 저장한다.
 */
public interface ProductVerificationRepository extends JpaRepository<ProductVerification, Long> {

	/** StatusCount는 검증 상태 문자열 하나와 그 상태인 검증 개수다. */
	interface StatusCount {

		/** @return 검증 상태 문자열 */
		String getStatus();

		/** @return 해당 상태인 검증 개수 */
		long getCount();
	}

	/** @param merchantProductId 판매처 상품 ID @return 최신 검증 순서의 비교 결과 */
	List<ProductVerification> findAllByMerchantProductIdOrderByVerifiedAtDescIdDesc(Long merchantProductId);

	/**
	 * 검증 완료 시각이 창 안에 있는 검증 결과를 상태별로 센다.
	 *
	 * @param since 창 시작 시각(포함)
	 * @param until 창 끝 시각(미포함)
	 * @return 상태별 검증 개수
	 */
	@Query("""
			SELECT verification.status AS status, COUNT(verification) AS count
			FROM ProductVerification verification
			WHERE verification.verifiedAt >= :since AND verification.verifiedAt < :until
			GROUP BY verification.status
			""")
	List<StatusCount> countByStatusInWindow(@Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);
}
