package com.purchasesearch.product_backend.research.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.purchasesearch.product_backend.research.dto.PurchaseCondition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ResearchSession은 AI가 정리한 구매 조건과 사용자의 확인 상태를 저장한다. */
@Entity
@Table(name = "research_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchSession {

	@Id
	private UUID id;

	@Column(nullable = false, length = 1000)
	private String question;

	@Column(nullable = false, length = 32)
	private String runtime;

	@Column(name = "plugin_id", nullable = false, length = 128)
	private String pluginId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ResearchSessionStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private PurchaseCondition conditions;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "confirmed_at")
	private OffsetDateTime confirmedAt;

	/**
	 * 확인 전 조사 세션을 생성한다.
	 *
	 * @param question 사용자 원문 질문
	 * @param runtime AI 실행 환경
	 * @param pluginId 적용한 Plugin 식별자
	 * @param conditions AI가 구조화한 조건
	 * @return DRAFT 상태의 조사 세션
	 */
	public static ResearchSession draft(
			String question,
			String runtime,
			String pluginId,
			PurchaseCondition conditions) {
		ResearchSession session = new ResearchSession();
		session.id = UUID.randomUUID();
		session.question = question;
		session.runtime = runtime;
		session.pluginId = pluginId;
		session.status = ResearchSessionStatus.DRAFT;
		session.conditions = conditions;
		return session;
	}

	/**
	 * 사용자가 수정하거나 확인한 조건을 확정한다.
	 *
	 * @param confirmedConditions 확정할 구매 조건
	 */
	public void confirm(PurchaseCondition confirmedConditions) {
		this.conditions = confirmedConditions;
		this.status = ResearchSessionStatus.CONFIRMED;
		this.confirmedAt = OffsetDateTime.now();
	}
}
