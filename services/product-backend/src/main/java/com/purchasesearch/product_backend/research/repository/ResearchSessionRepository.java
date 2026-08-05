package com.purchasesearch.product_backend.research.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.research.entity.ResearchSession;

/** ResearchSessionRepository는 조사 세션을 PostgreSQL에 저장하고 조회한다. */
public interface ResearchSessionRepository extends JpaRepository<ResearchSession, UUID> {
}
