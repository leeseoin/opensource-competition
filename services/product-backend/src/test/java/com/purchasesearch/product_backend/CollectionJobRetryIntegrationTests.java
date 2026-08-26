package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchFilters;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchPayload;
import com.purchasesearch.product_backend.collection.entity.CollectionJob;
import com.purchasesearch.product_backend.collection.entity.CollectionTask;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * CollectionJobRetryIntegrationTests는 job 재실행 API가 등록 당시 저장한 조건 그대로 새 job을
 * RabbitMQ에 다시 발행하고, 재실행할 수 없는 상태는 거절하는지 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CollectionJobRetryIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CollectionJobRepository collectionJobRepository;

	@Autowired
	private CollectionTaskRepository collectionTaskRepository;

	@BeforeEach
	void prepareQueues() {
		purgeQueues();
		deleteTrackedJobs();
	}

	@AfterEach
	void cleanQueues() {
		purgeQueues();
		deleteTrackedJobs();
	}

	/**
	 * FAILED job을 재실행하면 등록 당시의 merchant/query/페이지범위/limit/locale/currency/filters
	 * 그대로 새 jobId로 다시 발행되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void retriesFailedJobWithOriginalConditions() throws Exception {
		String originalJobId = saveJobWithTasks("job-original-failed", "FAILED", 2, 3);

		String response = mockMvc.perform(post("/internal/v1/collection-jobs/{jobId}/retry", originalJobId))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.merchant").value("29cm"))
				.andExpect(jsonPath("$.startPage").value(2))
				.andExpect(jsonPath("$.endPage").value(3))
				.andExpect(jsonPath("$.taskCount").value(2))
				.andExpect(jsonPath("$.jobId").value(org.hamcrest.Matchers.not(originalJobId)))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String newJobId = objectMapper.readTree(response).get("jobId").asText();

		CollectionTaskMessage first = readTask(receiveSearchTask());
		CollectionTaskMessage second = readTask(receiveSearchTask());

		assertThat(first.jobId()).isEqualTo(newJobId).isEqualTo(second.jobId());
		assertThat(List.of(first.payload().page(), second.payload().page())).containsExactlyInAnyOrder(2, 3);
		assertThat(first.merchant()).isEqualTo("29cm");
		assertThat(first.priority()).isEqualTo(10);
		assertThat(first.maxAttempts()).isEqualTo(4);
		assertThat(first.payload().query()).isEqualTo("구두");
		assertThat(first.payload().limit()).isEqualTo(45);
		assertThat(first.payload().locale()).isEqualTo("ko-KR");
		assertThat(first.payload().currency()).isEqualTo("KRW");
		assertThat(first.payload().filters().sizes()).containsExactly("270");
		assertThat(first.payload().filters().inStockOnly()).isTrue();
	}

	/**
	 * PARTIAL job도 FAILED와 동일하게 재실행 대상인지 검증한다.
	 *
	 * @throws Exception HTTP 요청 또는 Queue 수신에 실패한 경우
	 */
	@Test
	void retriesPartialJob() throws Exception {
		String jobId = saveJobWithTasks("job-original-partial", "PARTIAL", 1, 1);

		mockMvc.perform(post("/internal/v1/collection-jobs/{jobId}/retry", jobId))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.taskCount").value(1));

		assertThat(receiveSearchTask()).isNotNull();
	}

	/**
	 * COMPLETED job은 이미 성공했으므로 재실행을 거절하고 Queue에 아무것도 발행하지 않는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsRetryForCompletedJob() throws Exception {
		String jobId = saveJobWithTasks("job-original-completed", "COMPLETED", 1, 1);

		mockMvc.perform(post("/internal/v1/collection-jobs/{jobId}/retry", jobId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLLECTION_TASK"));

		assertThat(rabbitTemplate.receive(CollectionQueueNames.SEARCH_TASK_QUEUE)).isNull();
	}

	/**
	 * 존재하지 않는 jobId 재실행 요청이 안전한 404 오류로 변환되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void returnsNotFoundForUnknownJobRetry() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-jobs/{jobId}/retry", "job-does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COLLECTION_JOB_NOT_FOUND"));
	}

	/**
	 * 지정한 상태와 페이지 범위로 job과 그 하위 작업을 직접 저장한다.
	 *
	 * @param jobId 저장할 job 식별자
	 * @param status 저장할 job 상태
	 * @param startPage 첫 페이지
	 * @param endPage 마지막 페이지
	 * @return 저장한 jobId
	 */
	private String saveJobWithTasks(String jobId, String status, int startPage, int endPage) {
		OffsetDateTime requestedAt = OffsetDateTime.now();
		int totalTasks = endPage - startPage + 1;
		CollectionTaskMessage firstMessage = taskMessage(jobId + "-task-" + startPage, jobId, requestedAt, startPage);
		CollectionJob job = CollectionJob.queued(firstMessage, totalTasks, startPage, endPage);
		job.updateStatus(status, requestedAt);
		collectionJobRepository.save(job);
		for (int page = startPage; page <= endPage; page++) {
			CollectionTaskMessage message = taskMessage(jobId + "-task-" + page, jobId, requestedAt, page);
			CollectionTask task = CollectionTask.queued(job, message);
			task.failPublishing("RABBITMQ_PUBLISH_FAILED", "no ack", requestedAt);
			collectionTaskRepository.save(task);
		}
		return jobId;
	}

	private CollectionTaskMessage taskMessage(String taskId, String jobId, OffsetDateTime requestedAt, int page) {
		return new CollectionTaskMessage(
				"1", taskId, jobId, "29cm", "search",
				10, 0, 4, requestedAt, taskId + "-idem",
				new SearchPayload("구두", page, 45, "ko-KR", "KRW",
						new SearchFilters(null, null, List.of(), List.of("270"), List.of(), true, Map.of())));
	}

	private Message receiveSearchTask() {
		Message message = rabbitTemplate.receive(
				CollectionQueueNames.SEARCH_TASK_QUEUE,
				Duration.ofSeconds(5).toMillis());
		assertThat(message).isNotNull();
		return message;
	}

	private CollectionTaskMessage readTask(Message message) throws Exception {
		return objectMapper.readValue(message.getBody(), CollectionTaskMessage.class);
	}

	private void purgeQueues() {
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_TASK_QUEUE, true);
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_RETRY_QUEUE, true);
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_DEAD_LETTER_QUEUE, true);
	}

	private void deleteTrackedJobs() {
		collectionTaskRepository.deleteAllInBatch();
		collectionJobRepository.deleteAllInBatch();
	}
}
