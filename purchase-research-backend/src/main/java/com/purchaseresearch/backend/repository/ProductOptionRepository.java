package com.purchaseresearch.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchaseresearch.backend.domain.ProductOption;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

	@Modifying
	@Query("DELETE FROM ProductOption o WHERE o.productId = :productId")
	void deleteByProductId(@Param("productId") Long productId);
}
