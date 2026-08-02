package com.purchasesearch.product_backend.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.product.entity.MerchantProduct;

/**
 * MerchantProductRepository는 판매처 상품의 중복 식별과 사용자 검색을 담당한다.
 */
public interface MerchantProductRepository extends JpaRepository<MerchantProduct, Long> {

	/**
	 * 판매처와 외부 상품번호가 같은 기존 상품을 조회한다.
	 *
	 * @param merchant 판매처 식별자
	 * @param externalId 판매처 상품번호
	 * @return 기존 판매처 상품
	 */
	Optional<MerchantProduct> findByMerchantAndExternalId(String merchant, String externalId);

	/**
	 * 판매처와 상품명, 브랜드 또는 해당 상품을 수집한 검색어로 상품을 검색한다.
	 *
	 * @param merchant 선택 판매처
	 * @param query 선택 검색어
	 * @param pageable 페이지 조건
	 * @return 최근 수집 순서의 판매처 상품 page
	 */
	@Query(
			nativeQuery = true,
			value = """
					SELECT merchant_product.*
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					WHERE (:merchant IS NULL OR merchant_product.merchant = :merchant)
					  AND (
						:query IS NULL
						OR LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
						OR EXISTS (
							SELECT 1
							FROM offer_snapshots snapshot
							JOIN collection_search_contexts search_context
							  ON search_context.request_id = snapshot.request_id
							WHERE snapshot.merchant_product_id = merchant_product.id
							  AND LOWER(search_context.search_query) LIKE LOWER(CONCAT('%', :query, '%'))
						)
					  )
					ORDER BY merchant_product.last_collected_at DESC, merchant_product.id DESC
					""",
			countQuery = """
					SELECT COUNT(merchant_product.id)
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					WHERE (:merchant IS NULL OR merchant_product.merchant = :merchant)
					  AND (
						:query IS NULL
						OR LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
						OR EXISTS (
							SELECT 1
							FROM offer_snapshots snapshot
							JOIN collection_search_contexts search_context
							  ON search_context.request_id = snapshot.request_id
							WHERE snapshot.merchant_product_id = merchant_product.id
							  AND LOWER(search_context.search_query) LIKE LOWER(CONCAT('%', :query, '%'))
						)
					  )
					""")
	Page<MerchantProduct> search(
			@Param("merchant") String merchant,
			@Param("query") String query,
			Pageable pageable);
}
