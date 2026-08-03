package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;

/**
 * CollectionTaskResponse는 RabbitMQ가 접수한 수집 작업의 추적 정보를 반환한다.
 *
 * @param taskId 실행 단위 식별자
 * @param jobId 사용자 요청 단위 식별자
 * @param status 현재 접수 상태
 * @param merchant 판매처 식별자
 * @param operation 작업 종류
 * @param requestedAt 수집 요청 시각
 */
public record CollectionTaskResponse(
		String taskId,
		String jobId,
		String status,
		String merchant,
		String operation,
		OffsetDateTime requestedAt) {

	/**
	 * 발행된 Queue 메시지를 HTTP 접수 응답으로 변환한다.
	 *
	 * @param task RabbitMQ에 발행한 작업
	 * @return QUEUED 상태의 추적 응답
	 */
	public static CollectionTaskResponse queued(CollectionTaskMessage task) {
		return new CollectionTaskResponse(
				task.taskId(),
				task.jobId(),
				"QUEUED",
				task.merchant(),
				task.operation(),
				task.requestedAt());
	}

	/**
	 * ErrorResponse는 작업 요청 또는 발행 실패의 안전한 오류 내용을 반환한다.
	 *
	 * @param code 오류 코드
	 * @param message 사용자 확인용 오류 설명
	 */
	public record ErrorResponse(String code, String message) {
	}
}
