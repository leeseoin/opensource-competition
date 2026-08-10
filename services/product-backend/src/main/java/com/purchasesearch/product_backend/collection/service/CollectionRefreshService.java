package com.purchasesearch.product_backend.collection.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;
import com.purchasesearch.product_backend.product.service.ProductQueryService;

/** CollectionRefreshService는 DB 데이터 없음/만료를 판정하고 필요한 검색 수집만 Queue에 등록한다. */
@Service
public class CollectionRefreshService {

	private static final int REFRESH_LIMIT = 5;

	private final ProductQueryService productQueryService;
	private final CollectionTaskPublisher collectionTaskPublisher;
	private final long staleAfterHours;

	/**
	 * @param productQueryService 기존 DB 상품 조회 서비스
	 * @param collectionTaskPublisher 검색 수집 Queue 발행 서비스
	 * @param staleAfterHours 자동 갱신을 요구하는 수집 경과 시간
	 */
	public CollectionRefreshService(
			ProductQueryService productQueryService,
			CollectionTaskPublisher collectionTaskPublisher,
			@Value("${purchase-research.freshness.offer-ttl-hours:24}") long staleAfterHours) {
		this.productQueryService = productQueryService;
		this.collectionTaskPublisher = collectionTaskPublisher;
		this.staleAfterHours = Math.max(1, staleAfterHours);
	}

	/**
	 * @param request 판매처/검색어와 강제 갱신 여부
	 * @return 기존 상태와 Queue 접수 결과
	 */
	public CollectionRefreshResponse request(CollectionRefreshRequest request) {
		ProductSearchResponse current = productQueryService.search(request.merchant(), request.query(), 50);
		OffsetDateTime latestCollectedAt = current.products().stream()
				.filter(product -> product.source() != null && product.source().collectedAt() != null)
				.map(product -> product.source().collectedAt())
				.max(Comparator.naturalOrder())
				.orElse(null);
		DataStatus dataStatus = status(current, latestCollectedAt);
		if (dataStatus == DataStatus.FRESH && !Boolean.TRUE.equals(request.force())) {
			return new CollectionRefreshResponse(dataStatus, false, null, null, "NO_ACTION", latestCollectedAt);
		}
		CollectionTaskResponse queued = collectionTaskPublisher.publish(new CollectionTaskRequest(
				request.merchant(), request.query().trim(), 1, REFRESH_LIMIT, "ko-KR", "KRW", 30, 2, null));
		return new CollectionRefreshResponse(
				dataStatus, true, queued.jobId(), queued.taskId(), queued.status(), latestCollectedAt);
	}

	/** 검색 결과 존재 여부와 가장 최근 snapshot 나이로 상태를 판정한다. */
	private DataStatus status(ProductSearchResponse current, OffsetDateTime latestCollectedAt) {
		if (current.products().isEmpty() || latestCollectedAt == null) {
			return DataStatus.MISSING;
		}
		long ageHours = Math.max(0, Duration.between(latestCollectedAt, OffsetDateTime.now()).toHours());
		return ageHours <= staleAfterHours ? DataStatus.FRESH : DataStatus.STALE;
	}
}
