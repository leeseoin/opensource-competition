package com.purchasesearch.product_backend.agentrun.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.agentrun.entity.AgentRunCollectionJob;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunCollectionJob.Key;

/** AgentRunCollectionJobRepository는 실행과 수집 job의 연결을 관리한다. */
public interface AgentRunCollectionJobRepository extends JpaRepository<AgentRunCollectionJob, Key> {
	/** @return 실행에 연결된 판매처별 job 목록 */
	List<AgentRunCollectionJob> findAllByRunRunIdOrderByCreatedAtAsc(UUID runId);
}
