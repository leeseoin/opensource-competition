package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;

import com.purchasesearch.product_backend.collection.exception.InvalidCollectionResultMessageException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CollectionResultEnvelope는 Python/Go Worker가 RabbitMQ 결과 Queue에 발행하는 시작 상태와 실행 결과 계약이다.
 *
 * @param schemaVersion Queue 계약 버전
 * @param taskId 수집 작업 식별자
 * @param jobId 상위 수집 작업 묶음 식별자
 * @param status 작업 실행 결과 상태
 * @param startedAt 작업 시작 시각
 * @param completedAt 작업 완료 시각
 * @param durationMs 작업 소요시간
 * @param collectorResult 성공 또는 부분 성공 시 저장할 Collector 결과
 * @param error 실패 시 오류 정보
 */
public record CollectionResultEnvelope(
		@NotBlank
		@Pattern(regexp = "^1$")
		String schemaVersion,
		@NotBlank
		@Size(max = 128)
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]*$")
		String taskId,
		@NotBlank
		@Size(max = 128)
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]*$")
		String jobId,
		@NotBlank
		@Pattern(regexp = "^(running|success|partial|failed)$")
		String status,
		@NotNull
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		@Min(0)
		Long durationMs,
		@Valid
		CollectorResult collectorResult,
		@Valid
		TaskError error) {

	/**
	 * 상태에 따른 CollectorResult와 error 조합 및 taskId 연결을 검사한다.
	 *
	 * @throws InvalidCollectionResultMessageException 상태별 필드 조합이나 시간 순서가 잘못된 경우
	 */
	public void validateSemantics() {
		if ("running".equals(status)) {
			if (completedAt != null || durationMs != null || collectorResult != null || error != null) {
				throw new InvalidCollectionResultMessageException(
						"running 상태에는 시작 시각과 식별자만 필요합니다.");
			}
			return;
		}
		if (completedAt == null || durationMs == null) {
			throw new InvalidCollectionResultMessageException(
					"최종 결과에는 completedAt과 durationMs가 필요합니다.");
		}
		if (completedAt.isBefore(startedAt)) {
			throw new InvalidCollectionResultMessageException("completedAt은 startedAt보다 빠를 수 없습니다.");
		}
		if ("success".equals(status) || "partial".equals(status)) {
			if (collectorResult == null || error != null) {
				throw new InvalidCollectionResultMessageException(
						"success 또는 partial 결과에는 collectorResult만 필요합니다.");
			}
			if (!taskId.equals(collectorResult.requestId())) {
				throw new InvalidCollectionResultMessageException(
						"taskId와 collectorResult.requestId가 일치해야 합니다.");
			}
			return;
		}
		if ("failed".equals(status) && (error == null || collectorResult != null)) {
			throw new InvalidCollectionResultMessageException("failed 결과에는 error만 필요합니다.");
		}
	}

	/**
	 * TaskError는 수집 작업 실패 코드와 재시도 가능 여부를 표현한다.
	 *
	 * @param code 대문자 오류 코드
	 * @param message 식별정보를 포함하지 않는 오류 설명
	 * @param retryable 동일 작업을 다시 시도할 수 있는지 여부
	 */
	public record TaskError(
			@NotBlank
			@Size(max = 100)
			@Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
			String code,
			@NotBlank
			@Size(max = 1000)
			String message,
			@NotNull
			Boolean retryable) {
	}
}
