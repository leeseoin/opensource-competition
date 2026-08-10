package com.purchasesearch.product_backend.collection.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.purchasesearch.product_backend.collection.dto.CollectionResultEnvelope;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectorResult.VerificationSummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CollectionTask는 RabbitMQ에 전달한 한 페이지 작업과 최종 수집 결과를 저장한다.
 */
@Entity
@Table(name = "collection_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionTask {

	@Id
	@Column(name = "task_id", length = 128)
	private String taskId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id", nullable = false)
	private CollectionJob job;

	@Column(nullable = false)
	private int page;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "product_count", nullable = false)
	private int productCount;

	@Column(name = "verification_total", nullable = false)
	private int verificationTotal;

	@Column(name = "verification_matched", nullable = false)
	private int verificationMatched;

	@Column(name = "verification_mismatched", nullable = false)
	private int verificationMismatched;

	@Column(name = "verification_failed", nullable = false)
	private int verificationFailed;

	@Column(name = "verification_missing_in_html", nullable = false)
	private int verificationMissingInHtml;

	@Column(name = "verification_missing_in_json", nullable = false)
	private int verificationMissingInJson;

	@Column(name = "verification_pending", nullable = false)
	private int verificationPending;

	@Column(name = "requested_at", nullable = false)
	private OffsetDateTime requestedAt;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "error_code", length = 100)
	private String errorCode;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "error_retryable")
	private Boolean errorRetryable;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/**
	 * 발행 전 Queue 메시지를 추적 가능한 QUEUED 작업으로 변환한다.
	 *
	 * @param job 상위 수집 job
	 * @param message 실제 RabbitMQ에 보낼 작업
	 * @return 저장 전 페이지 작업
	 */
	public static CollectionTask queued(CollectionJob job, CollectionTaskMessage message) {
		CollectionTask task = new CollectionTask();
		task.taskId = message.taskId();
		task.job = job;
		task.page = message.payload().page();
		task.status = "QUEUED";
		task.requestedAt = message.requestedAt();
		return task;
	}

	/**
	 * Worker가 작업을 소비한 시작 상태를 기록한다.
	 * 이미 종료된 작업에 늦게 도착한 시작 이벤트는 상태를 되돌리지 않는다.
	 *
	 * @param envelope 시작 시각과 작업 식별자가 포함된 running 봉투
	 * @return QUEUED에서 RUNNING으로 전환했으면 true
	 */
	public boolean start(CollectionResultEnvelope envelope) {
		if (isTerminal() || "RUNNING".equals(status)) {
			return false;
		}
		this.status = "RUNNING";
		this.startedAt = envelope.startedAt();
		return true;
	}

	/**
	 * 성공 또는 부분 성공 Queue 결과와 검증 집계를 작업에 기록한다.
	 *
	 * @param envelope Python/Go Worker 결과 봉투
	 * @param productCount DB에 처리한 상품 수
	 */
	public void complete(CollectionResultEnvelope envelope, int productCount) {
		this.status = envelope.status().toUpperCase(java.util.Locale.ROOT);
		this.productCount = productCount;
		this.startedAt = envelope.startedAt();
		this.completedAt = envelope.completedAt();
		this.durationMs = envelope.durationMs();
		this.errorCode = null;
		this.errorMessage = null;
		this.errorRetryable = null;
		VerificationSummary summary = envelope.collectorResult().verificationSummary();
		if (summary != null) {
			this.verificationTotal = summary.total();
			this.verificationMatched = summary.matched();
			this.verificationMismatched = summary.mismatched();
			this.verificationFailed = summary.failed();
			this.verificationMissingInHtml = summary.missingInHtml();
			this.verificationMissingInJson = summary.missingInJson();
			this.verificationPending = summary.pending();
		}
	}

	/**
	 * Python/Go Worker가 보낸 정상 실패 결과를 작업에 기록한다.
	 *
	 * @param envelope 오류 정보가 포함된 결과 봉투
	 */
	public void fail(CollectionResultEnvelope envelope) {
		this.status = "FAILED";
		this.startedAt = envelope.startedAt();
		this.completedAt = envelope.completedAt();
		this.durationMs = envelope.durationMs();
		this.errorCode = envelope.error().code();
		this.errorMessage = envelope.error().message();
		this.errorRetryable = envelope.error().retryable();
	}

	/**
	 * RabbitMQ 발행 실패 또는 미발행 작업을 종료 상태로 기록한다.
	 *
	 * @param code 발행 실패 코드
	 * @param message 사람이 확인할 오류 설명
	 * @param failedAt 실패 확인 시각
	 */
	public void failPublishing(String code, String message, OffsetDateTime failedAt) {
		this.status = "PUBLISH_FAILED";
		this.completedAt = failedAt;
		this.errorCode = code;
		this.errorMessage = message;
		this.errorRetryable = true;
	}

	/**
	 * 작업이 결과 또는 발행 실패로 종료됐는지 반환한다.
	 *
	 * @return 더 이상 결과를 기다리지 않는 상태이면 true
	 */
	public boolean isTerminal() {
		return "SUCCESS".equals(status)
				|| "PARTIAL".equals(status)
				|| "FAILED".equals(status)
				|| "PUBLISH_FAILED".equals(status);
	}
}
