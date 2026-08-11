package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CollectionJobListResponse는 요청 이력 화면이 쓸 수집 job 목록과 요청별 성공률/상품 수를 반환한다.
 *
 * @param totalCount 필터 조건과 일치하는 전체 job 수
 * @param hasNext 현재 응답 뒤에 다음 job이 있는지 여부
 * @param items 최신 요청순 job 요약 목록
 */
public record CollectionJobListResponse(
		long totalCount,
		boolean hasNext,
		List<CollectionJobSummary> items) {

	/**
	 * CollectionJobSummary는 한 번의 수집 요청과 그 페이지 작업들의 성공률/상품 수를 요약한다.
	 *
	 * @param jobId 수집 요청 묶음 식별자
	 * @param merchant 판매처 식별자
	 * @param query 검색어
	 * @param status 전체 작업 상태
	 * @param taskCount 전체 페이지 작업 수
	 * @param succeededTaskCount 성공한 페이지 작업 수
	 * @param failedTaskCount 실행 또는 발행에 실패한 페이지 작업 수
	 * @param successRate succeededTaskCount / taskCount이며 taskCount가 0이면 null
	 * @param productCount DB에 처리한 상품 수 합계
	 * @param requestedAt job 요청 시각
	 * @param completedAt 모든 작업 종료 시각
	 */
	public record CollectionJobSummary(
			String jobId,
			String merchant,
			String query,
			String status,
			int taskCount,
			long succeededTaskCount,
			long failedTaskCount,
			Double successRate,
			long productCount,
			OffsetDateTime requestedAt,
			OffsetDateTime completedAt) {
	}
}
