package com.purchasesearch.product_backend.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
