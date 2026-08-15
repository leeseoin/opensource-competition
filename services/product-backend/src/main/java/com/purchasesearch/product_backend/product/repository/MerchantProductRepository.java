package com.purchasesearch.product_backend.product.repository;

import java.time.OffsetDateTime;
import java.util.List;
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

	/** MerchantCount는 판매처 하나와 그 판매처가 창 안에서 수집한 상품 개수다. */
	interface MerchantCount {

		/** @return 판매처 식별자 */
		String getMerchant();

		/** @return 해당 판매처가 창 안에서 수집한 상품 개수 */
		long getCount();
	}

	/**
	 * 마지막 수집 시각이 창 안에 있는 판매처 상품을 판매처별로 센다.
	 *
	 * @param since 창 시작 시각(포함)
	 * @param until 창 끝 시각(미포함)
	 * @return 판매처별 수집 상품 개수
	 */
	@Query("""
			SELECT merchantProduct.merchant AS merchant, COUNT(merchantProduct) AS count
			FROM MerchantProduct merchantProduct
			WHERE merchantProduct.lastCollectedAt >= :since AND merchantProduct.lastCollectedAt < :until
			GROUP BY merchantProduct.merchant
			""")
	List<MerchantCount> countByMerchantInWindow(@Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);

	/** CandidateRetrievalSignalProjection은 후보별 keyword/vector 원시 검색 점수를 읽는다. */
	interface CandidateRetrievalSignalProjection {

		/** @return 판매처 상품 ID */
		Long getCandidateId();

		/** @return exact/FTS/trigram 중 가장 높은 0부터 1 사이 keyword 점수 */
		Double getKeywordScore();

		/** @return 같은 model vector의 cosine 유사도 또는 vector가 없으면 null */
		Double getSemanticScore();
	}

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
						OR LOWER(CAST(product.category_path AS TEXT)) LIKE LOWER(CONCAT('%', :query, '%'))
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
	 * 상위 후보와 같은 판매처/브랜드/상품명/카테고리의 전체 판매 행을 조회한다.
	 *
	 * @param merchant 판매처 식별자
	 * @param name 정규화 전 대표 상품명
	 * @param brand 정규화 전 대표 브랜드 또는 빈 문자열
	 * @param categoryPath `/`로 연결한 카테고리 경로
	 * @return 재고나 사용자 조건으로 제거하지 않은 최신 판매처 상품 목록
	 */
	@Query(nativeQuery = true, value = """
			SELECT merchant_product.*
			FROM merchant_products merchant_product
			JOIN products product ON product.id = merchant_product.product_id
			WHERE merchant_product.merchant = :merchant
			  AND LOWER(TRIM(product.name)) = LOWER(TRIM(:name))
			  AND LOWER(COALESCE(TRIM(product.brand), '')) = LOWER(TRIM(:brand))
			  AND product.category_path = TO_JSONB(STRING_TO_ARRAY(:categoryPath, '/'))
			ORDER BY merchant_product.last_collected_at DESC, merchant_product.id DESC
			""")
	List<MerchantProduct> findFamilyListings(
			@Param("merchant") String merchant,
			@Param("name") String name,
			@Param("brand") String brand,
			@Param("categoryPath") String categoryPath);

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
	 * @param enableFullText 전문 검색과 trigram fallback 활성화 여부
	 * @param embeddingProvider vector embedding 제공자
	 * @param embeddingModel vector embedding model과 version 식별자
	 * @param queryVector pgvector 질문 embedding literal
	 * @param pageable 후보 제한
	 * @return 확인 조건과 일치하는 최근 수집 상품 page
	 */
	@Query(
			nativeQuery = true,
			value = """
					SELECT merchant_product.*
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					LEFT JOIN product_embeddings semantic_embedding
					  ON semantic_embedding.merchant_product_id = merchant_product.id
					 AND semantic_embedding.provider = :embeddingProvider
					 AND CONCAT(semantic_embedding.model, '@', semantic_embedding.model_version) = :embeddingModel
					JOIN LATERAL (
						SELECT snapshot.*
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_snapshot ON TRUE
					LEFT JOIN LATERAL (
						SELECT snapshot.id
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						  AND EXISTS (
							SELECT 1 FROM product_options known_option
							WHERE known_option.offer_snapshot_id = snapshot.id
						  )
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_option_snapshot ON TRUE
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
						OR (
							:enableFullText = TRUE
							AND (
								TO_TSVECTOR('simple', product.search_text)
									@@ WEBSEARCH_TO_TSQUERY('simple', LOWER(:query))
								OR WORD_SIMILARITY(LOWER(:query), product.search_text) >= 0.3
								OR SIMILARITY(
									REGEXP_REPLACE(LOWER(:query), '[^[:alnum:]]', '', 'g'),
									REGEXP_REPLACE(LOWER(product.name), '[^[:alnum:]]', '', 'g')) >= 0.3
								OR WORD_SIMILARITY(LOWER(:query), LOWER(CAST(product.category_path AS TEXT))) >= 0.3
								OR EXISTS (
									SELECT 1
									FROM collection_search_contexts fuzzy_context
									WHERE fuzzy_context.request_id = latest_snapshot.request_id
									  AND WORD_SIMILARITY(LOWER(:query), LOWER(fuzzy_context.search_query)) >= 0.3
								)
							)
						)
						OR (
							:queryVector IS NOT NULL
							AND semantic_embedding.embedding IS NOT NULL
							AND semantic_embedding.embedding <=> CAST(:queryVector AS vector) <= 0.55
						)
					  )
					  AND latest_snapshot.stock_status = 'available'
					  AND (:minPrice IS NULL OR latest_snapshot.price_amount >= :minPrice)
					  AND (:maxPrice IS NULL OR latest_snapshot.price_amount <= :maxPrice)
					  AND (:currency IS NULL OR latest_snapshot.currency = :currency)
					  AND (
						:sizesCsv IS NULL OR EXISTS (
							SELECT 1 FROM product_options option_value
							WHERE option_value.offer_snapshot_id = latest_option_snapshot.id
							  AND option_value.stock_status = 'available'
							  AND POSITION(',' || LOWER(COALESCE(option_value.size, '')) || ',' IN :sizesCsv) > 0
						)
					  )
					  AND (
						:colorsCsv IS NULL OR EXISTS (
							SELECT 1 FROM product_options option_value
							WHERE option_value.offer_snapshot_id = latest_option_snapshot.id
							  AND option_value.stock_status = 'available'
							  AND POSITION(',' || LOWER(COALESCE(option_value.color, '')) || ',' IN :colorsCsv) > 0
						)
					  )
					ORDER BY
					  CASE
						WHEN :query IS NULL THEN 0
						WHEN product.search_text LIKE LOWER(CONCAT('%', :query, '%'))
							OR LOWER(CAST(product.category_path AS TEXT)) LIKE LOWER(CONCAT('%', :query, '%')) THEN 4
						WHEN :enableFullText = TRUE AND TO_TSVECTOR('simple', product.search_text)
							@@ WEBSEARCH_TO_TSQUERY('simple', LOWER(:query)) THEN 3
						WHEN :enableFullText = TRUE
							AND (WORD_SIMILARITY(LOWER(:query), product.search_text) >= 0.3
								OR SIMILARITY(
									REGEXP_REPLACE(LOWER(:query), '[^[:alnum:]]', '', 'g'),
									REGEXP_REPLACE(LOWER(product.name), '[^[:alnum:]]', '', 'g')) >= 0.3
								OR WORD_SIMILARITY(LOWER(:query), LOWER(CAST(product.category_path AS TEXT))) >= 0.3) THEN 2
						WHEN :queryVector IS NOT NULL AND semantic_embedding.embedding IS NOT NULL
							AND semantic_embedding.embedding <=> CAST(:queryVector AS vector) <= 0.55 THEN 1
						ELSE 0
					  END DESC,
					  merchant_product.last_collected_at DESC,
					  merchant_product.id DESC
					""",
			countQuery = """
					SELECT COUNT(merchant_product.id)
					FROM merchant_products merchant_product
					JOIN products product ON product.id = merchant_product.product_id
					LEFT JOIN product_embeddings semantic_embedding
					  ON semantic_embedding.merchant_product_id = merchant_product.id
					 AND semantic_embedding.provider = :embeddingProvider
					 AND CONCAT(semantic_embedding.model, '@', semantic_embedding.model_version) = :embeddingModel
					JOIN LATERAL (
						SELECT snapshot.*
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_snapshot ON TRUE
					LEFT JOIN LATERAL (
						SELECT snapshot.id
						FROM offer_snapshots snapshot
						WHERE snapshot.merchant_product_id = merchant_product.id
						  AND EXISTS (
							SELECT 1 FROM product_options known_option
							WHERE known_option.offer_snapshot_id = snapshot.id
						  )
						ORDER BY snapshot.collected_at DESC, snapshot.id DESC
						LIMIT 1
					) latest_option_snapshot ON TRUE
					WHERE (:merchant IS NULL OR merchant_product.merchant = :merchant)
					  AND (
						:query IS NULL
						OR LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
						OR LOWER(CAST(product.category_path AS TEXT)) LIKE LOWER(CONCAT('%', :query, '%'))
						OR EXISTS (
							SELECT 1 FROM collection_search_contexts search_context
							WHERE search_context.request_id = latest_snapshot.request_id
							  AND LOWER(search_context.search_query) LIKE LOWER(CONCAT('%', :query, '%'))
						)
						OR (
							:enableFullText = TRUE
							AND (
								TO_TSVECTOR('simple', product.search_text)
									@@ WEBSEARCH_TO_TSQUERY('simple', LOWER(:query))
								OR WORD_SIMILARITY(LOWER(:query), product.search_text) >= 0.3
								OR SIMILARITY(
									REGEXP_REPLACE(LOWER(:query), '[^[:alnum:]]', '', 'g'),
									REGEXP_REPLACE(LOWER(product.name), '[^[:alnum:]]', '', 'g')) >= 0.3
								OR WORD_SIMILARITY(LOWER(:query), LOWER(CAST(product.category_path AS TEXT))) >= 0.3
								OR EXISTS (
									SELECT 1 FROM collection_search_contexts fuzzy_context
									WHERE fuzzy_context.request_id = latest_snapshot.request_id
									  AND WORD_SIMILARITY(LOWER(:query), LOWER(fuzzy_context.search_query)) >= 0.3
								)
							)
						)
						OR (
							:queryVector IS NOT NULL
							AND semantic_embedding.embedding IS NOT NULL
							AND semantic_embedding.embedding <=> CAST(:queryVector AS vector) <= 0.55
						)
					  )
					  AND latest_snapshot.stock_status = 'available'
					  AND (:minPrice IS NULL OR latest_snapshot.price_amount >= :minPrice)
					  AND (:maxPrice IS NULL OR latest_snapshot.price_amount <= :maxPrice)
					  AND (:currency IS NULL OR latest_snapshot.currency = :currency)
					  AND (:sizesCsv IS NULL OR EXISTS (
						SELECT 1 FROM product_options option_value
						WHERE option_value.offer_snapshot_id = latest_option_snapshot.id
						  AND option_value.stock_status = 'available'
						  AND POSITION(',' || LOWER(COALESCE(option_value.size, '')) || ',' IN :sizesCsv) > 0
					  ))
					  AND (:colorsCsv IS NULL OR EXISTS (
						SELECT 1 FROM product_options option_value
						WHERE option_value.offer_snapshot_id = latest_option_snapshot.id
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
			@Param("enableFullText") boolean enableFullText,
			@Param("embeddingProvider") String embeddingProvider,
			@Param("embeddingModel") String embeddingModel,
			@Param("queryVector") String queryVector,
			Pageable pageable);

	/**
	 * 이미 구조화 필터를 통과한 후보의 keyword/vector 점수를 같은 검색 문서에서 계산한다.
	 *
	 * @param candidateIds 점수를 계산할 판매처 상품 ID
	 * @param query 정규화한 사용자 검색어
	 * @param embeddingProvider vector embedding 제공자
	 * @param embeddingModel vector embedding model과 version 식별자
	 * @param queryVector pgvector 질문 embedding literal
	 * @return 후보 ID별 0부터 1 사이 검색 신호
	 */
	@Query(nativeQuery = true, value = """
			SELECT merchant_product.id AS candidateId,
			       LEAST(1.0, GREATEST(
			         CASE
			           WHEN LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
			             OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
			             OR LOWER(CAST(product.category_path AS TEXT)) LIKE LOWER(CONCAT('%', :query, '%'))
			           THEN 1.0 ELSE 0.0
			         END,
			         CASE
			           WHEN TO_TSVECTOR('simple', product.search_text)
			             @@ WEBSEARCH_TO_TSQUERY('simple', LOWER(:query))
			           THEN TS_RANK_CD(
			             TO_TSVECTOR('simple', product.search_text),
			             WEBSEARCH_TO_TSQUERY('simple', LOWER(:query)))
			           ELSE 0.0
			         END,
				         WORD_SIMILARITY(LOWER(:query), product.search_text),
				         SIMILARITY(
				           REGEXP_REPLACE(LOWER(:query), '[^[:alnum:]]', '', 'g'),
				           REGEXP_REPLACE(LOWER(product.name), '[^[:alnum:]]', '', 'g')),
				         WORD_SIMILARITY(LOWER(:query), LOWER(CAST(product.category_path AS TEXT))),
			         0.25 * COALESCE((
			           SELECT MAX(WORD_SIMILARITY(LOWER(:query), LOWER(search_context.search_query)))
			           FROM offer_snapshots snapshot
			           JOIN collection_search_contexts search_context
			             ON search_context.request_id = snapshot.request_id
			           WHERE snapshot.merchant_product_id = merchant_product.id
			         ), 0.0)
			       ))::DOUBLE PRECISION AS keywordScore,
			       CASE
			         WHEN :queryVector IS NOT NULL AND semantic_embedding.embedding IS NOT NULL
			         THEN GREATEST(
			           0.0,
			           1.0 - (semantic_embedding.embedding <=> CAST(:queryVector AS vector)))
			           ::DOUBLE PRECISION
			         ELSE NULL
			       END AS semanticScore
			FROM merchant_products merchant_product
			JOIN products product ON product.id = merchant_product.product_id
			LEFT JOIN product_embeddings semantic_embedding
			  ON semantic_embedding.merchant_product_id = merchant_product.id
			 AND semantic_embedding.provider = :embeddingProvider
			 AND CONCAT(semantic_embedding.model, '@', semantic_embedding.model_version) = :embeddingModel
			WHERE merchant_product.id IN (:candidateIds)
			""")
	List<CandidateRetrievalSignalProjection> findCandidateRetrievalSignals(
			@Param("candidateIds") List<Long> candidateIds,
			@Param("query") String query,
			@Param("embeddingProvider") String embeddingProvider,
			@Param("embeddingModel") String embeddingModel,
			@Param("queryVector") String queryVector);
}
