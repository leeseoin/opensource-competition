package com.purchasesearch.product_backend.evidence.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse;
import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse.BatchStatus;
import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse.Item;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;

/**
 * BulkVerificationBatchTracker는 대량 재검증 배치의 진행 상태를 메모리에 보관한다.
 * 서버가 재시작하면 진행 중이던 배치 기록 자체는 사라지지만, 이미 접수된 재검증
 * 요청(Queue 발행과 OfferVerificationRequest 저장)은 durable하게 남아있다 — 이
 * 트래커는 관리자 화면이 배치 진행률을 보여주기 위한 보조 상태일 뿐이다.
 */
@Component
public class BulkVerificationBatchTracker {

	private final Map<UUID, Batch> batches = new ConcurrentHashMap<>();

	/**
	 * @param requestedCount 배치에 포함된 상품 수
	 * @return 새로 만든 배치 ID
	 */
	public UUID create(int requestedCount) {
		UUID batchId = UUID.randomUUID();
		batches.put(batchId, new Batch(requestedCount));
		return batchId;
	}

	/**
	 * 상품 하나의 성공 결과를 배치에 기록한다.
	 *
	 * @param batchId 대상 배치 ID
	 * @param productId 처리한 판매처 상품 ID
	 * @param verification 접수된 재검증 요청
	 */
	public void recordSuccess(UUID batchId, long productId, OfferVerificationResponse verification) {
		record(batchId, new Item(productId, true, verification, null));
	}

	/**
	 * 상품 하나의 실패 사유를 배치에 기록한다.
	 *
	 * @param batchId 대상 배치 ID
	 * @param productId 처리한 판매처 상품 ID
	 * @param error 사람이 읽을 수 있는 실패 사유
	 */
	public void recordFailure(UUID batchId, long productId, String error) {
		record(batchId, new Item(productId, false, null, error));
	}

	private void record(UUID batchId, Item item) {
		Batch batch = batches.get(batchId);
		if (batch != null) {
			batch.results.add(item);
		}
	}

	/** @param batchId 완료 처리할 배치 ID */
	public void complete(UUID batchId) {
		Batch batch = batches.get(batchId);
		if (batch != null) {
			batch.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
		}
	}

	/**
	 * @param batchId 조회할 배치 ID
	 * @return 현재 진행 상태이며 존재하지 않으면 empty
	 */
	public Optional<BulkVerificationBatchResponse> get(UUID batchId) {
		Batch batch = batches.get(batchId);
		if (batch == null) {
			return Optional.empty();
		}
		List<Item> results = List.copyOf(batch.results);
		int queued = (int) results.stream().filter(Item::success).count();
		BatchStatus status = batch.completedAt == null ? BatchStatus.PROCESSING : BatchStatus.COMPLETED;
		return Optional.of(new BulkVerificationBatchResponse(
				batchId, status, batch.requestedCount, results.size(), queued, results,
				batch.startedAt, batch.completedAt));
	}

	/** Batch는 배치 하나의 접수 시각과 지금까지 처리된 결과를 스레드 안전하게 보관한다. */
	private static final class Batch {
		private final int requestedCount;
		private final OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
		private final List<Item> results = new CopyOnWriteArrayList<>();
		private volatile OffsetDateTime completedAt;

		private Batch(int requestedCount) {
			this.requestedCount = requestedCount;
		}
	}
}
