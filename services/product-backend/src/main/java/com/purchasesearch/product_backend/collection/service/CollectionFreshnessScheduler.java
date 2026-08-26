package com.purchasesearch.product_backend.collection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CollectionFreshnessScheduler는 사용자가 검색하기 전에 TTL이 지난 판매처/검색어
 * 조합을 낮은 우선순위로 미리 갱신해, 실제 조회 시점에 CollectionRefreshService가
 * 다시 크롤링을 기다리지 않고 최신 데이터를 돌려줄 확률을 높인다.
 *
 * purchase-research.freshness.scheduler.enabled가 true인 환경에서만 동작하며,
 * 기본값은 false다(레이트리미터와 같이 명시적으로 켜야 하는 배경 작업이기 때문).
 */
@Component
@ConditionalOnProperty(prefix = "purchase-research.freshness.scheduler", name = "enabled", havingValue = "true")
public class CollectionFreshnessScheduler {

	private static final Logger log = LoggerFactory.getLogger(CollectionFreshnessScheduler.class);

	private final CollectionRefreshService collectionRefreshService;
	private final int batchSize;

	/**
	 * @param collectionRefreshService TTL 판정과 사전 갱신 발행을 담당하는 서비스
	 * @param batchSize 한 번 실행에서 시도할 최대 작업 수
	 */
	public CollectionFreshnessScheduler(
			CollectionRefreshService collectionRefreshService,
			@Value("${purchase-research.freshness.scheduler.batch-size:20}") int batchSize) {
		this.collectionRefreshService = collectionRefreshService;
		this.batchSize = Math.max(1, batchSize);
	}

	/** 설정된 주기마다 TTL이 지난 검색 범위를 마지막 수집이 오래된 순으로 미리 갱신한다. */
	@Scheduled(
			fixedDelayString = "${purchase-research.freshness.scheduler.interval-ms:1800000}",
			initialDelayString = "${purchase-research.freshness.scheduler.initial-delay-ms:60000}")
	public void refreshStaleSearchContexts() {
		int queued = collectionRefreshService.refreshStaleBatch(batchSize);
		if (queued > 0) {
			log.info("사전 갱신으로 수집 작업 {}건을 발행했습니다", queued);
		}
	}
}
