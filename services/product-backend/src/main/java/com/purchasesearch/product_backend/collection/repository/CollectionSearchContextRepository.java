package com.purchasesearch.product_backend.collection.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.collection.entity.CollectionSearchContext;

/**
 * CollectionSearchContextRepository는 requestId별 검색 문맥의 저장과 중복 확인을 담당한다.
 */
public interface CollectionSearchContextRepository
		extends JpaRepository<CollectionSearchContext, String> {

	/**
	 * 판매처와 정규화된 검색어 및 기본 필터가 같은 마지막 수집 완료 시각을 조회한다.
	 *
	 * @param merchant 판매처 식별자
	 * @param searchQuery 앞뒤 공백과 대소문자를 무시해 비교할 검색어
	 * @return 동일한 수집 범위의 마지막 완료 시각
	 */
	@Query(nativeQuery = true, value = """
			SELECT collected_at
			FROM collection_search_contexts
			WHERE merchant = :merchant
			  AND LOWER(BTRIM(search_query)) = LOWER(BTRIM(:searchQuery))
			  AND filters = jsonb_build_object('inStockOnly', false)
			ORDER BY collected_at DESC
			LIMIT 1
			""")
	Optional<Instant> findLatestDefaultSearchCollectedAt(
			@Param("merchant") String merchant,
			@Param("searchQuery") String searchQuery);

	/**
	 * 기본 필터로 수집된 판매처/검색어 조합 중 마지막 수집이 기준 시각보다 오래된 조합을
	 * 가장 오래된 순으로 조회한다. CollectionFreshnessScheduler가 사용자 요청 전에
	 * 미리 갱신할 대상을 고를 때 사용한다.
	 *
	 * @param staleBoundary 이 시각 이전에 마지막으로 수집됐으면 대상에 포함한다
	 * @param batchSize 한 번에 반환할 최대 조합 수
	 * @return 판매처, 검색어와 마지막 수집 시각을 오래된 순으로 담은 목록
	 */
	@Query(nativeQuery = true, value = """
			WITH latest AS (
				SELECT DISTINCT ON (merchant, LOWER(BTRIM(search_query)))
					merchant, search_query, collected_at
				FROM collection_search_contexts
				WHERE filters = jsonb_build_object('inStockOnly', false)
				ORDER BY merchant, LOWER(BTRIM(search_query)), collected_at DESC
			)
			SELECT merchant AS merchant, search_query AS searchQuery, collected_at AS collectedAt
			FROM latest
			WHERE collected_at < :staleBoundary
			ORDER BY collected_at ASC
			LIMIT :batchSize
			""")
	List<StaleSearchContext> findStaleDefaultSearchContexts(
			@Param("staleBoundary") Instant staleBoundary,
			@Param("batchSize") int batchSize);
}
