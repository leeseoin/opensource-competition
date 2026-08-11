package com.purchasesearch.product_backend.agentrun.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AgentRunCollectionJob은 한 구매 조사 실행이 요청한 판매처별 수집 job을 연결한다. */
@Entity
@Table(name = "agent_run_collection_jobs")
@IdClass(AgentRunCollectionJob.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunCollectionJob {

	@Id
	@Column(name = "run_id", insertable = false, updatable = false)
	private UUID runId;

	@Id
	@Column(name = "job_id", length = 128)
	private String jobId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private AgentRun run;

	@Column(name = "merchant", nullable = false, length = 64)
	private String merchant;

	@Column(name = "data_status", nullable = false, length = 32)
	private String dataStatus;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** 판매처 수집 요청을 현재 실행에 연결한다. */
	public static AgentRunCollectionJob create(AgentRun run, String jobId, String merchant, String dataStatus) {
		AgentRunCollectionJob job = new AgentRunCollectionJob();
		job.run = run;
		job.runId = run.getRunId();
		job.jobId = jobId;
		job.merchant = merchant;
		job.dataStatus = dataStatus;
		return job;
	}

	/** AgentRunCollectionJob 복합 식별자다. */
	@NoArgsConstructor
	public static class Key implements Serializable {
		private UUID runId;
		private String jobId;

		/** 복합 식별자의 값 동등성을 비교한다. */
		@Override
		public boolean equals(Object object) {
			if (this == object) return true;
			if (!(object instanceof Key key)) return false;
			return Objects.equals(runId, key.runId) && Objects.equals(jobId, key.jobId);
		}

		/** 복합 식별자를 안정적으로 hash한다. */
		@Override
		public int hashCode() {
			return Objects.hash(runId, jobId);
		}
	}
}
