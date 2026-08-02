package com.purchasesearch.product_backend.collection.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CollectionSearchContext는 한 수집 요청에 사용한 검색어와 필터를 requestId에 연결한다.
 */
@Entity
@Table(name = "collection_search_contexts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionSearchContext {

	@Id
	@Column(name = "request_id", length = 128)
	private String requestId;

	@Column(nullable = false, length = 64)
	private String merchant;

	@Column(name = "search_query", nullable = false, length = 200)
	private String searchQuery;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> filters;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	@Column(name = "collector_version", nullable = false, length = 100)
	private String collectorVersion;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 검색 CollectorResult에서 저장할 요청 문맥을 생성한다.
	 *
	 * @param requestId 수집 요청 식별자
	 * @param merchant 판매처 식별자
	 * @param searchQuery 원본 검색어
	 * @param filters 적용된 검색 필터
	 * @param collectedAt Collector 수집 완료 시각
	 * @param collectorVersion Collector 버전
	 * @return 저장 전 검색 문맥 entity
	 */
	public static CollectionSearchContext create(
			String requestId,
			String merchant,
			String searchQuery,
			Map<String, Object> filters,
			OffsetDateTime collectedAt,
			String collectorVersion) {
		CollectionSearchContext context = new CollectionSearchContext();
		context.requestId = requestId;
		context.merchant = merchant;
		context.searchQuery = searchQuery;
		context.filters = Map.copyOf(filters);
		context.collectedAt = collectedAt;
		context.collectorVersion = collectorVersion;
		return context;
	}

	/**
	 * 같은 requestId가 기존 요청과 동일한 검색 조건을 나타내는지 검사한다.
	 *
	 * @param merchant 비교할 판매처
	 * @param searchQuery 비교할 검색어
	 * @param filters 비교할 적용 필터
	 * @return 판매처, 검색어와 필터가 모두 같으면 true
	 */
	public boolean matches(String merchant, String searchQuery, Map<String, Object> filters) {
		return this.merchant.equals(merchant)
				&& this.searchQuery.equals(searchQuery)
				&& this.filters.equals(filters);
	}
}
