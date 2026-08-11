package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.purchasesearch.product_backend.collection.entity.CollectionJob;
import com.purchasesearch.product_backend.collection.entity.CollectionTask;

/**
 * CollectionJobResponse는 수집 job의 전체 진행률, 상품 수와 JSON/HTML 검증 집계를 반환한다.
 *
 * @param jobId 수집 요청 묶음 식별자
 * @param status 전체 작업 상태
 * @param merchant 판매처 식별자
 * @param operation 수집 작업 종류
 * @param query 검색어
 * @param startPage 첫 검색 페이지
 * @param endPage 마지막 검색 페이지
 * @param taskCount 전체 페이지 작업 수
 * @param queuedTaskCount 결과를 기다리는 작업 수
 * @param runningTaskCount Worker가 실행 중인 작업 수
 * @param succeededTaskCount 성공한 작업 수
 * @param partialTaskCount 부분 성공한 작업 수
 * @param failedTaskCount 실행 또는 발행에 실패한 작업 수
 * @param productCount DB에 처리한 상품 수
 * @param verificationSummary 전체 JSON/HTML 검증 집계
 * @param requestedAt job 요청 시각
 * @param completedAt 모든 작업 종료 시각
 * @param tasks 페이지별 상세 상태
 */
public record CollectionJobResponse(
		String jobId,
		String status,
		String merchant,
		String operation,
		String query,
		int startPage,
		int endPage,
		int taskCount,
		long queuedTaskCount,
		long runningTaskCount,
		long succeededTaskCount,
		long partialTaskCount,
		long failedTaskCount,
		int productCount,
		VerificationSummary verificationSummary,
		OffsetDateTime requestedAt,
		OffsetDateTime completedAt,
		List<TaskStatus> tasks) {

	/**
	 * 저장된 job과 작업 목록을 API 응답으로 집계한다.
	 *
	 * @param job 상위 수집 job
	 * @param taskEntities 페이지 순서의 작업 목록
	 * @return 사용자와 Swagger에서 확인할 job 상태
	 */
	public static CollectionJobResponse from(CollectionJob job, List<CollectionTask> taskEntities) {
		long queued = taskEntities.stream().filter(task -> "QUEUED".equals(task.getStatus())).count();
		long running = taskEntities.stream().filter(task -> "RUNNING".equals(task.getStatus())).count();
		long succeeded = taskEntities.stream().filter(task -> "SUCCESS".equals(task.getStatus())).count();
		long partial = taskEntities.stream().filter(task -> "PARTIAL".equals(task.getStatus())).count();
		long failed = taskEntities.stream()
				.filter(task -> "FAILED".equals(task.getStatus()) || "PUBLISH_FAILED".equals(task.getStatus()))
				.count();
		int products = taskEntities.stream().mapToInt(CollectionTask::getProductCount).sum();
		VerificationSummary verification = VerificationSummary.from(taskEntities);
		List<TaskStatus> tasks = taskEntities.stream().map(TaskStatus::from).toList();
		return new CollectionJobResponse(
				job.getJobId(),
				job.getStatus(),
				job.getMerchant(),
				job.getOperation(),
				job.getSearchQuery(),
				job.getStartPage(),
				job.getEndPage(),
				job.getTotalTasks(),
				queued,
				running,
				succeeded,
				partial,
				failed,
				products,
				verification,
				job.getRequestedAt(),
				job.getCompletedAt(),
				tasks);
	}

	/**
	 * VerificationSummary는 job에 속한 모든 페이지의 JSON/HTML 검증 개수를 합산한다.
	 *
	 * @param total 검증 결과 전체 수
	 * @param matched 일치 수
	 * @param mismatched 불일치 수
	 * @param failed 검증 실행 실패 수
	 * @param missingInHtml HTML 누락 수
	 * @param missingInJson JSON 누락 수
	 * @param pending 미검증 수
	 */
	public record VerificationSummary(
			int total,
			int matched,
			int mismatched,
			int failed,
			int missingInHtml,
			int missingInJson,
			int pending) {

		/**
		 * 페이지 작업의 검증 개수를 모두 더한다.
		 *
		 * @param tasks 집계할 페이지 작업
		 * @return job 전체 검증 개수
		 */
		private static VerificationSummary from(List<CollectionTask> tasks) {
			return new VerificationSummary(
					tasks.stream().mapToInt(CollectionTask::getVerificationTotal).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationMatched).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationMismatched).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationFailed).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationMissingInHtml).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationMissingInJson).sum(),
					tasks.stream().mapToInt(CollectionTask::getVerificationPending).sum());
		}
	}

	/**
	 * TaskStatus는 한 페이지 작업의 실행 결과와 오류를 반환한다.
	 *
	 * @param taskId 페이지 작업 식별자
	 * @param page 검색 페이지
	 * @param status 작업 상태
	 * @param productCount 처리 상품 수
	 * @param startedAt Worker 시작 시각
	 * @param completedAt Worker 완료 또는 발행 실패 시각
	 * @param durationMs Worker 실행 시간
	 * @param error 실패 정보
	 */
	public record TaskStatus(
			String taskId,
			int page,
			String status,
			int productCount,
			OffsetDateTime startedAt,
			OffsetDateTime completedAt,
			Long durationMs,
			TaskError error) {

		/**
		 * 저장된 페이지 작업을 API 상세 상태로 변환한다.
		 *
		 * @param task 저장된 페이지 작업
		 * @return 조회 응답의 작업 상태
		 */
		private static TaskStatus from(CollectionTask task) {
			TaskError error = task.getErrorCode() == null
					? null
					: new TaskError(task.getErrorCode(), task.getErrorMessage(), task.getErrorRetryable());
			return new TaskStatus(
					task.getTaskId(),
					task.getPage(),
					task.getStatus(),
					task.getProductCount(),
					task.getStartedAt(),
					task.getCompletedAt(),
					task.getDurationMs(),
					error);
		}
	}

	/**
	 * TaskError는 실패한 페이지 작업의 안전한 오류 정보다.
	 *
	 * @param code 오류 코드
	 * @param message 오류 설명
	 * @param retryable 다시 실행할 수 있는지 여부
	 */
	public record TaskError(String code, String message, Boolean retryable) {
	}
}
