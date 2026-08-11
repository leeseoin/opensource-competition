package com.purchasesearch.product_backend.agentrun.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AgentRunEvent는 사용자에게 보여줄 구매 조사 단계 사건을 순서대로 저장한다. */
@Entity
@Table(name = "agent_run_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_id")
	private Long eventId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private AgentRun run;

	@Column(name = "sequence_no", nullable = false)
	private int sequenceNo;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "message", nullable = false, length = 500)
	private String message;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * @param run 상위 구매 조사 실행
	 * @param sequenceNo 실행 안에서 증가하는 순서
	 * @param eventType 안정적인 사건 종류
	 * @param message 사용자에게 공개할 설명
	 * @return 저장 전 사건
	 */
	public static AgentRunEvent create(AgentRun run, int sequenceNo, String eventType, String message) {
		AgentRunEvent event = new AgentRunEvent();
		event.run = run;
		event.sequenceNo = sequenceNo;
		event.status = run.getStatus().name();
		event.eventType = eventType;
		event.message = message;
		return event;
	}
}
