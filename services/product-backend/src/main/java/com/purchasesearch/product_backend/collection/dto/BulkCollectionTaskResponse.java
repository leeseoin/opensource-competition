package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * BulkCollectionTaskResponse는 여러 페이지 수집 작업의 공통 추적 정보와 발행 범위를 반환한다.
 *
 * @param jobId 전체 페이지를 묶는 사용자 요청 단위 식별자
 * @param status 현재 접수 상태
 * @param merchant 판매처 식별자
 * @param operation 작업 종류
 * @param startPage 첫 번째 검색 페이지
 * @param endPage 마지막 검색 페이지
 * @param taskCount job에 등록된 작업 수(접수 기준). 실제 RabbitMQ 발행은 배경 스레드에서
 *     비동기로 진행되므로, 발행 성공/실패 여부는 jobId로 job 상태를 조회해 확인해야 한다
 * @param requestedAt 수집 요청 시각
 * @param skippedDuplicatePages 동일 조건 작업이 이미 진행 중이어서 발행을 건너뛴 페이지 목록
 */
public record BulkCollectionTaskResponse(
		String jobId,
		String status,
		String merchant,
		String operation,
		int startPage,
		int endPage,
		int taskCount,
		OffsetDateTime requestedAt,
		List<Integer> skippedDuplicatePages) {
}
