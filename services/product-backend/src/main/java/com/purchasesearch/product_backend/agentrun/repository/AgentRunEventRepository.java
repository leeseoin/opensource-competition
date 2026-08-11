package com.purchasesearch.product_backend.agentrun.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.agentrun.entity.AgentRunEvent;

/** AgentRunEventRepository는 실행 사건을 순서대로 저장하고 조회한다. */
public interface AgentRunEventRepository extends JpaRepository<AgentRunEvent, Long> {
	/** @return 실행 ID의 사건을 발생 순서대로 반환한다. */
	List<AgentRunEvent> findAllByRunRunIdOrderBySequenceNoAsc(UUID runId);

	/** @return 다음 사건 번호 계산에 사용할 현재 사건 수 */
	long countByRunRunId(UUID runId);
}
