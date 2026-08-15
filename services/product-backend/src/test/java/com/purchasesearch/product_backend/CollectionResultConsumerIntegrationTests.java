package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionResultMessageException;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService;
import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService.ProcessingOutcome;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * CollectionResultConsumerIntegrationTests는 실제 RabbitMQ와 PostgreSQL에서 결과 ACK, 저장과 DLQ 흐름을 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CollectionResultConsumerIntegrationTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private CollectionResultMessageService messageService;

	@Autowired
	private CollectionJobService collectionJobService;

	@Autowired
	private CollectionTaskRepository collectionTaskRepository;

	@Autowired
	private CollectionJobRepository collectionJobRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CollectionSearchContextRepository collectionSearchContextRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MerchantProductRepository merchantProductRepository;

	@Autowired
	private OfferSnapshotRepository offerSnapshotRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private EvidenceRepository evidenceRepository;

	/**
	 * 각 테스트가 독립적인 Queue와 DB 상태에서 시작하도록 기존 데이터를 제거한다.
	 */
	@BeforeEach
	void prepareState() {
		purgeQueues();
		deleteStoredProducts();
	}

	/**
	 * 비동기 Consumer 처리가 끝난 뒤 다음 테스트에 영향을 주지 않도록 데이터를 제거한다.
	 */
	@AfterEach
	void cleanState() {
		purgeQueues();
		deleteStoredProducts();
	}

	/**
	 * 성공 Queue 결과를 발행하면 Consumer가 ACK하고 기존 저장 서비스를 통해 상품과 검색 문맥을 저장하는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 비동기 처리 대기에 실패한 경우
	 */
	@Test
	void consumesSuccessfulResultAndStoresProducts() throws Exception {
		String collectorResult = Files.readString(abcmartCollectorResultPath())
				.replace("\r\n", "\n")
				.replace("""
						  "filters": {
						    "sizes": ["270"],
						    "inStockOnly": true
						  },
						""", "  \"filters\": {},\n");
		String envelope = successfulEnvelope(collectorResult);

		publishResult(envelope);
		waitUntil(() -> offerSnapshotRepository.count() == 1, Duration.ofSeconds(10));

		assertThat(productRepository.count()).isEqualTo(1);
		assertThat(merchantProductRepository.count()).isEqualTo(1);
		assertThat(offerSnapshotRepository.count()).isEqualTo(1);
		assertThat(productOptionRepository.count()).isEqualTo(1);
		assertThat(evidenceRepository.count()).isEqualTo(1);
		assertThat(collectionSearchContextRepository.findById("backend-test-001"))
				.hasValueSatisfying(context -> {
					assertThat(context.getSearchQuery()).isEqualTo("구두");
					assertThat(context.getFilters()).containsEntry("inStockOnly", false);
				});
	}

	/**
	 * 추적 중인 성공 결과가 job 완료 상태, 상품 수와 verificationSummary에 반영되는지 검증한다.
	 *
	 * @throws Exception Queue 계약 fixture를 읽거나 결과를 처리하지 못한 경우
	 */
	@Test
	void updatesTrackedJobWithStoredProductAndVerificationCounts() throws Exception {
		CollectionTaskMessage task = trackedTask("backend-test-001", "queue-test-job-001");
		collectionJobService.register(java.util.List.of(task));

		ProcessingOutcome outcome = messageService.process(
				successfulEnvelope(normalizedCollectorResult(), task.taskId())
						.getBytes(StandardCharsets.UTF_8));
		CollectionJobResponse job = collectionJobService.get(task.jobId());

		assertThat(outcome).isEqualTo(ProcessingOutcome.STORED);
		assertThat(job.status()).isEqualTo("COMPLETED");
		assertThat(job.queuedTaskCount()).isZero();
		assertThat(job.succeededTaskCount()).isEqualTo(1);
		assertThat(job.productCount()).isEqualTo(1);
		assertThat(job.verificationSummary().total()).isEqualTo(1);
		assertThat(job.verificationSummary().matched()).isEqualTo(1);
		assertThat(job.tasks()).singleElement().satisfies(taskStatus -> {
			assertThat(taskStatus.status()).isEqualTo("SUCCESS");
			assertThat(taskStatus.productCount()).isEqualTo(1);
		});
	}

	/**
	 * running 상태가 상품 저장 없이 task와 job을 RUNNING으로 바꾸고 최종 성공으로 이어지는지 검증한다.
	 *
	 * @throws Exception 최종 CollectorResult fixture를 읽거나 처리하지 못한 경우
	 */
	@Test
	void recordsRunningBeforeFinalResult() throws Exception {
		CollectionTaskMessage task = trackedTask("backend-test-001", "queue-test-job-running");
		collectionJobService.register(java.util.List.of(task));

		ProcessingOutcome runningOutcome = messageService.process(
				runningEnvelope(task.taskId(), task.jobId()).getBytes(StandardCharsets.UTF_8));
		CollectionJobResponse runningJob = collectionJobService.get(task.jobId());

		assertThat(runningOutcome).isEqualTo(ProcessingOutcome.TASK_RUNNING);
		assertThat(runningJob.status()).isEqualTo("RUNNING");
		assertThat(runningJob.queuedTaskCount()).isZero();
		assertThat(runningJob.runningTaskCount()).isEqualTo(1);
		assertThat(runningJob.completedAt()).isNull();
		assertThat(runningJob.tasks()).singleElement().satisfies(taskStatus -> {
			assertThat(taskStatus.status()).isEqualTo("RUNNING");
			assertThat(taskStatus.startedAt()).isNotNull();
			assertThat(taskStatus.completedAt()).isNull();
		});
		assertThat(productRepository.count()).isZero();

		ProcessingOutcome finalOutcome = messageService.process(
				successfulEnvelope(normalizedCollectorResult(), task.taskId(), task.jobId())
						.getBytes(StandardCharsets.UTF_8));
		CollectionJobResponse completedJob = collectionJobService.get(task.jobId());

		assertThat(finalOutcome).isEqualTo(ProcessingOutcome.STORED);
		assertThat(completedJob.status()).isEqualTo("COMPLETED");
		assertThat(completedJob.runningTaskCount()).isZero();
		assertThat(completedJob.succeededTaskCount()).isEqualTo(1);
	}

	/**
	 * 최종 결과 뒤 늦게 재전달된 running 상태가 완료 작업을 RUNNING으로 되돌리지 않는지 검증한다.
	 *
	 * @throws Exception CollectorResult fixture를 읽거나 처리하지 못한 경우
	 */
	@Test
	void ignoresLateRunningEventAfterCompletion() throws Exception {
		CollectionTaskMessage task = trackedTask("backend-test-001", "queue-test-job-late-running");
		collectionJobService.register(java.util.List.of(task));
		messageService.process(successfulEnvelope(normalizedCollectorResult(), task.taskId(), task.jobId())
				.getBytes(StandardCharsets.UTF_8));

		ProcessingOutcome outcome = messageService.process(
				runningEnvelope(task.taskId(), task.jobId()).getBytes(StandardCharsets.UTF_8));
		CollectionJobResponse job = collectionJobService.get(task.jobId());

		assertThat(outcome).isEqualTo(ProcessingOutcome.TASK_RUNNING);
		assertThat(job.status()).isEqualTo("COMPLETED");
		assertThat(job.runningTaskCount()).isZero();
		assertThat(job.tasks().getFirst().status()).isEqualTo("SUCCESS");
	}

	/**
	 * running 상태에 완료 정보가 포함되면 상태와 상품을 변경하지 않고 계약 위반으로 거부하는지 검증한다.
	 */
	@Test
	void rejectsRunningEventWithCompletionData() throws Exception {
		CollectionTaskMessage task = trackedTask("backend-test-001", "queue-test-job-invalid-running");
		collectionJobService.register(java.util.List.of(task));
		String invalid = runningEnvelope(task.taskId(), task.jobId())
				.replace("\"completedAt\":null", "\"completedAt\":\"2026-08-02T16:00:01+09:00\"")
				.replace("\"durationMs\":null", "\"durationMs\":1000");

		assertThatThrownBy(() -> messageService.process(invalid.getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(InvalidCollectionResultMessageException.class)
				.hasMessageContaining("running");
		assertThat(collectionJobService.get(task.jobId()).status()).isEqualTo("QUEUED");
		assertThat(productRepository.count()).isZero();
	}

	/**
	 * 여러 페이지 중 한 작업의 발행이 실패하고 다른 작업이 성공하면 job이 PARTIAL로 종료되는지 검증한다.
	 *
	 * @throws Exception Queue 계약 fixture를 읽거나 성공 결과를 처리하지 못한 경우
	 */
	@Test
	void completesJobAsPartialWhenOnePageCouldNotBePublished() throws Exception {
		String jobId = "queue-test-job-partial";
		CollectionTaskMessage first = trackedTask("backend-test-001", jobId, 1);
		CollectionTaskMessage second = trackedTask("backend-test-002", jobId, 2);
		collectionJobService.register(java.util.List.of(first, second));
		collectionJobService.markPublishFailed(
				java.util.List.of(second.taskId()),
				"RabbitMQ 확인 실패");

		assertThat(collectionJobService.get(jobId).status()).isEqualTo("PROCESSING");
		messageService.process(
				successfulEnvelope(normalizedCollectorResult(), first.taskId(), jobId)
						.getBytes(StandardCharsets.UTF_8));
		CollectionJobResponse job = collectionJobService.get(jobId);

		assertThat(job.status()).isEqualTo("PARTIAL");
		assertThat(job.succeededTaskCount()).isEqualTo(1);
		assertThat(job.failedTaskCount()).isEqualTo(1);
		assertThat(job.productCount()).isEqualTo(1);
		assertThat(job.tasks().get(1).status()).isEqualTo("PUBLISH_FAILED");
		assertThat(job.tasks().get(1).error().code()).isEqualTo("RABBITMQ_PUBLISH_FAILED");
	}

	/**
	 * 같은 검색어의 서로 다른 페이지 결과가 각각의 요청 문맥과 상품 snapshot으로 누적 저장되는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 결과 JSON 처리에 실패한 경우
	 */
	@Test
	void storesProductsFromMultiplePageTasks() throws Exception {
		String firstResult = normalizedCollectorResult();
		String secondResult = normalizedCollectorResult()
				.replace("backend-test-001", "backend-test-002")
				.replace("1010110882", "1010110999")
				.replace("페니 로퍼", "두 번째 페이지 로퍼");

		ProcessingOutcome firstOutcome = messageService.process(
				successfulEnvelope(firstResult, "backend-test-001").getBytes(StandardCharsets.UTF_8));
		ProcessingOutcome secondOutcome = messageService.process(
				successfulEnvelope(secondResult, "backend-test-002").getBytes(StandardCharsets.UTF_8));

		assertThat(firstOutcome).isEqualTo(ProcessingOutcome.STORED);
		assertThat(secondOutcome).isEqualTo(ProcessingOutcome.STORED);
		assertThat(collectionSearchContextRepository.count()).isEqualTo(2);
		assertThat(productRepository.count()).isEqualTo(2);
		assertThat(merchantProductRepository.count()).isEqualTo(2);
		assertThat(offerSnapshotRepository.count()).isEqualTo(2);
		assertThat(productOptionRepository.count()).isEqualTo(2);
		assertThat(evidenceRepository.count()).isEqualTo(2);
	}

	/**
	 * 필수 필드가 없는 Queue 결과는 저장하지 않고 RabbitMQ 결과 DLQ로 이동하는지 검증한다.
	 */
	@Test
	void rejectsInvalidResultToDeadLetterQueue() {
		String invalidEnvelope = "{\"schemaVersion\":\"1\"}";

		publishResult(invalidEnvelope);
		Message deadLetter = rabbitTemplate.receive(
				CollectionQueueNames.RESULT_DEAD_LETTER_QUEUE,
				Duration.ofSeconds(10).toMillis());

		assertThat(deadLetter).isNotNull();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.isEqualTo(invalidEnvelope);
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 정상적인 failed 결과는 상품 저장 없이 처리 완료 상태로 반환하는지 검증한다.
	 *
	 * @throws Exception 실패 결과 fixture 읽기에 실패한 경우
	 */
	@Test
	void acknowledgesValidFailedResultWithoutSavingProducts() throws Exception {
		CollectionTaskMessage task = trackedTask("task-20260726-001", "job-20260726-001");
		collectionJobService.register(java.util.List.of(task));
		byte[] failedEnvelope = Files.readAllBytes(collectionFailedResultPath());

		ProcessingOutcome outcome = messageService.process(failedEnvelope);
		CollectionJobResponse job = collectionJobService.get(task.jobId());

		assertThat(outcome).isEqualTo(ProcessingOutcome.TASK_FAILED);
		assertThat(offerSnapshotRepository.count()).isZero();
		assertThat(job.status()).isEqualTo("FAILED");
		assertThat(job.failedTaskCount()).isEqualTo(1);
		assertThat(job.tasks().getFirst().error().code()).isEqualTo("COLLECTOR_TIMEOUT");
	}

	/**
	 * Queue taskId와 내부 Collector requestId가 다르면 다른 작업 결과의 오저장을 막는지 검증한다.
	 *
	 * @throws Exception CollectorResult fixture 읽기에 실패한 경우
	 */
	@Test
	void rejectsMismatchedTaskAndRequestIdentifiers() throws Exception {
		String collectorResult = Files.readString(abcmartCollectorResultPath());
		String envelope = successfulEnvelope(collectorResult)
				.replace("\"taskId\":\"backend-test-001\"", "\"taskId\":\"different-task\"");

		assertThatThrownBy(() -> messageService.process(envelope.getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(InvalidCollectionResultMessageException.class)
				.hasMessageContaining("taskId");
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 등록된 taskId가 같아도 결과의 jobId가 다르면 상품과 상태를 함께 저장하지 않는지 검증한다.
	 *
	 * @throws Exception CollectorResult fixture 읽기 또는 작업 등록에 실패한 경우
	 */
	@Test
	void rejectsResultWithMismatchedTrackedJobIdentifier() throws Exception {
		CollectionTaskMessage task = trackedTask("backend-test-001", "queue-test-job-001");
		collectionJobService.register(java.util.List.of(task));
		String envelope = successfulEnvelope(
				normalizedCollectorResult(),
				task.taskId(),
				"different-job");

		assertThatThrownBy(() -> messageService.process(envelope.getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(InvalidCollectionResultMessageException.class)
				.hasMessageContaining("jobId");
		assertThat(offerSnapshotRepository.count()).isZero();
		assertThat(collectionJobService.get(task.jobId()).status()).isEqualTo("QUEUED");
	}

	/**
	 * 결과 JSON을 persistent RabbitMQ message로 수집 결과 exchange에 발행한다.
	 *
	 * @param json 발행할 CollectionResult JSON
	 */
	private void publishResult(String json) {
		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT)
				.build();
		rabbitTemplate.send(
				CollectionQueueNames.COLLECTION_EXCHANGE,
				CollectionQueueNames.RESULT_ROUTING_KEY,
				message);
	}

	/**
	 * 실제 CollectorResult fixture를 포함한 성공 Queue 봉투를 생성한다.
	 *
	 * @param collectorResult 공통 CollectorResult JSON
	 * @return RabbitMQ에 발행할 성공 결과 JSON
	 */
	private String successfulEnvelope(String collectorResult) {
		return successfulEnvelope(collectorResult, "backend-test-001");
	}

	/**
	 * 지정한 taskId와 일치하는 성공 Queue 봉투를 생성한다.
	 *
	 * @param collectorResult 공통 CollectorResult JSON
	 * @param taskId Queue 작업과 Collector requestId에 함께 사용할 식별자
	 * @return RabbitMQ에 발행하거나 직접 처리할 성공 결과 JSON
	 */
	private String successfulEnvelope(String collectorResult, String taskId) {
		return successfulEnvelope(collectorResult, taskId, "queue-test-job-001");
	}

	/**
	 * 지정한 taskId와 jobId에 일치하는 성공 Queue 봉투를 생성한다.
	 *
	 * @param collectorResult 공통 CollectorResult JSON
	 * @param taskId Queue 작업과 Collector requestId에 함께 사용할 식별자
	 * @param jobId 추적 중인 상위 job 식별자
	 * @return RabbitMQ에 발행하거나 직접 처리할 성공 결과 JSON
	 */
	private String successfulEnvelope(String collectorResult, String taskId, String jobId) {
		return """
				{
				  "schemaVersion":"1",
				  "taskId":"%s",
				  "jobId":"%s",
				  "status":"success",
				  "startedAt":"2026-08-02T16:00:00+09:00",
				  "completedAt":"2026-08-02T16:00:01+09:00",
				  "durationMs":1000,
				  "collectorResult":%s,
				  "error":null
				}
				""".formatted(taskId, jobId, collectorResult);
	}

	/**
	 * Worker가 작업을 소비한 직후 발행할 running Queue 봉투를 생성한다.
	 *
	 * @param taskId 시작한 페이지 작업 식별자
	 * @param jobId 추적 중인 상위 job 식별자
	 * @return 상품과 완료 정보가 없는 running JSON
	 */
	private String runningEnvelope(String taskId, String jobId) {
		return """
				{
				  "schemaVersion":"1",
				  "taskId":"%s",
				  "jobId":"%s",
				  "status":"running",
				  "startedAt":"2026-08-02T16:00:00+09:00",
				  "completedAt":null,
				  "durationMs":null,
				  "collectorResult":null,
				  "error":null
				}
				""".formatted(taskId, jobId);
	}

	/**
	 * 저장 계약 검증에 맞게 선택 필터를 빈 필터로 바꾼 ABC마트 fixture를 반환한다.
	 *
	 * @return DB 저장 테스트용 CollectorResult JSON
	 * @throws Exception fixture 읽기에 실패한 경우
	 */
	private String normalizedCollectorResult() throws Exception {
		return Files.readString(abcmartCollectorResultPath())
				.replace("\r\n", "\n")
				.replace("""
					  "filters": {
					    "sizes": ["270"],
					    "inStockOnly": true
					  },
					""", "  \"filters\": {},\n");
	}

	/**
	 * Queue 작업 fixture를 지정한 taskId와 jobId의 추적 작업으로 변환한다.
	 *
	 * @param taskId 결과 봉투와 맞출 작업 식별자
	 * @param jobId 상위 job 식별자
	 * @return 작업 상태 DB에 등록할 Queue 메시지
	 * @throws Exception fixture 읽기 또는 JSON 해석에 실패한 경우
	 */
	private CollectionTaskMessage trackedTask(String taskId, String jobId) throws Exception {
		return trackedTask(taskId, jobId, 1);
	}

	/**
	 * Queue 작업 fixture를 지정한 식별자와 페이지의 추적 작업으로 변환한다.
	 *
	 * @param taskId 결과 봉투와 맞출 작업 식별자
	 * @param jobId 상위 job 식별자
	 * @param page 검색 페이지
	 * @return 작업 상태 DB에 등록할 Queue 메시지
	 * @throws Exception fixture 읽기 또는 JSON 해석에 실패한 경우
	 */
	private CollectionTaskMessage trackedTask(String taskId, String jobId, int page) throws Exception {
		String json = Files.readString(collectionTaskPath())
				.replace("task-20260726-001", taskId)
				.replace("job-20260726-001", jobId)
				.replace("\"page\": 1", "\"page\": " + page);
		return objectMapper.readValue(json, CollectionTaskMessage.class);
	}

	/**
	 * 조건이 제한 시간 안에 만족될 때까지 짧은 간격으로 확인한다.
	 *
	 * @param condition 완료 조건
	 * @param timeout 최대 대기 시간
	 * @throws InterruptedException 대기 중 테스트 thread가 중단된 경우
	 */
	private void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("RabbitMQ 결과가 제한 시간 안에 처리되지 않았습니다.");
	}

	/**
	 * 테스트 대상 결과 Queue와 DLQ에 남은 메시지를 제거한다.
	 */
	private void purgeQueues() {
		rabbitAdmin.purgeQueue(CollectionQueueNames.RESULT_QUEUE, true);
		rabbitAdmin.purgeQueue(CollectionQueueNames.RESULT_DEAD_LETTER_QUEUE, true);
	}

	/**
	 * 외래키 순서에 맞춰 테스트에서 저장한 상품 관련 행을 제거한다.
	 */
	private void deleteStoredProducts() {
		evidenceRepository.deleteAllInBatch();
		productOptionRepository.deleteAllInBatch();
		offerSnapshotRepository.deleteAllInBatch();
		merchantProductRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();
		collectionSearchContextRepository.deleteAllInBatch();
		collectionTaskRepository.deleteAllInBatch();
		collectionJobRepository.deleteAllInBatch();
	}

	/**
	 * ABC마트 CollectorResult fixture 경로를 반환한다.
	 *
	 * @return 저장 성공 테스트용 fixture 경로
	 */
	private Path abcmartCollectorResultPath() {
		return repositoryPath("contracts", "collector", "v1", "examples", "collector-result.abcmart-success.json");
	}

	/**
	 * 실패 CollectionResult fixture 경로를 반환한다.
	 *
	 * @return 정상 작업 실패 테스트용 fixture 경로
	 */
	private Path collectionFailedResultPath() {
		return repositoryPath("contracts", "collection", "v1", "examples", "collection-result.failed.json");
	}

	/**
	 * 성공 및 실패 job 상태 테스트에 사용할 CollectionTask fixture 경로를 반환한다.
	 *
	 * @return 검색 작업 계약 예제 경로
	 */
	private Path collectionTaskPath() {
		return repositoryPath("contracts", "collection", "v1", "examples", "collection-task.search.json");
	}

	/**
	 * Product Backend module 기준으로 저장소 파일의 절대 경로를 계산한다.
	 *
	 * @param parts 저장소 root 아래 경로 조각
	 * @return 정규화된 fixture 경로
	 */
	private Path repositoryPath(String... parts) {
		Path root = Path.of(System.getProperty("user.dir"), "..", "..").normalize();
		return root.resolve(Path.of("", parts));
	}
}
