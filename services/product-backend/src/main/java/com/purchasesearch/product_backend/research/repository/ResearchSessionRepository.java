package com.purchasesearch.product_backend.research.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.purchasesearch.product_backend.research.entity.ResearchSession;

/** ResearchSessionRepository는 조사 세션을 PostgreSQL에 저장하고 조회한다. */
public interface ResearchSessionRepository extends JpaRepository<ResearchSession, UUID> {
	/** 같은 조사 세션에서 중복 Agent Run이 시작되지 않도록 행 잠금으로 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from ResearchSession session where session.id = :sessionId")
	java.util.Optional<ResearchSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}
