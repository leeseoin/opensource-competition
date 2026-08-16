package com.purchasesearch.product_backend.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.repository.StaleSearchContext;

/** CollectionRefreshServiceTests는 FRESH/STALE/MISSING에 따른 조건부 Queue 발행을 검증한다. */
class CollectionRefreshServiceTests {

	private CollectionSearchContextRepository searchContextRepository;
	private CollectionTaskPublisher publisher;
	private CollectionRefreshService service;

	/** 각 테스트에 24시간 TTL의 격리된 최신성 서비스를 구성한다. */
	@BeforeEach
	void setUp() {
		searchContextRepository = mock(CollectionSearchContextRepository.class);
		publisher = mock(CollectionTaskPublisher.class);
		service = new CollectionRefreshService(searchContextRepository, publisher, 24);
	}

	/** 24시간 이내 데이터는 force가 없으면 새 Queue 작업을 만들지 않는지 검증한다. */
	@Test
	void doesNotCollectFreshDataWithoutForce() {
		when(searchContextRepository.findLatestDefaultSearchCollectedAt("abcmart", "구두"))
				.thenReturn(Optional.of(Instant.now().minusSeconds(2 * 60 * 60)));

		var response = service.request(new CollectionRefreshRequest("abcmart", "구두", false));

		assertThat(response.dataStatus()).isEqualTo(DataStatus.FRESH);
		assertThat(response.collectionRequested()).isFalse();
		verify(publisher, never()).publish(any());
	}

	/** 오래됐거나 없는 데이터는 Python Worker가 소비할 검색 작업으로 등록하는지 검증한다. */
	@Test
	void collectsStaleData() {
		when(searchContextRepository.findLatestDefaultSearchCollectedAt("abcmart", "구두"))
				.thenReturn(Optional.of(Instant.now().minusSeconds(30 * 60 * 60)));
		when(publisher.publish(any(CollectionTaskRequest.class)))
				.thenReturn(new CollectionTaskResponse("task-1", "job-1", "QUEUED", "abcmart", "search", OffsetDateTime.now()));

		var response = service.request(new CollectionRefreshRequest("abcmart", "구두", false));

		assertThat(response.dataStatus()).isEqualTo(DataStatus.STALE);
		assertThat(response.collectionRequested()).isTrue();
		assertThat(response.jobId()).isEqualTo("job-1");
		verify(publisher).publish(any(CollectionTaskRequest.class));
	}

	/** 24시간을 분 단위로 초과한 데이터가 시간 절삭 없이 STALE이 되는지 검증한다. */
	@Test
	void collectsDataOlderThanTtlByPartialHour() {
		when(searchContextRepository.findLatestDefaultSearchCollectedAt("abcmart", "구두"))
				.thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(24).plusMinutes(30))));
		when(publisher.publish(any(CollectionTaskRequest.class)))
				.thenReturn(new CollectionTaskResponse("task-boundary", "job-boundary", "QUEUED",
						"abcmart", "search", OffsetDateTime.now()));

		var response = service.request(new CollectionRefreshRequest("abcmart", "구두", false));

		assertThat(response.dataStatus()).isEqualTo(DataStatus.STALE);
		assertThat(response.collectionRequested()).isTrue();
		verify(publisher).publish(any(CollectionTaskRequest.class));
	}

	/** 다른 검색어의 최신 상품이 있어도 정확한 수집 범위가 없으면 새 작업을 만드는지 검증한다. */
	@Test
	void collectsWhenExactSearchContextIsMissing() {
		when(searchContextRepository.findLatestDefaultSearchCollectedAt("abcmart", "구두"))
				.thenReturn(Optional.empty());
		when(publisher.publish(any(CollectionTaskRequest.class)))
				.thenReturn(new CollectionTaskResponse("task-2", "job-2", "QUEUED", "abcmart", "search", OffsetDateTime.now()));

		var response = service.request(new CollectionRefreshRequest("abcmart", " 구두 ", false));

		assertThat(response.dataStatus()).isEqualTo(DataStatus.MISSING);
		assertThat(response.collectionRequested()).isTrue();
		assertThat(response.jobId()).isEqualTo("job-2");
		verify(searchContextRepository).findLatestDefaultSearchCollectedAt("abcmart", "구두");
		verify(publisher).publish(any(CollectionTaskRequest.class));
	}

	/** 조회된 각 오래된 조합마다 낮은 우선순위로 별도 Queue 작업을 발행하는지 검증한다. */
	@Test
	void refreshStaleBatchQueuesEachStaleSearchContext() {
		StaleSearchContext abcmart = mockStaleContext("abcmart", "구두");
		StaleSearchContext cm29 = mockStaleContext("29cm", "로퍼");
		when(searchContextRepository.findStaleDefaultSearchContexts(any(Instant.class), eq(20)))
				.thenReturn(List.of(abcmart, cm29));
		when(publisher.publish(any(CollectionTaskRequest.class)))
				.thenReturn(new CollectionTaskResponse("task-1", "job-1", "QUEUED", "abcmart", "search", OffsetDateTime.now()));

		int queued = service.refreshStaleBatch(20);

		assertThat(queued).isEqualTo(2);
		verify(publisher).publish(argThat(request ->
				request != null && request.merchant().equals("abcmart") && request.query().equals("구두")));
		verify(publisher).publish(argThat(request ->
				request != null && request.merchant().equals("29cm") && request.query().equals("로퍼")));
	}

	/** 한 조합의 Queue 발행이 실패해도 나머지 조합은 계속 발행하고 성공 수만 반환하는지 검증한다. */
	@Test
	void refreshStaleBatchSkipsFailedPublishAndContinues() {
		StaleSearchContext failing = mockStaleContext("abcmart", "구두");
		StaleSearchContext succeeding = mockStaleContext("29cm", "로퍼");
		when(searchContextRepository.findStaleDefaultSearchContexts(any(Instant.class), anyInt()))
				.thenReturn(List.of(failing, succeeding));
		when(publisher.publish(argThat(request -> request != null && request.merchant().equals("abcmart"))))
				.thenThrow(new CollectionTaskPublishException("broker timeout"));
		when(publisher.publish(argThat(request -> request != null && request.merchant().equals("29cm"))))
				.thenReturn(new CollectionTaskResponse("task-2", "job-2", "QUEUED", "29cm", "search", OffsetDateTime.now()));

		int queued = service.refreshStaleBatch(20);

		assertThat(queued).isEqualTo(1);
		verify(publisher, times(2)).publish(any(CollectionTaskRequest.class));
	}

	/** 오래된 조합이 없으면 Queue 발행 없이 0을 반환하는지 검증한다. */
	@Test
	void refreshStaleBatchReturnsZeroWithoutStaleContexts() {
		when(searchContextRepository.findStaleDefaultSearchContexts(any(Instant.class), anyInt()))
				.thenReturn(List.of());

		int queued = service.refreshStaleBatch(20);

		assertThat(queued).isZero();
		verify(publisher, never()).publish(any());
	}

	private static StaleSearchContext mockStaleContext(String merchant, String searchQuery) {
		StaleSearchContext context = mock(StaleSearchContext.class);
		when(context.getMerchant()).thenReturn(merchant);
		when(context.getSearchQuery()).thenReturn(searchQuery);
		when(context.getCollectedAt()).thenReturn(Instant.now().minusSeconds(48 * 60 * 60));
		return context;
	}
}
