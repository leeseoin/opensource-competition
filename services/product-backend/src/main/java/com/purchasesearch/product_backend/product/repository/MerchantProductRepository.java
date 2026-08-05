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

	/**
	 * 최신 offer와 옵션에 사용자 확인 가격, 사이즈, 색상 및 재고 조건을 적용한다.
	 *
	 * @param merchant 선택 판매처
	 * @param query 상품 검색어
	 * @param minPrice 최소 가격
	 * @param maxPrice 최대 가격
	 * @param currency 가격 통화
	 * @param sizesCsv 쉼표 경계를 포함한 소문자 사이즈 목록
	 * @param colorsCsv 쉼표 경계를 포함한 소문자 색상 목록
	 * @param pageable 후보 제한
	 * @return 확인 조건과 일치하는 최근 수집 상품 page
	 */
	@Query(
			nativeQuery = true,
			value = """
					SELECT merchant_product.*
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					JOIN LATERAL (
						SELECT snapshot.*
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_snapshot ON TRUE
					WHERE (:merchant IS NULL OR merchant_product.merchant = :merchant)
					  AND (
						:query IS NULL
						OR LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
						OR EXISTS (
							SELECT 1
							FROM collection_search_contexts search_context
							WHERE search_context.request_id = latest_snapshot.request_id
							  AND LOWER(search_context.search_query) LIKE LOWER(CONCAT('%', :query, '%'))
						)
					  )
					  AND latest_snapshot.stock_status = 'available'
					  AND (:minPrice IS NULL OR latest_snapshot.price_amount >= :minPrice)
					  AND (:maxPrice IS NULL OR latest_snapshot.price_amount <= :maxPrice)
					  AND (:currency IS NULL OR latest_snapshot.currency = :currency)
					  AND (
						:sizesCsv IS NULL OR EXISTS (
							SELECT 1 FROM product_options option_value
							WHERE option_value.offer_snapshot_id = latest_snapshot.id
							  AND option_value.stock_status = 'available'
							  AND POSITION(',' || LOWER(COALESCE(option_value.size, '')) || ',' IN :sizesCsv) > 0
						)
					  )
					  AND (
						:colorsCsv IS NULL OR EXISTS (
							SELECT 1 FROM product_options option_value
							WHERE option_value.offer_snapshot_id = latest_snapshot.id
							  AND option_value.stock_status = 'available'
							  AND POSITION(',' || LOWER(COALESCE(option_value.color, '')) || ',' IN :colorsCsv) > 0
						)
					  )
					ORDER BY merchant_product.last_collected_at DESC, merchant_product.id DESC
					""",
			countQuery = """
					SELECT COUNT(merchant_product.id)
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					JOIN LATERAL (
						SELECT snapshot.*
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_snapshot ON TRUE
					WHERE (:merchant IS NULL OR merchant_product.merchant = :merchant)
					  AND (
						:query IS NULL
						OR LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
						OR EXISTS (
							SELECT 1 FROM collection_search_contexts search_context
							WHERE search_context.request_id = latest_snapshot.request_id
							  AND LOWER(search_context.search_query) LIKE LOWER(CONCAT('%', :query, '%'))
						)
					  )
					  AND latest_snapshot.stock_status = 'available'
					  AND (:minPrice IS NULL OR latest_snapshot.price_amount >= :minPrice)
					  AND (:maxPrice IS NULL OR latest_snapshot.price_amount <= :maxPrice)
					  AND (:currency IS NULL OR latest_snapshot.currency = :currency)
					  AND (:sizesCsv IS NULL OR EXISTS (
						SELECT 1 FROM product_options option_value
						WHERE option_value.offer_snapshot_id = latest_snapshot.id
						  AND option_value.stock_status = 'available'
						  AND POSITION(',' || LOWER(COALESCE(option_value.size, '')) || ',' IN :sizesCsv) > 0
					  ))
					  AND (:colorsCsv IS NULL OR EXISTS (
						SELECT 1 FROM product_options option_value
						WHERE option_value.offer_snapshot_id = latest_snapshot.id
						  AND option_value.stock_status = 'available'
						  AND POSITION(',' || LOWER(COALESCE(option_value.color, '')) || ',' IN :colorsCsv) > 0
					  ))
					""")
	Page<MerchantProduct> searchCandidates(
			@Param("merchant") String merchant,
			@Param("query") String query,
			@Param("minPrice") Long minPrice,
			@Param("maxPrice") Long maxPrice,
			@Param("currency") String currency,
			@Param("sizesCsv") String sizesCsv,
			@Param("colorsCsv") String colorsCsv,
			Pageable pageable);
}
