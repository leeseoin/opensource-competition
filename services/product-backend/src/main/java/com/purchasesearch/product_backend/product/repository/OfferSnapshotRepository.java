package com.purchasesearch.product_backend.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;

/**
 * OfferSnapshotRepository는 가격과 재고 이력을 추가하고 최신 snapshot을 조회한다.
 */
public interface OfferSnapshotRepository extends JpaRepository<OfferSnapshot, Long> {

	/**
	 * 판매처 상품의 가장 최근 offer snapshot을 조회한다.
	 *
	 * @param merchantProduct 판매처 상품
	 * @return 가장 최근 snapshot
	 */
	Optional<OfferSnapshot> findFirstByMerchantProductOrderByCollectedAtDescIdDesc(
			MerchantProduct merchantProduct);

	/**
	 * 최신 snapshot이 옵션을 생략한 기존 데이터에서 마지막으로 옵션이 확인된 snapshot을 찾는다.
	 *
	 * @param merchantProductId 판매처 상품 ID
	 * @return 옵션이 하나 이상 연결된 최신 snapshot
	 */
	@Query(nativeQuery = true, value = """
			SELECT snapshot.*
			FROM offer_snapshots snapshot
			WHERE snapshot.merchant_product_id = :merchantProductId
			  AND EXISTS (
				SELECT 1 FROM product_options option_value
				WHERE option_value.offer_snapshot_id = snapshot.id
			  )
			ORDER BY snapshot.collected_at DESC, snapshot.id DESC
			LIMIT 1
			""")
	Optional<OfferSnapshot> findLatestWithOptions(
			@Param("merchantProductId") long merchantProductId);
}
