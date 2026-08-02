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

import com.purchasesearch.product_backend.collection.exception.InvalidCollectionResultMessageException;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService;
import com.purchasesearch.product_backend.collection.service.CollectionResultMessageService.ProcessingOutcome;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;

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
		String collectorResult = Files.readString(abcmartCollectorResultPath());
		String envelope = successfulEnvelope(collectorResult);

		publishResult(envelope);
		waitUntil(() -> offerSnapshotRepository.count() == 1, Duration.ofSeconds(10));

		assertThat(productRepository.count()).isEqualTo(1);
		assertThat(merchantProductRepository.count()).isEqualTo(1);
		assertThat(offerSnapshotRepository.count()).isEqualTo(1);
		assertThat(productOptionRepository.count()).isEqualTo(1);
		assertThat(evidenceRepository.count()).isEqualTo(1);
		assertThat(collectionSearchContextRepository.findById("backend-test-001"))
				.hasValueSatisfying(context -> assertThat(context.getSearchQuery()).isEqualTo("구두"));
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
		byte[] failedEnvelope = Files.readAllBytes(collectionFailedResultPath());

		ProcessingOutcome outcome = messageService.process(failedEnvelope);

		assertThat(outcome).isEqualTo(ProcessingOutcome.TASK_FAILED);
		assertThat(offerSnapshotRepository.count()).isZero();
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
		return """
				{
				  "schemaVersion":"1",
				  "taskId":"backend-test-001",
				  "jobId":"queue-test-job-001",
				  "status":"success",
				  "startedAt":"2026-08-02T16:00:00+09:00",
				  "completedAt":"2026-08-02T16:00:01+09:00",
				  "durationMs":1000,
				  "collectorResult":%s,
				  "error":null
				}
				""".formatted(collectorResult);
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
