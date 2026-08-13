package com.purchasesearch.product_backend.collection.dto;

/**
 * CollectionJobRequestSnapshot은 job 등록 당시 원본 검색 요청 중 merchant/query/페이지범위 밖의
 * 조건(limit, locale, currency, priority, maxAttempts, filters)을 재실행을 위해 그대로 보존한다.
 *
 * @param limit 페이지당 상품 최대 개수
 * @param locale 검색 지역 형식
 * @param currency 통화 코드
 * @param priority RabbitMQ 작업 우선순위
 * @param maxAttempts 최초 실행을 포함한 최대 시도 횟수
 * @param filters 가격, 카테고리, 사이즈, 색상 및 재고 필터
 */
public record CollectionJobRequestSnapshot(
		Integer limit,
		String locale,
		String currency,
		Integer priority,
		Integer maxAttempts,
		CollectionTaskMessage.SearchFilters filters) {

	private static final CollectionJobRequestSnapshot EMPTY =
			new CollectionJobRequestSnapshot(null, null, null, null, null, null);

	/**
	 * 등록 전 첫 Queue 작업에서 재실행에 필요한 조건만 뽑아낸다.
	 *
	 * @param firstTask 동일 job의 첫 작업
	 * @return job에 함께 저장할 요청 스냅샷
	 */
	public static CollectionJobRequestSnapshot from(CollectionTaskMessage firstTask) {
		CollectionTaskMessage.SearchPayload payload = firstTask.payload();
		return new CollectionJobRequestSnapshot(
				payload.limit(),
				payload.locale(),
				payload.currency(),
				firstTask.priority(),
				firstTask.maxAttempts(),
				payload.filters());
	}

	/**
	 * 컬럼 기본값('{}') 또는 구버전 job처럼 스냅샷이 비어 있을 때 쓸 빈 값을 돌려준다.
	 *
	 * @return 모든 필드가 null인 스냅샷
	 */
	public static CollectionJobRequestSnapshot empty() {
		return EMPTY;
	}

	/**
	 * Queue 계약 형태로 저장한 filters를 재발행 요청 DTO가 받는 형태로 되돌린다.
	 *
	 * @return 재발행 요청에 그대로 넣을 수 있는 filters이며 저장된 filters가 없으면 null
	 */
	public CollectionTaskRequest.SearchFilters toRequestFilters() {
		if (filters == null) {
			return null;
		}
		return new CollectionTaskRequest.SearchFilters(
				filters.priceMin(),
				filters.priceMax(),
				filters.categories(),
				filters.sizes(),
				filters.colors(),
				filters.inStockOnly(),
				filters.attributes());
	}
}
