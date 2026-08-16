package com.purchasesearch.product_backend.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse.BatchStatus;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.VerificationStatus;

/**
 * BulkOfferVerificationRunnerTests는 대량 재검증 배경 처리 루프가 상품별 성공/실패를
 * 배치에 정확히 기록하고, 한 상품이 실패해도 나머지를 계속 처리하는지 검증한다.
 * {@code @Async}는 Spring 프록시를 거칠 때만 배경 스레드로 실행되므로, 이 테스트처럼
 * 객체를 직접 호출하면 동기적으로 실행돼 순서를 그대로 검증할 수 있다.
 */
class BulkOfferVerificationRunnerTests {

	private final OfferVerificationService offerVerificationService = mock(OfferVerificationService.class);
	private final BulkVerificationBatchTracker batchTracker = new BulkVerificationBatchTracker();
	private final BulkOfferVerificationRunner runner =
			new BulkOfferVerificationRunner(offerVerificationService, batchTracker);

	/** 전부 성공하면 배치가 COMPLETED로 끝나고 모든 결과가 성공으로 기록되는지 검증한다. */
	@Test
	void recordsSuccessForEveryProductAndCompletesBatch() {
		when(offerVerificationService.request(11L)).thenReturn(verification(11L));
		when(offerVerificationService.request(22L)).thenReturn(verification(22L));
		UUID batchId = batchTracker.create(2);

		runner.runAsync(batchId, List.of(11L, 22L));

		var response = batchTracker.get(batchId).orElseThrow();
		assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(response.processedCount()).isEqualTo(2);
		assertThat(response.queuedCount()).isEqualTo(2);
	}

	/** 존재하지 않는 상품(404)이 섞여 있어도 나머지 상품은 계속 처리하는지 검증한다. */
	@Test
	void continuesRemainingProductsWhenOneIsNotFound() {
		when(offerVerificationService.request(eq(11L)))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
		when(offerVerificationService.request(eq(22L))).thenReturn(verification(22L));
		UUID batchId = batchTracker.create(2);

		runner.runAsync(batchId, List.of(11L, 22L));

		var response = batchTracker.get(batchId).orElseThrow();
		assertThat(response.queuedCount()).isEqualTo(1);
		assertThat(response.results().get(0).success()).isFalse();
		assertThat(response.results().get(0).error()).isEqualTo("상품을 찾을 수 없습니다.");
		assertThat(response.results().get(1).success()).isTrue();
	}

	/** Queue 발행이 실패한 상품이 있어도 나머지 상품은 계속 처리하는지 검증한다. */
	@Test
	void continuesRemainingProductsWhenPublishFails() {
		when(offerVerificationService.request(eq(11L)))
				.thenThrow(new CollectionTaskPublishException("broker timeout"));
		when(offerVerificationService.request(eq(22L))).thenReturn(verification(22L));
		UUID batchId = batchTracker.create(2);

		runner.runAsync(batchId, List.of(11L, 22L));

		var response = batchTracker.get(batchId).orElseThrow();
		assertThat(response.queuedCount()).isEqualTo(1);
		assertThat(response.results().get(0).error()).isEqualTo("broker timeout");
	}

	/** 빈 목록도 즉시 COMPLETED로 끝나는지 검증한다. */
	@Test
	void completesImmediatelyForEmptyProductList() {
		UUID batchId = batchTracker.create(0);

		runner.runAsync(batchId, List.of());

		var response = batchTracker.get(batchId).orElseThrow();
		assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(response.processedCount()).isZero();
	}

	private OfferVerificationResponse verification(long productId) {
		return new OfferVerificationResponse(
				UUID.randomUUID(), productId, "job-1", VerificationStatus.QUEUED, "PRIORITY_SEARCH_REFRESH",
				new OfferVerificationResponse.SnapshotView(69_000L, "KRW", "available", OffsetDateTime.now()),
				null, List.of());
	}
}
