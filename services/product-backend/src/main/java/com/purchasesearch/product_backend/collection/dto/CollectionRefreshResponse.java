package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;

/**
 * @param dataStatus 기존 DB 데이터 상태
 * @param collectionRequested Queue 작업 등록 여부
 * @param jobId 등록한 job ID
 * @param taskId 등록한 task ID
 * @param status 현재 처리 상태 또는 NO_ACTION
 * @param latestCollectedAt 검색 결과 중 가장 최근 수집 시각
 */
public record CollectionRefreshResponse(
		DataStatus dataStatus,
		boolean collectionRequested,
		String jobId,
		String taskId,
		String status,
		OffsetDateTime latestCollectedAt) {

	/** DataStatus는 검색어와 판매처 기준 DB 데이터의 존재 및 최신성을 구분한다. */
	public enum DataStatus {
		FRESH,
		STALE,
		MISSING
	}
}
