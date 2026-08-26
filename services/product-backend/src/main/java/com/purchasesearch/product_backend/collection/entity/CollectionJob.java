package com.purchasesearch.product_backend.collection.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.purchasesearch.product_backend.collection.dto.CollectionJobRequestSnapshot;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CollectionJob은 한 번의 사용자 수집 요청과 그 안의 페이지 작업 전체 상태를 저장한다.
 */
@Entity
@Table(name = "collection_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionJob {

	@Id
	@Column(name = "job_id", length = 128)
	private String jobId;

	@Column(nullable = false, length = 64)
	private String merchant;

	@Column(nullable = false, length = 32)
	private String operation;

	@Column(name = "search_query", nullable = false, length = 200)
	private String searchQuery;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "total_tasks", nullable = false)
	private int totalTasks;

	@Column(name = "start_page", nullable = false)
	private int startPage;

	@Column(name = "end_page", nullable = false)
	private int endPage;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "request_snapshot", nullable = false, columnDefinition = "jsonb")
	private CollectionJobRequestSnapshot requestSnapshot;

	@Column(name = "requested_at", nullable = false)
	private OffsetDateTime requestedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/**
	 * 첫 Queue 작업과 페이지 범위로 추적할 수집 job을 생성한다.
	 *
	 * @param firstTask 동일 job의 첫 작업
	 * @param totalTasks job에 속한 전체 페이지 작업 수
	 * @param startPage 첫 검색 페이지
	 * @param endPage 마지막 검색 페이지
	 * @return QUEUED 상태의 저장 전 job
	 */
	public static CollectionJob queued(
			CollectionTaskMessage firstTask,
			int totalTasks,
			int startPage,
			int endPage) {
		CollectionJob job = new CollectionJob();
		job.jobId = firstTask.jobId();
		job.merchant = firstTask.merchant();
		job.operation = firstTask.operation();
		job.searchQuery = firstTask.payload().query();
		job.status = "QUEUED";
		job.totalTasks = totalTasks;
		job.startPage = startPage;
		job.endPage = endPage;
		job.requestSnapshot = CollectionJobRequestSnapshot.from(firstTask);
		job.requestedAt = firstTask.requestedAt();
		return job;
	}

	/**
	 * 하위 작업 집계에 맞춰 job 상태와 완료 시각을 갱신한다.
	 *
	 * @param status 계산된 job 상태
	 * @param completedAt 모든 작업이 끝났을 때의 완료 시각이며 진행 중이면 null
	 */
	public void updateStatus(String status, OffsetDateTime completedAt) {
		this.status = status;
		this.completedAt = completedAt;
	}
}
