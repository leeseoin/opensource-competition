package com.purchasesearch.product_backend.collection.repository;

import java.time.Instant;
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
}
