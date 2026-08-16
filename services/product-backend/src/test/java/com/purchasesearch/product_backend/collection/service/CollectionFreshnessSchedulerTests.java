package com.purchasesearch.product_backend.collection.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** CollectionFreshnessSchedulerTests는 배치 크기 전달과 실행 위임을 검증한다. */
class CollectionFreshnessSchedulerTests {

	/** 설정한 배치 크기로 refreshStaleBatch를 호출하는지 검증한다. */
	@Test
	void delegatesToRefreshServiceWithConfiguredBatchSize() {
		CollectionRefreshService refreshService = mock(CollectionRefreshService.class);
		when(refreshService.refreshStaleBatch(15)).thenReturn(3);
		CollectionFreshnessScheduler scheduler = new CollectionFreshnessScheduler(refreshService, 15);

		scheduler.refreshStaleSearchContexts();

		verify(refreshService).refreshStaleBatch(15);
	}

	/** 0 이하로 설정된 배치 크기를 최소 1로 보정하는지 검증한다. */
	@Test
	void clampsNonPositiveBatchSizeToOne() {
		CollectionRefreshService refreshService = mock(CollectionRefreshService.class);
		when(refreshService.refreshStaleBatch(1)).thenReturn(0);
		CollectionFreshnessScheduler scheduler = new CollectionFreshnessScheduler(refreshService, 0);

		scheduler.refreshStaleSearchContexts();

		verify(refreshService).refreshStaleBatch(1);
	}
}
