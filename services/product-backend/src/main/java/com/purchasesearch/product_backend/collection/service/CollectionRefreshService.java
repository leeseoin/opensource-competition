package com.purchasesearch.product_backend.collection.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.repository.StaleSearchContext;

/** CollectionRefreshService는 DB 데이터 없음/만료를 판정하고 필요한 검색 수집만 Queue에 등록한다. */
@Service
public class CollectionRefreshService {

	private static final Logger log = LoggerFactory.getLogger(CollectionRefreshService.class);

	private static final int REFRESH_LIMIT = 5;
	// 사용자가 직접 기다리는 반응형 갱신(우선순위 30)보다 뒤로 밀려야 하는 배경 사전 갱신 우선순위다.
	private static final int BACKGROUND_REFRESH_PRIORITY = 5;

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

	/**
	 * TTL이 지난 기본 검색 범위를 마지막 수집이 오래된 순으로 최대 batchSize개 찾아
	 * 낮은 우선순위로 미리 갱신을 발행한다. 사용자가 검색하기 전에 데이터를 채워 둬
	 * {@link #request}가 STALE/MISSING을 반환하며 만드는 대기 시간을 줄이는 데 쓰인다.
	 * 개별 발행 실패는 기록만 하고 나머지 batch 처리를 막지 않는다.
	 *
	 * @param batchSize 이번 실행에서 시도할 최대 판매처/검색어 조합 수
	 * @return 실제로 Queue 발행에 성공한 조합 수
	 */
	public int refreshStaleBatch(int batchSize) {
		Instant staleBoundary = Instant.now().minus(Duration.ofHours(staleAfterHours));
		List<StaleSearchContext> staleContexts =
				searchContextRepository.findStaleDefaultSearchContexts(staleBoundary, batchSize);
		int queued = 0;
		for (StaleSearchContext context : staleContexts) {
			try {
				collectionTaskPublisher.publish(new CollectionTaskRequest(
						context.getMerchant(), context.getSearchQuery(),
						1, REFRESH_LIMIT, "ko-KR", "KRW", BACKGROUND_REFRESH_PRIORITY, null, null));
				queued++;
			} catch (CollectionTaskPublishException exception) {
				log.warn("사전 갱신 발행 실패: merchant={}, query={}, reason={}",
						context.getMerchant(), context.getSearchQuery(), exception.getMessage());
			}
		}
		return queued;
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
