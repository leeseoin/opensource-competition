package com.purchaseresearch.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchaseresearch.backend.domain.Product;
import com.purchaseresearch.backend.domain.SiteType;

public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findBySiteTypeAndSourceProductId(SiteType siteType, String sourceProductId);

	// §3.3 조회 파이프라인용 검색. keyword는 title LIKE 매칭(카테고리 컬럼이 없어 대체),
	// maxPrice는 price 이하. 둘 다 없으면(NULL) 조건을 걸지 않는다.
	@Query("SELECT p FROM Product p "
			+ "WHERE (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%')) "
			+ "AND (:maxPrice IS NULL OR p.price <= :maxPrice) "
			+ "ORDER BY p.updatedAt DESC")
	List<Product> search(@Param("keyword") String keyword, @Param("maxPrice") Integer maxPrice);
}
