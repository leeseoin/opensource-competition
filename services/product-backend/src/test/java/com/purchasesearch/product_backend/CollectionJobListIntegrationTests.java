package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectionJobListResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionResultEnvelope;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchFilters;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchPayload;
import com.purchasesearch.product_backend.collection.dto.CollectorResult;
import com.purchasesearch.product_backend.collection.dto.CollectorResult.VerificationSummary;
import com.purchasesearch.product_backend.collection.entity.CollectionJob;
import com.purchasesearch.product_backend.collection.entity.CollectionTask;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;

/**
 * CollectionJobListIntegrationTests는 요청 이력 목록이 판매처/상태 필터와 성공률/상품 수 집계를
 * 실제 PostgreSQL에서 올바르게 계산하는지 서비스와 HTTP 응답 양쪽에서 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CollectionJobListIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CollectionJobService collectionJobService;

	@Autowired
	private CollectionJobRepository collectionJobRepository;

	@Autowired
	private CollectionTaskRepository collectionTaskRepository;

	/** 성공/실패 작업이 섞인 job의 성공률과 상품 수 합계가 올바르게 계산되는지 검증한다. */
	@Test
	void computesSuccessRateAndProductCountAcrossTasks() throws Exception {
		OffsetDateTime requestedAt = OffsetDateTime.now();
		CollectionJob job = saveJob("job-mixed", "abcmart", requestedAt, "PARTIAL", 3);
		saveSucceededTask(job, "task-1", requestedAt, 1, 30);
		saveSucceededTask(job, "task-2", requestedAt, 2, 20);
		savePublishFailedTask(job, "task-3", requestedAt, 3);

		CollectionJobListResponse response = collectionJobService.list("abcmart", null, 0, 20);

		var summary = response.items().stream()
				.filter(item -> item.jobId().equals("job-mixed"))
				.findFirst()
				.orElseThrow();
		assertThat(summary.taskCount()).isEqualTo(3);
		assertThat(summary.succeededTaskCount()).isEqualTo(2);
		assertThat(summary.failedTaskCount()).isEqualTo(1);
		assertThat(summary.successRate()).isEqualTo(2.0 / 3.0);
		assertThat(summary.productCount()).isEqualTo(50);

		mockMvc.perform(get("/internal/v1/collection-jobs").param("merchant", "abcmart"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].jobId").value("job-mixed"))
				.andExpect(jsonPath("$.items[0].successRate").value(2.0 / 3.0))
				.andExpect(jsonPath("$.items[0].productCount").value(50));
	}

	/** merchant/status 필터가 서로 다른 판매처와 상태의 job을 제외하는지 검증한다. */
	@Test
	void filtersByMerchantAndStatus() throws Exception {
		OffsetDateTime requestedAt = OffsetDateTime.now();
		saveJob("job-abcmart-completed", "abcmart", requestedAt, "COMPLETED");
		saveJob("job-abcmart-failed", "abcmart", requestedAt, "FAILED");
		saveJob("job-29cm-completed", "29cm", requestedAt, "COMPLETED");

		mockMvc.perform(get("/internal/v1/collection-jobs")
						.param("merchant", "abcmart")
						.param("status", "COMPLETED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.items[0].jobId").value("job-abcmart-completed"));
	}

	/** size보다 job이 많으면 hasNext가 true이고 최신 요청순으로 잘리는지 검증한다. */
	@Test
	void paginatesWithHasNext() {
		OffsetDateTime requestedAt = OffsetDateTime.now();
		saveJob("job-page-1", "abcmart", requestedAt.minusMinutes(1), "COMPLETED");
		saveJob("job-page-2", "abcmart", requestedAt, "COMPLETED");

		CollectionJobListResponse firstPage = collectionJobService.list("abcmart", null, 0, 1);

		assertThat(firstPage.items()).hasSize(1);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.items().get(0).jobId()).isEqualTo("job-page-2");
	}

	private CollectionJob saveJob(String jobId, String merchant, OffsetDateTime requestedAt, String status) {
		return saveJob(jobId, merchant, requestedAt, status, 1);
	}

	private CollectionJob saveJob(
			String jobId, String merchant, OffsetDateTime requestedAt, String status, int totalTasks) {
		CollectionTaskMessage message = taskMessage(jobId + "-task-1", jobId, merchant, requestedAt, 1);
		CollectionJob job = CollectionJob.queued(message, totalTasks, 1, totalTasks);
		job.updateStatus(status, requestedAt);
		return collectionJobRepository.save(job);
	}

	private void saveSucceededTask(
			CollectionJob job, String taskId, OffsetDateTime requestedAt, int page, int productCount) {
		CollectionTaskMessage message = taskMessage(taskId, job.getJobId(), job.getMerchant(), requestedAt, page);
		CollectionTask task = CollectionTask.queued(job, message);
		task.complete(successEnvelope(taskId, job.getJobId(), requestedAt), productCount);
		collectionTaskRepository.save(task);
	}

	private void savePublishFailedTask(CollectionJob job, String taskId, OffsetDateTime requestedAt, int page) {
		CollectionTaskMessage message = taskMessage(taskId, job.getJobId(), job.getMerchant(), requestedAt, page);
		CollectionTask task = CollectionTask.queued(job, message);
		task.failPublishing("RABBITMQ_PUBLISH_FAILED", "no ack", requestedAt);
		collectionTaskRepository.save(task);
	}

	private CollectionTaskMessage taskMessage(
			String taskId, String jobId, String merchant, OffsetDateTime requestedAt, int page) {
		return new CollectionTaskMessage(
				"1", taskId, jobId, merchant, "search",
				50, 1, 3, requestedAt, taskId + "-idem",
				new SearchPayload("구두", page, 50, "ko-KR", "KRW",
						new SearchFilters(null, null, List.of(), List.of(), List.of(), false, Map.of())));
	}

	/** complete()가 읽는 status/completedAt/durationMs/verificationSummary만 채운 최소 결과 봉투를 만든다. */
	private CollectionResultEnvelope successEnvelope(String taskId, String jobId, OffsetDateTime completedAt) {
		CollectorResult collectorResult = new CollectorResult(
				"req-" + taskId, "search", "success", "abcmart", "구두", null,
				null, null, completedAt, "test",
				new VerificationSummary(0, 0, 0, 0, 0, 0, 0),
				List.of(), List.of(), List.of());
		return new CollectionResultEnvelope(
				"1", taskId, jobId, "success", completedAt.minusSeconds(1), completedAt, 1000L,
				collectorResult, null);
	}
}
