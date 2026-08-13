package com.purchasesearch.product_backend.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;

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
}
