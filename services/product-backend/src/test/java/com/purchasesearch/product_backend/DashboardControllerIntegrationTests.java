package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchFilters;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchPayload;
import com.purchasesearch.product_backend.collection.entity.CollectionJob;
import com.purchasesearch.product_backend.collection.entity.CollectionTask;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;
import com.purchasesearch.product_backend.dashboard.exception.InvalidDashboardWindowException;
import com.purchasesearch.product_backend.dashboard.service.DashboardService;
import com.purchasesearch.product_backend.evidence.entity.ProductVerification;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;
import com.purchasesearch.product_backend.product.entity.Product;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;

/**
 * DashboardControllerIntegrationTests는 실제 PostgreSQL에서 시간 창별 job/작업, 판매처 상품,
 * 검증 집계가 올바른지 서비스와 HTTP 응답 양쪽에서 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private CollectionJobRepository collectionJobRepository;

	@Autowired
	private CollectionTaskRepository collectionTaskRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MerchantProductRepository merchantProductRepository;

	@Autowired
	private OfferSnapshotRepository offerSnapshotRepository;

	@Autowired
	private ProductVerificationRepository productVerificationRepository;

	/** 창 안의 job/작업 상태, 판매처별 상품 수, 검증 일치율이 창 밖 데이터를 제외하고 집계되는지 검증한다. */
	@Test
	void summarizesWindowedDataAcrossAllDomains() throws Exception {
		OffsetDateTime windowStart = OffsetDateTime.now().minusHours(1);
		OffsetDateTime windowEnd = OffsetDateTime.now().plusHours(1);
		OffsetDateTime insideWindow = OffsetDateTime.now();
		OffsetDateTime outsideWindow = windowStart.minusDays(1);

		CollectionJob completedJob = saveJob("job-in-window", "abcmart", insideWindow, "COMPLETED");
		saveJob("job-outside-window", "abcmart", outsideWindow, "FAILED");
		CollectionTask queuedTask = saveQueuedTask(completedJob, "task-queued", insideWindow, 1);
		CollectionTask publishFailedTask = saveQueuedTask(completedJob, "task-publish-failed", insideWindow, 2);
		publishFailedTask.failPublishing("RABBITMQ_PUBLISH_FAILED", "no ack", insideWindow);
		collectionTaskRepository.save(publishFailedTask);
		collectionTaskRepository.save(queuedTask);

		MerchantProduct abcmartProduct = saveMerchantProduct("abcmart", "ext-1", insideWindow);
		saveMerchantProduct("abcmart", "ext-2", insideWindow);
		saveMerchantProduct("29cm", "ext-3", insideWindow);
		saveMerchantProduct("abcmart", "ext-4", outsideWindow);

		OfferSnapshot snapshot = offerSnapshotRepository.save(OfferSnapshot.create(
				abcmartProduct, "req-1", 10_000L, "KRW", 0L, "KRW", "무료배송",
				"available", BigDecimal.valueOf(4.5), 10, "https://example.com", insideWindow, "test"));
		saveVerification(abcmartProduct, snapshot, "MATCHED", insideWindow);
		saveVerification(abcmartProduct, snapshot, "MATCHED", insideWindow);
		saveVerification(abcmartProduct, snapshot, "MISMATCH", insideWindow);
		saveVerification(abcmartProduct, snapshot, "MATCHED", outsideWindow);

		var response = dashboardService.summarize(windowStart, windowEnd);

		assertThat(response.jobs().total()).isEqualTo(1);
		assertThat(response.jobs().byStatus().get("COMPLETED")).isEqualTo(1L);
		assertThat(response.jobs().byStatus().get("FAILED")).isEqualTo(0L);

		assertThat(response.tasks().total()).isEqualTo(2);
		assertThat(response.tasks().byStatus().get("QUEUED")).isEqualTo(1L);
		assertThat(response.tasks().byStatus().get("PUBLISH_FAILED")).isEqualTo(1L);
		assertThat(response.tasks().byStatus().get("SUCCESS")).isEqualTo(0L);

		assertThat(response.products().totalMerchantProductsCollected()).isEqualTo(3);
		assertThat(response.products().byMerchant())
				.extracting("merchant", "count")
				.containsExactlyInAnyOrder(Tuple.tuple("abcmart", 2L), Tuple.tuple("29cm", 1L));

		assertThat(response.verifications().total()).isEqualTo(3);
		assertThat(response.verifications().byStatus().get("MATCHED")).isEqualTo(2L);
		assertThat(response.verifications().byStatus().get("MISMATCH")).isEqualTo(1L);
		assertThat(response.verifications().matchRate()).isEqualTo(2.0 / 3.0);

		mockMvc.perform(get("/internal/v1/dashboard/summary")
						.param("since", windowStart.toString())
						.param("until", windowEnd.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobs.total").value(1))
				.andExpect(jsonPath("$.tasks.total").value(2))
				.andExpect(jsonPath("$.products.totalMerchantProductsCollected").value(3))
				.andExpect(jsonPath("$.verifications.total").value(3));
	}

	/** since/until을 생략하면 최근 24시간이 기본 시간 창으로 쓰이는지 확인한다. */
	@Test
	void defaultsToLast24HoursWhenWindowNotSpecified() throws Exception {
		mockMvc.perform(get("/internal/v1/dashboard/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.window.since").exists())
				.andExpect(jsonPath("$.window.until").exists());
	}

	/** since가 until보다 늦으면 400과 오류 코드를 반환하는지 확인한다. */
	@Test
	void rejectsWindowWhereSinceIsAfterUntil() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();

		mockMvc.perform(get("/internal/v1/dashboard/summary")
						.param("since", now.toString())
						.param("until", now.minusHours(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_WINDOW"));
	}

	/** service 직접 호출에서도 잘못된 창이 같은 예외로 거절되는지 확인한다. */
	@Test
	void serviceRejectsInvalidWindowDirectly() {
		OffsetDateTime now = OffsetDateTime.now();

		assertThatThrownBy(() -> dashboardService.summarize(now, now))
				.isInstanceOf(InvalidDashboardWindowException.class);
	}

	private CollectionJob saveJob(String jobId, String merchant, OffsetDateTime requestedAt, String status) {
		CollectionTaskMessage message = taskMessage(jobId + "-task-1", jobId, merchant, requestedAt, 1);
		CollectionJob job = CollectionJob.queued(message, 1, 1, 1);
		job.updateStatus(status, requestedAt);
		return collectionJobRepository.save(job);
	}

	private CollectionTask saveQueuedTask(CollectionJob job, String taskId, OffsetDateTime requestedAt, int page) {
		CollectionTaskMessage message = taskMessage(taskId, job.getJobId(), job.getMerchant(), requestedAt, page);
		return CollectionTask.queued(job, message);
	}

	private CollectionTaskMessage taskMessage(
			String taskId, String jobId, String merchant, OffsetDateTime requestedAt, int page) {
		return new CollectionTaskMessage(
				"1", taskId, jobId, merchant, "search",
				50, 1, 3, requestedAt, taskId + "-idem",
				new SearchPayload("구두", page, 50, "ko-KR", "KRW",
						new SearchFilters(null, null, List.of(), List.of(), List.of(), false, Map.of())));
	}

	private MerchantProduct saveMerchantProduct(String merchant, String externalId, OffsetDateTime collectedAt) {
		Product product = productRepository.save(
				Product.create("스니커즈 " + externalId, "brand", List.of("신발", "스니커즈"), List.of()));
		MerchantProduct merchantProduct = MerchantProduct.create(
				product, merchant, externalId, "https://example.com/" + externalId, collectedAt);
		return merchantProductRepository.save(merchantProduct);
	}

	private void saveVerification(
			MerchantProduct merchantProduct, OfferSnapshot snapshot, String status, OffsetDateTime verifiedAt) {
		productVerificationRepository.save(ProductVerification.create(
				merchantProduct, snapshot, status, List.of("price"), List.of(),
				"https://example.com/json", "https://example.com/html", verifiedAt));
	}
}
