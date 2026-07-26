package com.purchaseresearch.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchaseresearch.backend.domain.PriceHistory;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
}
