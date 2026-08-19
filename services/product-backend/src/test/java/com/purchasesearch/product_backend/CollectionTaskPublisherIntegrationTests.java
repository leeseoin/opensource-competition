package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * CollectionTaskPublisherIntegrationTests는 HTTP 수집 요청이 실제 RabbitMQ 검색 Queue 계약으로 발행되는지 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CollectionTaskPublisherIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CollectionTaskRepository collectionTaskRepository;

	@Autowired
	private CollectionJobRepository collectionJobRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 각 테스트 전에 검색 Queue와 DLQ에 남은 메시지를 제거한다.
	 */
	@BeforeEach
	void prepareQueues() {
		purgeQueues();
		deleteTrackedJobs();
	}

	/**
	 * 테스트 종료 뒤 발행 메시지가 다른 테스트에 영향을 주지 않도록 제거한다.
	 */
	@AfterEach
	void cleanQueues() {
		purgeQueues();
		deleteTrackedJobs();
	}

	/**
	 * 최소 검색 요청을 보내면 기본값, 추적 ID와 SHA-256 멱등성 키가 포함된 persistent 작업이 발행되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void publishesSearchTaskThroughHttpEndpoint() throws Exception {
		MvcResult result = mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.merchant").value("29cm"))
				.andExpect(jsonPath("$.operation").value("search"))
				.andExpect(jsonPath("$.taskId").isNotEmpty())
				.andExpect(jsonPath("$.jobId").isNotEmpty())
				.andReturn();
		CollectionTaskResponse response = objectMapper.readValue(
				result.getResponse().getContentAsByteArray(),
				CollectionTaskResponse.class);

		mockMvc.perform(get("/internal/v1/collection-jobs/{jobId}", response.jobId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.taskCount").value(1))
				.andExpect(jsonPath("$.queuedTaskCount").value(1))
				.andExpect(jsonPath("$.productCount").value(0))
				.andExpect(jsonPath("$.verificationSummary.total").value(0))
				.andExpect(jsonPath("$.tasks[0].taskId").value(response.taskId()));

		Message message = receiveSearchTask();
		String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);
		CollectionTaskMessage task = objectMapper.readValue(message.getBody(), CollectionTaskMessage.class);

		assertThat(task.schemaVersion()).isEqualTo("1");
		assertThat(task.taskId()).startsWith("task-");
		assertThat(task.jobId()).startsWith("job-");
		assertThat(task.merchant()).isEqualTo("29cm");
		assertThat(task.operation()).isEqualTo("search");
		assertThat(task.priority()).isEqualTo(20);
		assertThat(task.attempt()).isZero();
		assertThat(task.maxAttempts()).isEqualTo(2);
		assertThat(task.idempotencyKey()).matches("^collection:v1:[a-f0-9]{64}$");
		assertThat(task.payload().query()).isEqualTo("구두");
		assertThat(task.payload().page()).isEqualTo(1);
		assertThat(task.payload().limit()).isEqualTo(30);
		assertThat(task.payload().locale()).isEqualTo("ko-KR");
		assertThat(task.payload().currency()).isEqualTo("KRW");
		assertThat(task.payload().filters().sizes()).containsExactly("270");
		assertThat(task.payload().filters().inStockOnly()).isTrue();
		assertThat(messageBody).doesNotContain("\"priceMin\":null", "\"priceMax\":null");
		assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(message.getMessageProperties().getPriority()).isEqualTo(20);
		assertThat(message.getMessageProperties().getMessageId()).isEqualTo(task.taskId());
	}

	/**
	 * 동일한 판매처와 검색 조건을 같은 작업이 아직 진행 중인 동안 다시 요청하면 409로 거절되고,
	 * 그 작업이 끝난 뒤에는 같은 멱등성 키로 다시 발행할 수 있는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void rejectsDuplicateWhileInFlightThenAllowsAfterCompletion() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted());
		CollectionTaskMessage first = readTask(receiveSearchTask());

		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_COLLECTION_TASK"));
		assertThat(rabbitTemplate.receive(CollectionQueueNames.SEARCH_TASK_QUEUE)).isNull();

		jdbcTemplate.update(
				"UPDATE collection_tasks SET status = 'SUCCESS' WHERE task_id = ?", first.taskId());

		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted());
		CollectionTaskMessage third = readTask(receiveSearchTask());

		assertThat(third.taskId()).isNotEqualTo(first.taskId());
		assertThat(third.jobId()).isNotEqualTo(first.jobId());
		assertThat(third.idempotencyKey()).isEqualTo(first.idempotencyKey());
	}

	/**
	 * 단일 작업 API에서 두 번째 페이지를 지정하면 해당 페이지가 Queue에 기록되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void publishesRequestedPage() throws Exception {
		String secondPageRequest = minimalRequest().replace("\"page\": 1", "\"page\": 2");

		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(secondPageRequest))
				.andExpect(status().isAccepted());

		CollectionTaskMessage task = readTask(receiveSearchTask());
		assertThat(task.payload().page()).isEqualTo(2);
	}

	/**
	 * 여러 페이지 API가 같은 jobId와 서로 다른 taskId로 연속 페이지 작업을 모두 발행하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void publishesConsecutivePagesWithSharedJobIdentifier() throws Exception {
		String request = """
				{
				  "merchant": "abcmart",
				  "query": "구두",
				  "startPage": 2,
				  "pageCount": 3,
				  "limit": 50
				}
				""";

		mockMvc.perform(post("/internal/v1/collection-tasks/pages")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.merchant").value("abcmart"))
				.andExpect(jsonPath("$.startPage").value(2))
				.andExpect(jsonPath("$.endPage").value(4))
				.andExpect(jsonPath("$.taskCount").value(3))
				.andExpect(jsonPath("$.jobId").isNotEmpty());

		CollectionTaskMessage first = readTask(receiveSearchTask());
		CollectionTaskMessage second = readTask(receiveSearchTask());
		CollectionTaskMessage third = readTask(receiveSearchTask());

		assertThat(first.payload().page()).isEqualTo(2);
		assertThat(second.payload().page()).isEqualTo(3);
		assertThat(third.payload().page()).isEqualTo(4);
		assertThat(first.jobId()).isEqualTo(second.jobId()).isEqualTo(third.jobId());
		assertThat(first.taskId()).isNotEqualTo(second.taskId()).isNotEqualTo(third.taskId());
		assertThat(first.idempotencyKey()).isNotEqualTo(second.idempotencyKey());
		assertThat(second.idempotencyKey()).isNotEqualTo(third.idempotencyKey());
	}

	/**
	 * 이미 다른 job으로 진행 중인 페이지는 건너뛰고 나머지 새 페이지만 발행하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void skipsPageAlreadyInFlightInAnotherJobAndPublishesTheRest() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest().replace("\"page\": 1", "\"page\": 2")))
				.andExpect(status().isAccepted());
		receiveSearchTask();

		String pagesRequest = """
				{
				  "merchant": "29cm",
				  "query": "구두",
				  "startPage": 2,
				  "pageCount": 2,
				  "filters": {
				    "sizes": ["270"],
				    "inStockOnly": true
				  }
				}
				""";

		mockMvc.perform(post("/internal/v1/collection-tasks/pages")
					.contentType(MediaType.APPLICATION_JSON)
					.content(pagesRequest))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.taskCount").value(1))
				.andExpect(jsonPath("$.skippedDuplicatePages").value(org.hamcrest.Matchers.contains(2)));

		CollectionTaskMessage onlyNewTask = readTask(receiveSearchTask());
		assertThat(onlyNewTask.payload().page()).isEqualTo(3);
	}

	/**
	 * 요청한 페이지가 모두 이미 진행 중이면 아무것도 발행하지 않고 409를 반환하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void rejectsPagesRequestWhenEveryPageIsAlreadyInFlight() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest().replace("\"page\": 1", "\"page\": 2")))
				.andExpect(status().isAccepted());
		receiveSearchTask();

		String pagesRequest = """
				{
				  "merchant": "29cm",
				  "query": "구두",
				  "startPage": 2,
				  "pageCount": 1,
				  "filters": {
				    "sizes": ["270"],
				    "inStockOnly": true
				  }
				}
				""";

		mockMvc.perform(post("/internal/v1/collection-tasks/pages")
					.contentType(MediaType.APPLICATION_JSON)
					.content(pagesRequest))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_COLLECTION_TASK"));
		assertThat(rabbitTemplate.receive(CollectionQueueNames.SEARCH_TASK_QUEUE)).isNull();
	}

	/**
	 * 여러 페이지 요청의 마지막 페이지가 200을 넘으면 작업을 하나도 발행하지 않는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsPageRangeBeyondMaximumBeforePublishing() throws Exception {
		String request = """
				{
				  "merchant": "abcmart",
				  "query": "구두",
				  "startPage": 199,
				  "pageCount": 3,
				  "limit": 50
				}
				""";

		mockMvc.perform(post("/internal/v1/collection-tasks/pages")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLLECTION_TASK"));

		assertThat(rabbitTemplate.receive(CollectionQueueNames.SEARCH_TASK_QUEUE)).isNull();
	}

	/**
	 * 존재하지 않는 jobId를 조회하면 안전한 오류 코드와 404를 반환하는지 검증한다.
	 *
	 * @throws Exception HTTP 조회에 실패한 경우
	 */
	@Test
	void returnsNotFoundForUnknownJob() throws Exception {
		mockMvc.perform(get("/internal/v1/collection-jobs/{jobId}", "job-does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COLLECTION_JOB_NOT_FOUND"));
	}

	/**
	 * 최소 검색 작업 API 요청 JSON을 반환한다.
	 *
	 * @return 29CM 구두 검색과 270 재고 필터 JSON
	 */
	private String minimalRequest() {
		return """
				{
				  "merchant": "29cm",
				  "query": "구두",
				  "page": 1,
				  "filters": {
				    "sizes": ["270"],
				    "inStockOnly": true
				  }
				}
				""";
	}

	/**
	 * 검색 Queue에서 제한 시간 안에 작업 메시지 한 건을 받는다.
	 *
	 * @return 수신한 검색 작업 메시지
	 */
	private Message receiveSearchTask() {
		Message message = rabbitTemplate.receive(
				CollectionQueueNames.SEARCH_TASK_QUEUE,
				Duration.ofSeconds(5).toMillis());
		assertThat(message).isNotNull();
		return message;
	}

	/**
	 * RabbitMQ 메시지 body를 CollectionTaskMessage로 해석한다.
	 *
	 * @param message Queue에서 수신한 메시지
	 * @return 해석한 작업 계약
	 * @throws Exception JSON 해석에 실패한 경우
	 */
	private CollectionTaskMessage readTask(Message message) throws Exception {
		return objectMapper.readValue(message.getBody(), CollectionTaskMessage.class);
	}

	/**
	 * 검색 작업, 재시도 및 DLQ Queue를 비동기로 정리한다.
	 */
	private void purgeQueues() {
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_TASK_QUEUE, true);
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_RETRY_QUEUE, true);
		rabbitAdmin.purgeQueue(CollectionQueueNames.SEARCH_DEAD_LETTER_QUEUE, true);
	}

	/**
	 * 외래키 순서에 맞춰 테스트에서 등록한 작업 추적 행을 제거한다.
	 */
	private void deleteTrackedJobs() {
		collectionTaskRepository.deleteAllInBatch();
		collectionJobRepository.deleteAllInBatch();
	}
}
