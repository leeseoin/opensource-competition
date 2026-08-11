package com.purchasesearch.product_backend.evidence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.evidence.entity.OfferVerificationRequest;

/** OfferVerificationRequestRepository는 재검증 기준 snapshot과 수집 job 연결을 저장한다. */
public interface OfferVerificationRequestRepository extends JpaRepository<OfferVerificationRequest, UUID> {
}
