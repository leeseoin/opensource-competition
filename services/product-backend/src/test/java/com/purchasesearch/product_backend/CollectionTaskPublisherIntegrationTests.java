package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;

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

	/**
	 * 각 테스트 전에 검색 Queue와 DLQ에 남은 메시지를 제거한다.
	 */
	@BeforeEach
	void prepareQueues() {
		purgeQueues();
	}

	/**
	 * 테스트 종료 뒤 발행 메시지가 다른 테스트에 영향을 주지 않도록 제거한다.
	 */
	@AfterEach
	void cleanQueues() {
		purgeQueues();
	}

	/**
	 * 최소 검색 요청을 보내면 기본값, 추적 ID와 SHA-256 멱등성 키가 포함된 persistent 작업이 발행되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void publishesSearchTaskThroughHttpEndpoint() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.merchant").value("29cm"))
				.andExpect(jsonPath("$.operation").value("search"))
				.andExpect(jsonPath("$.taskId").isNotEmpty())
				.andExpect(jsonPath("$.jobId").isNotEmpty());

		Message message = receiveSearchTask();
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
		assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(message.getMessageProperties().getPriority()).isEqualTo(20);
		assertThat(message.getMessageProperties().getMessageId()).isEqualTo(task.taskId());
	}

	/**
	 * 동일한 판매처와 검색 조건을 두 번 요청하면 실행 ID는 달라도 멱등성 키는 같은지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 수신 또는 JSON 해석에 실패한 경우
	 */
	@Test
	void createsStableIdempotencyKeyForSameSearchConditions() throws Exception {
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted());
		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(minimalRequest()))
				.andExpect(status().isAccepted());

		CollectionTaskMessage first = readTask(receiveSearchTask());
		CollectionTaskMessage second = readTask(receiveSearchTask());

		assertThat(first.taskId()).isNotEqualTo(second.taskId());
		assertThat(first.jobId()).isNotEqualTo(second.jobId());
		assertThat(first.idempotencyKey()).isEqualTo(second.idempotencyKey());
	}

	/**
	 * 현재 Worker가 지원하지 않는 두 번째 페이지 요청은 Queue 발행 전에 400으로 거절하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsUnsupportedPageBeforePublishing() throws Exception {
		String invalidRequest = minimalRequest().replace("\"page\": 1", "\"page\": 2");

		mockMvc.perform(post("/internal/v1/collection-tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content(invalidRequest))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLLECTION_TASK"));

		assertThat(rabbitTemplate.receive(CollectionQueueNames.SEARCH_TASK_QUEUE)).isNull();
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
}
