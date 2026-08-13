package com.purchasesearch.product_backend.collection.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;

/** CollectionRefreshService는 DB 데이터 없음/만료를 판정하고 필요한 검색 수집만 Queue에 등록한다. */
@Service
public class CollectionRefreshService {

	private static final int REFRESH_LIMIT = 5;

	private final CollectionSearchContextRepository searchContextRepository;
	private final CollectionTaskPublisher collectionTaskPublisher;
	private final long staleAfterHours;

	/**
	 * @param searchContextRepository 동일 수집 범위의 마지막 완료 시각 저장소
	 * @param collectionTaskPublisher 검색 수집 Queue 발행 서비스
	 * @param staleAfterHours 자동 갱신을 요구하는 수집 경과 시간
	 */
	public CollectionRefreshService(
			CollectionSearchContextRepository searchContextRepository,
			CollectionTaskPublisher collectionTaskPublisher,
			@Value("${purchase-research.freshness.offer-ttl-hours:24}") long staleAfterHours) {
		this.searchContextRepository = searchContextRepository;
		this.collectionTaskPublisher = collectionTaskPublisher;
		this.staleAfterHours = Math.max(1, staleAfterHours);
	}

	/**
	 * @param request 판매처/검색어와 강제 갱신 여부
	 * @return 기존 상태와 Queue 접수 결과
	 */
	public CollectionRefreshResponse request(CollectionRefreshRequest request) {
		String normalizedQuery = request.query().trim();
		Instant latestCollectedInstant = searchContextRepository
				.findLatestDefaultSearchCollectedAt(request.merchant(), normalizedQuery)
				.orElse(null);
		OffsetDateTime latestCollectedAt = latestCollectedInstant == null
				? null : latestCollectedInstant.atOffset(ZoneOffset.UTC);
		DataStatus dataStatus = status(latestCollectedInstant);
		if (dataStatus == DataStatus.FRESH && !Boolean.TRUE.equals(request.force())) {
			return new CollectionRefreshResponse(dataStatus, false, null, null, "NO_ACTION", latestCollectedAt);
		}
		CollectionTaskResponse queued = collectionTaskPublisher.publish(new CollectionTaskRequest(
				request.merchant(), normalizedQuery, 1, REFRESH_LIMIT, "ko-KR", "KRW", 30, 2, null));
		return new CollectionRefreshResponse(
				dataStatus, true, queued.jobId(), queued.taskId(), queued.status(), latestCollectedAt);
	}

	/** 동일 판매처, 검색어와 필터의 마지막 수집 완료 시각으로 상태를 판정한다. */
	private DataStatus status(Instant latestCollectedAt) {
		if (latestCollectedAt == null) {
			return DataStatus.MISSING;
		}
		Instant staleBoundary = Instant.now().minus(Duration.ofHours(staleAfterHours));
		return latestCollectedAt.isBefore(staleBoundary) ? DataStatus.STALE : DataStatus.FRESH;
	}
}
