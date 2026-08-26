package com.purchasesearch.product_backend.evidence.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BulkVerificationBatchResponse는 대량 재검증 배치의 현재 진행 상태와 지금까지 처리된
 * 상품별 결과를 담는다. 접수 직후에는 processedCount가 0인 PROCESSING 상태로 반환되고,
 * 배경 처리가 끝나면 COMPLETED로 바뀐다.
 *
 * @param batchId 배치 식별자
 * @param status 진행 또는 완료 상태
 * @param requestedCount 배치에 포함된 상품 수
 * @param processedCount 지금까지 처리(성공 또는 실패)된 상품 수
 * @param queuedCount 지금까지 Queue 발행에 성공한 상품 수
 * @param results 지금까지 처리된 상품별 결과이며 처리 완료 순서다
 * @param startedAt 배치 접수 시각
 * @param completedAt 배치 완료 시각이며 진행 중이면 null
 */
public record BulkVerificationBatchResponse(
		UUID batchId,
		BatchStatus status,
		int requestedCount,
		int processedCount,
		int queuedCount,
		List<Item> results,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {

	/** BatchStatus는 배경 처리가 끝났는지를 구분한다. */
	public enum BatchStatus {
		PROCESSING,
		COMPLETED
	}

	/**
	 * Item은 상품 한 건의 재검증 접수 성공 또는 실패를 나타낸다.
	 *
	 * @param productId 대상 판매처 상품 ID
	 * @param success 재검증 요청이 Queue에 접수됐는지 여부
	 * @param verification 성공 시 접수된 재검증 요청이며 실패면 null
	 * @param error 실패 시 사람이 읽을 수 있는 사유이며 성공이면 null
	 */
	public record Item(
			long productId,
			boolean success,
			OfferVerificationResponse verification,
			String error) {
	}
}
