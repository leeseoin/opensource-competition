package com.purchasesearch.product_backend.evidence.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;

/**
 * BulkOfferVerificationRunner는 대량 재검증 배치를 배경 스레드에서 순차 처리한다.
 * 상품 하나마다 실제 Queue 발행(수 초 이내 broker confirm 대기 포함)이 있어 수천 건이면
 * 처리에 수 분이 걸릴 수 있다 — 컨트롤러가 이 메서드를 호출만 하고 바로 응답하도록
 * 접수와 처리를 분리했다. {@code @Async}가 실제로 배경 스레드에서 실행되려면
 * 프록시를 통한 외부 호출이어야 하므로, 배치를 만드는 호출자(컨트롤러)가 이 메서드를
 * 직접 호출하고 이 클래스 스스로는 자신을 호출하지 않는다(self-invocation 방지).
 */
@Component
public class BulkOfferVerificationRunner {

	private static final Logger log = LoggerFactory.getLogger(BulkOfferVerificationRunner.class);

	private final OfferVerificationService offerVerificationService;
	private final BulkVerificationBatchTracker batchTracker;

	/**
	 * @param offerVerificationService 상품 하나 재검증 요청 서비스
	 * @param batchTracker 배치 진행 상태 기록소
	 */
	public BulkOfferVerificationRunner(
			OfferVerificationService offerVerificationService,
			BulkVerificationBatchTracker batchTracker) {
		this.offerVerificationService = offerVerificationService;
		this.batchTracker = batchTracker;
	}

	/**
	 * 상품 목록을 순서대로 재검증 요청하고 결과를 배치에 기록한다. 한 상품이 없거나
	 * Queue 발행이 실패해도 나머지 상품 처리를 막지 않는다.
	 *
	 * @param batchId 진행 상태를 기록할 배치 ID
	 * @param productIds 재검증할 판매처 상품 ID 목록
	 */
	@Async("bulkVerificationExecutor")
	public void runAsync(UUID batchId, List<Long> productIds) {
		for (Long productId : productIds) {
			try {
				OfferVerificationResponse verification = offerVerificationService.request(productId);
				batchTracker.recordSuccess(batchId, productId, verification);
			} catch (ResponseStatusException exception) {
				batchTracker.recordFailure(batchId, productId, exception.getReason());
			} catch (CollectionTaskPublishException exception) {
				log.warn("상품 {} 재검증 발행 실패: {}", productId, exception.getMessage());
				batchTracker.recordFailure(batchId, productId, exception.getMessage());
			}
		}
		batchTracker.complete(batchId);
	}
}
