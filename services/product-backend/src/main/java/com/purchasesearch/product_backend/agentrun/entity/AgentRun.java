package com.purchasesearch.product_backend.agentrun.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.purchasesearch.product_backend.research.entity.ResearchSession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AgentRun은 확인된 조사 세션의 검색부터 재검증까지 현재 상태를 영구 저장한다. */
@Entity
@Table(name = "agent_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRun {

	@Id
	@GeneratedValue
	@Column(name = "run_id")
	private UUID runId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "research_session_id", nullable = false)
	private ResearchSession researchSession;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private AgentRunStatus status;

	@Column(name = "verification_id")
	private UUID verificationId;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	/**
	 * @param researchSession 사용자가 확인한 조사 세션
	 * @return SEARCHING 상태의 새 실행
	 */
	public static AgentRun start(ResearchSession researchSession) {
		AgentRun run = new AgentRun();
		run.researchSession = researchSession;
		run.status = AgentRunStatus.SEARCHING;
		return run;
	}

	/**
	 * 실행 상태를 바꾸고 종료 상태의 완료 시각을 기록한다.
	 *
	 * @param next 다음 상태
	 */
	public void transitionTo(AgentRunStatus next) {
		this.status = next;
		if (next.isTerminal()) {
			this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
		}
	}

	/** @param verificationId 선택 상품 재검증 요청 ID */
	public void beginVerification(UUID verificationId) {
		this.verificationId = verificationId;
		this.status = AgentRunStatus.VERIFYING;
	}

	/** 안전하게 공개할 오류를 기록하고 실행을 실패 상태로 종료한다. */
	public void fail(String code, String message) {
		this.errorCode = code;
		this.errorMessage = message;
		transitionTo(AgentRunStatus.FAILED);
	}
}
