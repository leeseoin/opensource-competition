package com.purchasesearch.product_backend.agentrun.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.purchasesearch.product_backend.agentrun.entity.AgentRun;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunEvent;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.research.dto.ResearchSessionResponse;

/**
 * AgentRunResponse는 구매 조사 실행의 현재 상태와 사람이 확인할 단계별 근거를 반환한다.
 *
 * @param runId 실행 ID
 * @param sessionId 조사 세션 ID
 * @param status 현재 실행 상태
 * @param research 준비된 후보를 포함한 조사 세션 또는 null
 * @param collectionJobs 연결된 판매처 수집 작업
 * @param verification 선택 상품 재검증 상태 또는 null
 * @param events 순서가 보장된 실행 사건
 * @param error 실패 정보 또는 null
 * @param createdAt 시작 시각
 * @param updatedAt 마지막 상태 변경 시각
 * @param completedAt 종료 시각
 * @param nextAction 호출자가 수행할 다음 행동
 */
public record AgentRunResponse(
		UUID runId,
		UUID sessionId,
		AgentRunStatus status,
		ResearchSessionResponse research,
		List<CollectionJobView> collectionJobs,
		OfferVerificationResponse verification,
		List<EventView> events,
		ErrorView error,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		OffsetDateTime completedAt,
		String nextAction) {

	/** Agent Run entity와 연결 데이터를 외부 응답으로 변환한다. */
	public static AgentRunResponse from(
			AgentRun run,
			ResearchSessionResponse research,
			List<CollectionJobView> jobs,
			OfferVerificationResponse verification,
			List<EventView> events,
			String nextAction) {
		ErrorView error = run.getErrorCode() == null ? null : new ErrorView(run.getErrorCode(), run.getErrorMessage());
		return new AgentRunResponse(
				run.getRunId(), run.getResearchSession().getId(), run.getStatus(), research,
				List.copyOf(jobs), verification, List.copyOf(events), error,
				run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt(), nextAction);
	}

	/** @param sequence 실행 안의 사건 순서 @param type 사건 종류 @param status 당시 상태 @param message 설명 @param createdAt 발생 시각 */
	public record EventView(int sequence, String type, String status, String message, OffsetDateTime createdAt) {
		/** 저장 사건을 응답으로 변환한다. */
		public static EventView from(AgentRunEvent event) {
			return new EventView(event.getSequenceNo(), event.getEventType(), event.getStatus(),
					event.getMessage(), event.getCreatedAt());
		}
	}

	/** @param jobId 수집 job ID @param merchant 판매처 @param dataStatus 요청 당시 데이터 상태 @param status 현재 job 상태 @param productCount 처리 상품 수 */
	public record CollectionJobView(
			String jobId, String merchant, String dataStatus, String status, int productCount) {
		/** 연결 정보와 현재 job 집계를 응답으로 변환한다. */
		public static CollectionJobView from(String merchant, String dataStatus, CollectionJobResponse job) {
			return new CollectionJobView(job.jobId(), merchant, dataStatus, job.status(), job.productCount());
		}
	}

	/** @param code 안정적인 오류 코드 @param message 사용자에게 공개 가능한 설명 */
	public record ErrorView(String code, String message) {
	}
}
