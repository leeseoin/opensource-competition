package com.purchasesearch.product_backend.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.product.entity.OfferSnapshot;
import com.purchasesearch.product_backend.product.entity.ProductOption;

/**
 * ProductOptionRepository는 offer snapshot에 속한 옵션 정보를 저장하고 조회한다.
 */
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

	/**
	 * 하나의 offer snapshot에 포함된 옵션을 저장 순서로 조회한다.
	 *
	 * @param offerSnapshot offer snapshot
	 * @return 옵션 목록
	 */
	List<ProductOption> findAllByOfferSnapshotOrderById(OfferSnapshot offerSnapshot);
}
