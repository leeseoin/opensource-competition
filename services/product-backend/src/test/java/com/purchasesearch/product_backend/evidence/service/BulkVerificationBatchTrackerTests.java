package com.purchasesearch.product_backend.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse.BatchStatus;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.VerificationStatus;

/** BulkVerificationBatchTrackerTests는 배치 진행 상태 기록과 조회를 검증한다. */
class BulkVerificationBatchTrackerTests {

	private final BulkVerificationBatchTracker tracker = new BulkVerificationBatchTracker();

	/** 갓 만든 배치는 처리 건수 0, PROCESSING 상태로 조회되는지 검증한다. */
	@Test
	void createdBatchStartsAsProcessingWithZeroProgress() {
		UUID batchId = tracker.create(3);

		var response = tracker.get(batchId).orElseThrow();

		assertThat(response.status()).isEqualTo(BatchStatus.PROCESSING);
		assertThat(response.requestedCount()).isEqualTo(3);
		assertThat(response.processedCount()).isZero();
		assertThat(response.queuedCount()).isZero();
		assertThat(response.completedAt()).isNull();
	}

	/** 성공과 실패가 섞여 기록되면 processedCount와 queuedCount가 각각 정확히 반영되는지 검증한다. */
	@Test
	void tracksMixedSuccessAndFailureResults() {
		UUID batchId = tracker.create(2);

		tracker.recordSuccess(batchId, 11L, verification(11L));
		tracker.recordFailure(batchId, 22L, "상품을 찾을 수 없습니다.");

		var response = tracker.get(batchId).orElseThrow();

		assertThat(response.processedCount()).isEqualTo(2);
		assertThat(response.queuedCount()).isEqualTo(1);
		assertThat(response.results()).hasSize(2);
		assertThat(response.results().get(0).success()).isTrue();
		assertThat(response.results().get(1).success()).isFalse();
		assertThat(response.results().get(1).error()).isEqualTo("상품을 찾을 수 없습니다.");
	}

	/** complete 호출 뒤에는 COMPLETED 상태와 완료 시각을 반환하는지 검증한다. */
	@Test
	void completingBatchSetsCompletedStatusAndTimestamp() {
		UUID batchId = tracker.create(1);
		tracker.recordSuccess(batchId, 11L, verification(11L));

		tracker.complete(batchId);

		var response = tracker.get(batchId).orElseThrow();
		assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(response.completedAt()).isNotNull();
	}

	/** 존재하지 않는 배치 ID는 empty를 반환하는지 검증한다. */
	@Test
	void returnsEmptyForUnknownBatchId() {
		assertThat(tracker.get(UUID.randomUUID())).isEmpty();
	}

	/** 존재하지 않는 배치에 결과를 기록해도 예외 없이 무시하는지 검증한다. */
	@Test
	void ignoresResultsForUnknownBatchId() {
		UUID unknownBatchId = UUID.randomUUID();

		tracker.recordSuccess(unknownBatchId, 11L, verification(11L));
		tracker.complete(unknownBatchId);

		assertThat(tracker.get(unknownBatchId)).isEmpty();
	}

	private OfferVerificationResponse verification(long productId) {
		return new OfferVerificationResponse(
				UUID.randomUUID(), productId, "job-1", VerificationStatus.QUEUED, "PRIORITY_SEARCH_REFRESH",
				new OfferVerificationResponse.SnapshotView(69_000L, "KRW", "available", OffsetDateTime.now()),
				null, java.util.List.of());
	}
}
