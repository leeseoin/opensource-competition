package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectorResult;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService.StoreReport;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;

import jakarta.validation.ConstraintViolationException;
import tools.jackson.databind.ObjectMapper;

/**
 * ProductStorageIntegrationTests는 실제 PostgreSQL에서 Flyway schema, CollectorResult
 * upsert, snapshot 이력 및 상품 검색 API를 함께 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductStorageIntegrationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CollectorResultStoreService collectorResultStoreService;

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

	@Autowired
	private ProductVerificationRepository productVerificationRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Flyway flyway;

	/**
	 * 실제 CollectorResult JSON을 내부 HTTP API에 전송하면 검증과 transaction 저장 후
	 * 저장 개수가 반환되는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void storesCollectorResultThroughHttpEndpoint() throws Exception {
		String collectorResultJson = Files.readString(abcmartFixturePath());

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectorResultJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productCount").value(1))
				.andExpect(jsonPath("$.snapshotCount").value(1))
				.andExpect(jsonPath("$.optionCount").value(1))
				.andExpect(jsonPath("$.evidenceCount").value(1))
				.andExpect(jsonPath("$.verificationCount").value(1));

		assertThat(productRepository.count()).isEqualTo(1);
		assertThat(collectionSearchContextRepository.count()).isEqualTo(1);
		assertThat(merchantProductRepository.count()).isEqualTo(1);
		assertThat(offerSnapshotRepository.count()).isEqualTo(1);
		assertThat(productOptionRepository.count()).isEqualTo(1);
		assertThat(evidenceRepository.count()).isEqualTo(1);
		assertThat(productVerificationRepository.count()).isEqualTo(1);
		assertThat(productVerificationRepository.findAll())
				.singleElement()
				.satisfies(verification -> {
					assertThat(verification.getStatus()).isEqualTo("MATCHED");
					assertThat(verification.getComparedFields()).contains("title", "price");
					assertThat(verification.getDifferences()).isEmpty();
				});
	}

	/**
	 * Go Collector가 false 기본 필드를 생략해 빈 filters 객체를 보내도 false로 정규화해 저장하는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void storesCollectorResultWithEmptyFilters() throws Exception {
		String collectorResultJson = Files.readString(abcmartFixturePath())
				.replace("""
						  "filters": {
						    "sizes": ["270"],
						    "inStockOnly": true
						  },
						""", "  \"filters\": {},\n");

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectorResultJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productCount").value(1));

		assertThat(collectionSearchContextRepository.findById("backend-test-001"))
				.hasValueSatisfying(context -> assertThat(context.getFilters())
						.containsEntry("inStockOnly", false));
	}

	/**
	 * 저장 대상이 아닌 차단 상태의 CollectorResult는 상품을 저장하지 않고 400 오류를
	 * 반환하는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsNonStorableCollectorResultThroughHttpEndpoint() throws Exception {
		String blockedResultJson = Files.readString(abcmartFixturePath())
				.replace("\"status\": \"success\"", "\"status\": \"blocked\"");

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(blockedResultJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLLECTOR_RESULT"));

		assertThat(productRepository.count()).isZero();
		assertThat(merchantProductRepository.count()).isZero();
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 필수 requestId가 비어 있는 HTTP 요청은 Bean Validation에서 400으로 거절하고
	 * transaction 저장을 시작하지 않는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsContractViolationThroughHttpEndpoint() throws Exception {
		String invalidResultJson = Files.readString(abcmartFixturePath())
				.replace("\"requestId\": \"backend-test-001\"", "\"requestId\": \"\"");

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidResultJson))
				.andExpect(status().isBadRequest());

		assertThat(productRepository.count()).isZero();
		assertThat(merchantProductRepository.count()).isZero();
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 같은 판매처 상품을 두 번 저장하면 검색 문맥과 상품은 중복되지 않고 snapshot, 옵션과
	 * 근거만 이력으로 추가되며 상품명에 없는 원본 검색어로도 조회되는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 검증에 실패한 경우
	 */
	@Test
	void storesCollectorResultAndReturnsLatestProductWithoutDuplicatingProduct() throws Exception {
		CollectorResult result = loadAbcmartCollectorResult();

		StoreReport firstReport = collectorResultStoreService.store(result);
		StoreReport secondReport = collectorResultStoreService.store(result);

		assertThat(firstReport.productCount()).isEqualTo(1);
		assertThat(firstReport.snapshotCount()).isEqualTo(1);
		assertThat(firstReport.optionCount()).isEqualTo(1);
		assertThat(firstReport.evidenceCount()).isEqualTo(1);
		assertThat(firstReport.verificationCount()).isEqualTo(1);
		assertThat(secondReport).isEqualTo(firstReport);
		assertThat(productRepository.count()).isEqualTo(1);
		assertThat(collectionSearchContextRepository.count()).isEqualTo(1);
		assertThat(merchantProductRepository.count()).isEqualTo(1);
		assertThat(offerSnapshotRepository.count()).isEqualTo(2);
		assertThat(productOptionRepository.count()).isEqualTo(2);
		assertThat(evidenceRepository.count()).isEqualTo(2);
		assertThat(productVerificationRepository.count()).isEqualTo(2);

		mockMvc.perform(get("/internal/v1/products")
						.param("merchant", "abcmart")
						.param("query", "구두")
						.param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.products[0].externalId").value("1010110882"))
				.andExpect(jsonPath("$.products[0].name").value("페니 로퍼"))
				.andExpect(jsonPath("$.products[0].price.amount").value(69000))
				.andExpect(jsonPath("$.products[0].stockStatus").value("available"))
				.andExpect(jsonPath("$.products[0].options[0].size").value("270"))
				.andExpect(jsonPath("$.products[0].source.collectorVersion").value("abcmart-search-v2"));

		assertThat(collectionSearchContextRepository.findById("backend-test-001"))
				.hasValueSatisfying(context -> {
					assertThat(context.getSearchQuery()).isEqualTo("구두");
					assertThat(context.getFilters())
							.containsEntry("inStockOnly", true)
							.containsEntry("sizes", java.util.List.of("270"));
				});
	}

	/**
	 * search 결과에 query가 없으면 검색 문맥 없는 snapshot을 만들지 않고 400으로
	 * 거절하는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsSearchResultWithoutQueryContext() throws Exception {
		String resultWithoutQuery = Files.readString(abcmartFixturePath())
				.replace("\"query\": \"구두\",", "");

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(resultWithoutQuery))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLLECTOR_RESULT"));

		assertThat(collectionSearchContextRepository.count()).isZero();
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 필수 requestId가 비어 있는 CollectorResult는 transaction 시작 후 저장되지 않는지
	 * 검증한다.
	 *
	 * @throws Exception fixture를 읽거나 JSON을 변환할 수 없는 경우
	 */
	@Test
	void rejectsInvalidCollectorResultBeforeSavingAnyRows() throws Exception {
		String invalidJson = Files.readString(abcmartFixturePath())
				.replace("\"requestId\": \"backend-test-001\"", "\"requestId\": \"\"");
		CollectorResult invalidResult = objectMapper.readValue(invalidJson, CollectorResult.class);

		assertThatThrownBy(() -> collectorResultStoreService.store(invalidResult))
				.isInstanceOf(ConstraintViolationException.class);
		assertThat(productRepository.count()).isZero();
		assertThat(merchantProductRepository.count()).isZero();
		assertThat(offerSnapshotRepository.count()).isZero();
	}

	/**
	 * 적용 완료된 Flyway migration을 다시 실행해도 추가 변경이 없고 health endpoint가
	 * 정상인지 검증한다.
	 *
	 * @throws Exception HTTP health 검증에 실패한 경우
	 */
	@Test
	void keepsMigrationIdempotentAndExposesHealthEndpoint() throws Exception {
		assertThat(flyway.migrate().migrationsExecuted).isZero();

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	/**
	 * OpenAPI JSON에 수동 적재와 상품 조회 경로 및 짧은 페이지 수집 예시가 포함되고
	 * Swagger UI 진입 주소가 정상적으로 제공되는지 검증한다.
	 *
	 * @throws Exception OpenAPI 또는 Swagger UI 요청에 실패한 경우
	 */
	@Test
	void exposesOpenApiDocumentAndSwaggerUi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title")
						.value("Purchase Research Product Backend API"))
				.andExpect(jsonPath("$.paths['/internal/v1/collection-results'].post")
						.exists())
				.andExpect(jsonPath("$.paths['/internal/v1/products'].get")
						.exists())
				.andExpect(jsonPath("$.paths['/internal/v1/collection-jobs/{jobId}'].get")
						.exists())
				.andExpect(jsonPath("$.paths['/internal/v1/collection-tasks/pages'].post"
						+ ".requestBody.content['application/json'].examples"
						+ "['ABC마트 1페이지 소량 수집'].value.merchant")
						.value("abcmart"))
				.andExpect(jsonPath("$.paths['/internal/v1/collection-tasks/pages'].post"
						+ ".requestBody.content['application/json'].examples"
						+ "['ABC마트 1페이지 소량 수집'].value.limit")
						.value(3));

		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}

	/**
	 * 저장소의 ABC마트 CollectorResult 계약 예제를 Java DTO로 읽는다.
	 *
	 * @return 검증과 저장에 사용할 CollectorResult
	 * @throws Exception fixture 파일을 읽거나 JSON을 변환할 수 없는 경우
	 */
	private CollectorResult loadAbcmartCollectorResult() throws Exception {
		return objectMapper.readValue(Files.readString(abcmartFixturePath()), CollectorResult.class);
	}

	/**
	 * 저장소의 ABC마트 CollectorResult 계약 예제 경로를 계산한다.
	 *
	 * @return 절대 경로로 정규화한 fixture 위치
	 */
	private Path abcmartFixturePath() {
		return Path.of(
				System.getProperty("user.dir"),
				"..",
				"..",
				"contracts",
				"collector",
				"v1",
				"examples",
				"collector-result.abcmart-success.json").normalize();
	}
}
