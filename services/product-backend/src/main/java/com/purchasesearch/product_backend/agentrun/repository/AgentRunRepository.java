package com.purchasesearch.product_backend.agentrun.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.purchasesearch.product_backend.agentrun.entity.AgentRun;

/** AgentRunRepository는 구매 조사 실행의 현재 상태를 PostgreSQL에 저장한다. */
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
	/** @return 같은 조사 세션에서 이미 시작한 실행 */
	Optional<AgentRun> findByResearchSessionId(UUID researchSessionId);

	/** 동시 진행 요청이 중복 상태 전이를 만들지 않도록 실행을 행 잠금으로 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from AgentRun run where run.runId = :runId")
	Optional<AgentRun> findByIdForUpdate(@Param("runId") UUID runId);
}
