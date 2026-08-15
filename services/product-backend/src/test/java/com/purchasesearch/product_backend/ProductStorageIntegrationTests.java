package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectorResult;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.repository.StaleSearchContext;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService.StoreReport;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument;
import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument.WikiClaimDocument;
import com.purchasesearch.product_backend.knowledge.service.WikiConceptIndexService;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;
import com.purchasesearch.product_backend.research.repository.ResearchSessionRepository;

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
	private ResearchSessionRepository researchSessionRepository;

	@Autowired
	private AgentRunRepository agentRunRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WikiConceptIndexService wikiConceptIndexService;

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
				.replace("\r\n", "\n")
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
	 * 조건부 수집 최신성 조회가 상품 이름이나 카테고리가 아닌 동일 판매처, 검색어와
	 * 기본 필터의 실제 수집 문맥만 사용하는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void findsLatestCollectionOnlyForExactDefaultSearchScope() throws Exception {
		String collectorResultJson = Files.readString(abcmartFixturePath())
				.replace("\r\n", "\n")
				.replace("""
					  "filters": {
					    "sizes": ["270"],
					    "inStockOnly": true
					  },
					""", "  \"filters\": {},\n");

		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectorResultJson))
				.andExpect(status().isOk());

		assertThat(collectionSearchContextRepository
				.findLatestDefaultSearchCollectedAt("abcmart", " 구두 "))
				.contains(OffsetDateTime.parse("2026-07-30T15:00:00+09:00").toInstant());
		assertThat(collectionSearchContextRepository
				.findLatestDefaultSearchCollectedAt("abcmart", "페니 로퍼"))
				.isEmpty();
		assertThat(collectionSearchContextRepository
				.findLatestDefaultSearchCollectedAt("29cm", "구두"))
				.isEmpty();
	}

	/**
	 * 사전 갱신 배치 조회가 기본 필터로 수집된 조합만 마지막 수집이 오래된 순으로 반환하고,
	 * 기준 시각보다 최신인 조합은 제외하며, batchSize를 넘지 않는지 검증한다.
	 *
	 * @throws Exception fixture 읽기 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void findsStaleDefaultSearchContextsOldestFirstWithinBatchSize() throws Exception {
		String collectorResultJson = Files.readString(abcmartFixturePath())
				.replace("\r\n", "\n")
				.replace("""
						  "filters": {
						    "sizes": ["270"],
						    "inStockOnly": true
						  },
						""", "  \"filters\": {},\n");
		mockMvc.perform(post("/internal/v1/collection-results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectorResultJson))
				.andExpect(status().isOk());

		insertDefaultSearchContext("stale-context-002", "29cm", "로퍼", Instant.now().minus(Duration.ofHours(2)));
		insertDefaultSearchContext("fresh-context-003", "29cm", "슬리퍼", Instant.now());

		Instant staleBoundary = Instant.now().minus(Duration.ofHours(1));

		List<StaleSearchContext> firstPage = collectionSearchContextRepository
				.findStaleDefaultSearchContexts(staleBoundary, 1);
		assertThat(firstPage).singleElement().satisfies(context -> {
			assertThat(context.getMerchant()).isEqualTo("abcmart");
			assertThat(context.getSearchQuery()).isEqualTo("구두");
		});

		List<StaleSearchContext> allStale = collectionSearchContextRepository
				.findStaleDefaultSearchContexts(staleBoundary, 10);
		assertThat(allStale)
				.extracting(StaleSearchContext::getMerchant, StaleSearchContext::getSearchQuery)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("abcmart", "구두"),
						org.assertj.core.groups.Tuple.tuple("29cm", "로퍼"));
	}

	/**
	 * 사전 갱신 배치 조회 테스트 전용으로 기본 필터의 검색 문맥 한 건을 직접 삽입한다.
	 *
	 * @param requestId 고유 request_id
	 * @param merchant 판매처 식별자
	 * @param searchQuery 검색어
	 * @param collectedAt 마지막 수집 완료 시각
	 */
	private void insertDefaultSearchContext(String requestId, String merchant, String searchQuery, Instant collectedAt) {
		jdbcTemplate.update("""
				INSERT INTO collection_search_contexts
				    (request_id, merchant, search_query, filters, collected_at, collector_version)
				VALUES (?, ?, ?, jsonb_build_object('inStockOnly', false), ?, ?)
				""", requestId, merchant, searchQuery, Timestamp.from(collectedAt), "test-v1");
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
	 * sort=oldest 파라미터가 마지막 수집이 가장 오래된 상품부터 반환하는지 검증한다.
	 * 관리자 화면이 재검증(재수집)이 시급한 상품을 고를 때 사용하는 정렬이다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void sortsProductsByOldestCollectedAtWhenRequested() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		CollectorResult secondProduct = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("backend-test-001", "backend-test-stale-002")
						.replace("1010110882", "stale-test-002"),
				CollectorResult.class);
		collectorResultStoreService.store(secondProduct);
		long freshId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "1010110882")
				.orElseThrow().getId();
		long staleId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "stale-test-002")
				.orElseThrow().getId();
		jdbcTemplate.update(
				"UPDATE merchant_products SET last_collected_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().minus(Duration.ofDays(10))), staleId);
		jdbcTemplate.update(
				"UPDATE merchant_products SET last_collected_at = ? WHERE id = ?",
				Timestamp.from(Instant.now()), freshId);

		mockMvc.perform(get("/internal/v1/products")
						.param("merchant", "abcmart")
						.param("limit", "10")
						.param("sort", "oldest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products[0].externalId").value("stale-test-002"))
				.andExpect(jsonPath("$.products[1].externalId").value("1010110882"));

		mockMvc.perform(get("/internal/v1/products")
						.param("merchant", "abcmart")
						.param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products[0].externalId").value("1010110882"))
				.andExpect(jsonPath("$.products[1].externalId").value("stale-test-002"));
	}

	/**
	 * staleHours 파라미터가 마지막 수집이 그 시간(시) 이전인 상품만 남기는지 검증한다.
	 * 관리자 화면의 "N시간 이상 지난 것만" 필터가 이 조건에 대응한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void filtersProductsByStaleHoursThreshold() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		CollectorResult secondProduct = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("backend-test-001", "backend-test-stale-003")
						.replace("1010110882", "stale-test-003"),
				CollectorResult.class);
		collectorResultStoreService.store(secondProduct);
		long freshId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "1010110882")
				.orElseThrow().getId();
		long staleId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "stale-test-003")
				.orElseThrow().getId();
		jdbcTemplate.update(
				"UPDATE merchant_products SET last_collected_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().minus(Duration.ofHours(10))), staleId);
		jdbcTemplate.update(
				"UPDATE merchant_products SET last_collected_at = ? WHERE id = ?",
				Timestamp.from(Instant.now()), freshId);

		mockMvc.perform(get("/internal/v1/products")
						.param("merchant", "abcmart")
						.param("limit", "10")
						.param("staleHours", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(1))
				.andExpect(jsonPath("$.products[0].externalId").value("stale-test-003"));

		mockMvc.perform(get("/internal/v1/products")
						.param("merchant", "abcmart")
						.param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(2));
	}

	/**
	 * 대량 재검증 요청이 배경 스레드에서 실제로 처리되어 COMPLETED로 끝나는지 검증한다.
	 * 존재하지 않는 상품 ID를 써서 개별 재검증 성공 여부가 아니라, 컨트롤러가 즉시
	 * 배치를 접수하고 {@code @Async} 실행기가 실제로 동작해 상태가 PROCESSING에서
	 * COMPLETED로 바뀌는 비동기 배관 자체를 검증한다.
	 *
	 * @throws Exception HTTP 요청 또는 진행 상태 대기에 실패한 경우
	 */
	@Test
	void completesBulkVerificationBatchAsynchronously() throws Exception {
		String response = mockMvc.perform(post("/internal/v1/offer-verifications/products/bulk")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productIds\":[999999999]}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.requestedCount").value(1))
				.andReturn().getResponse().getContentAsString();
		String batchId = objectMapper.readTree(response).get("batchId").asText();

		waitUntil(() -> {
			try {
				String status = mockMvc.perform(get("/internal/v1/offer-verifications/products/bulk/{batchId}", batchId))
						.andReturn().getResponse().getContentAsString();
				return objectMapper.readTree(status).get("status").asText().equals("COMPLETED");
			} catch (Exception exception) {
				return false;
			}
		}, Duration.ofSeconds(10));

		mockMvc.perform(get("/internal/v1/offer-verifications/products/bulk/{batchId}", batchId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.processedCount").value(1))
				.andExpect(jsonPath("$.queuedCount").value(0))
				.andExpect(jsonPath("$.results[0].success").value(false))
				.andExpect(jsonPath("$.results[0].error").value("상품을 찾을 수 없습니다."));
	}

	/** 조건이 참이 될 때까지 짧은 간격으로 재확인하고, 제한 시간을 넘기면 실패시킨다. */
	private void waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("배치 처리가 제한 시간 안에 끝나지 않았습니다.");
	}

	/**
	 * 사용자 질문 후보 API가 원본 질문을 보존하고 DB 상품의 가격, 재고와 출처를 반환하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void returnsProductCandidatesForUserQuestion() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());

		mockMvc.perform(post("/internal/v1/product-candidates/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "출근용 검정 구두를 찾아줘",
							  "query": "구두",
							  "limit": 5
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.question").value("출근용 검정 구두를 찾아줘"))
				.andExpect(jsonPath("$.query").value("구두"))
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.candidates[0].merchant").value("abcmart"))
				.andExpect(jsonPath("$.candidates[0].price.amount").value(69000))
				.andExpect(jsonPath("$.candidates[0].stockStatus").value("available"))
				.andExpect(jsonPath("$.candidates[0].source.sourceUrl").exists());
	}

	/**
	 * 상품 상세/근거와 두 후보 비교 API가 최신 snapshot provenance를 같은 상품 ID로 반환하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void returnsProductDetailEvidenceAndComparison() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		CollectorResult second = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("backend-test-001", "backend-test-detail-002")
						.replace("1010110882", "detail-test-002")
						.replace("\"name\": \"페니 로퍼\"", "\"name\": \"클래식 더비\""),
				CollectorResult.class);
		collectorResultStoreService.store(second);
		long firstId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "1010110882")
				.orElseThrow().getId();
		long secondId = merchantProductRepository.findByMerchantAndExternalId("abcmart", "detail-test-002")
				.orElseThrow().getId();

		mockMvc.perform(get("/internal/v1/products/{productId}", firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.product.externalId").value("1010110882"))
				.andExpect(jsonPath("$.freshness.status").value("STALE"))
				.andExpect(jsonPath("$.evidence[0].sourceUrl").exists())
				.andExpect(jsonPath("$.verifications[0].status").value("MATCHED"));

		mockMvc.perform(get("/internal/v1/products/{productId}/evidence", firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].collectorVersion").value("abcmart-search-v2"));

		mockMvc.perform(post("/internal/v1/product-comparisons")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productIds\":[%d,%d]}".formatted(firstId, secondId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(2))
				.andExpect(jsonPath("$.fields[?(@.key == 'price')]").exists())
				.andExpect(jsonPath("$.fields[?(@.key == 'freshness')]").exists());
	}

	/** 비교 API가 같은 상품 ID를 중복 비교하지 않도록 400으로 거절하는지 검증한다. */
	@Test
	void rejectsDuplicateProductComparisonIds() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		long productId = merchantProductRepository.findAll().getFirst().getId();

		mockMvc.perform(post("/internal/v1/product-comparisons")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productIds\":[%d,%d]}".formatted(productId, productId)))
				.andExpect(status().isBadRequest());
	}

	/**
	 * 후보 검색 결과가 없을 때 성공 응답과 빈 후보 목록을 반환하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void returnsEmptyCandidateListWhenDatabaseHasNoMatch() throws Exception {
		mockMvc.perform(post("/internal/v1/product-candidates/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "없는 상품을 찾아줘",
							  "query": "존재하지않는검색어"
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(0))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.candidates").isEmpty());
	}

	/**
	 * 검토된 Wiki의 운동화 하위 개념으로 러닝화를 확장해 원문 exact 검색이 놓친 265 후보를
	 * 반환하고 사용한 Wiki 점수와 관계 근거를 설명하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void expandsConfirmedProductTypeWithPublishedWikiClaim() throws Exception {
		collectorResultStoreService.store(loadRunningShoeCollectorResult());
		collectorResultStoreService.store(loadWalkingShoeCollectorResult());
		wikiConceptIndexService.indexReviewedPage(publishedRunningShoeWikiPage());
		String condition = purchaseCondition("[]")
				.replace("\"productType\": {\"value\": \"구두\"", "\"productType\": {\"value\": \"운동화\"")
				.replace("\"usage\": [{\"value\": \"출근\"", "\"usage\": [{\"value\": \"운동용\"")
				.replace("\"270\"", "\"265\"");
		String sessionId = createConfirmedResearchSession("15만 원 이하 운동화 265", condition);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(2))
				.andExpect(jsonPath("$.result.candidates[*].name")
						.value(org.hamcrest.Matchers.containsInAnyOrder("테스트 러닝 페이서", "테스트 워킹 밸런스")))
				.andExpect(jsonPath("$.result.assessments[*].sizeStatus")
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("MATCH"))))
				.andExpect(jsonPath("$.result.assessments[*].wikiConceptScore")
						.value(org.hamcrest.Matchers.containsInAnyOrder(0.9, 0.85)))
				.andExpect(jsonPath("$.result.assessments[*].matchReasons[*]")
						.value(org.hamcrest.Matchers.hasItems(
								"검토 Wiki: 운동화 → 러닝화 (narrower)",
								"검토 Wiki: 운동화 → 워킹화 (narrower)")));
	}

	/** PUBLISHED synonym이 사용자 상품 표현을 표준 상품 종류로 바꿔 검색에 사용하는지 검증한다. */
	@Test
	void resolvesProductTypeWithPublishedWikiSynonym() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		wikiConceptIndexService.indexReviewedPage(publishedDressShoeSynonymPage());
		String synonymCondition = purchaseCondition("[]").replace(
				"\"value\": \"구두\"",
				"\"value\": \"정장신발\"");
		String sessionId = createConfirmedResearchSession("정장신발을 찾아줘", synonymCondition);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conditions.productType.normalizedValue").value("구두"))
				.andExpect(jsonPath("$.conditions.productType.derivedBy").value("wiki"))
				.andExpect(jsonPath("$.result.totalCount").value(1))
				.andExpect(jsonPath("$.result.candidates[0].name").value("페니 로퍼"));
	}

	/**
	 * PUBLISHED Wiki가 없으면 의미를 추측하지 않고 기존 FTS/vector 검색으로 fallback해
	 * exact 관계가 없는 운동화 질문을 빈 결과로 유지하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void fallsBackToExistingRetrievalWithoutPublishedWiki() throws Exception {
		jdbcTemplate.update("DELETE FROM wiki_pages");
		collectorResultStoreService.store(loadRunningShoeCollectorResult());
		String condition = purchaseCondition("[]")
				.replace("\"productType\": {\"value\": \"구두\"", "\"productType\": {\"value\": \"운동화\"")
				.replace("\"270\"", "\"265\"");
		String sessionId = createConfirmedResearchSession("운동화 265", condition);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(0))
				.andExpect(jsonPath("$.result.candidates").isEmpty());
	}

	/**
	 * 구두 검색으로 수집됐더라도 실제 상품명과 카테고리가 워킹화면 필수 상품 종류 후보에서
	 * 제외해 수집 문맥이 상품 사실을 덮어쓰지 않는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void excludesUnrelatedCategoryCollectedUnderRequiredProductTypeQuery() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		collectorResultStoreService.store(loadWalkingShoeCollectedAsDressShoes());
		String sessionId = createConfirmedResearchSession("10만 원 이하 270 구두", purchaseCondition("[]"));

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(1))
				.andExpect(jsonPath("$.result.candidates[0].name").value("페니 로퍼"));
	}

	/** 사람 검토가 없는 DRAFT Wiki는 운영 index 적재를 거절하는지 검증한다. */
	@Test
	void rejectsDraftWikiPageFromRuntimeIndex() {
		WikiPageDocument draft = new WikiPageDocument(
				"running-shoes-taxonomy",
				1,
				"DRAFT",
				"운동화 분류",
				null,
				null,
				null,
				List.of(new WikiClaimDocument(
						"running-shoes",
						"운동화",
						"narrower",
						"러닝화",
						true,
						0.99,
						List.of("test-source"),
						List.of("test.category=러닝화"))));

		assertThatThrownBy(() -> wikiConceptIndexService.indexReviewedPage(draft))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("DRAFT Wiki page");
	}

	/**
	 * DB 검색어가 비어 있으면 전체 상품을 노출하지 않고 400으로 거절하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsCandidateRequestWithoutExplicitQuery() throws Exception {
		mockMvc.perform(post("/internal/v1/product-candidates/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "출근용 구두를 찾아줘",
							  "query": " "
							}
							"""))
				.andExpect(status().isBadRequest());
	}

	/**
	 * 상품 후보를 5개보다 많이 요청하면 화면 계약을 벗어나므로 400으로 거절하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsCandidateRequestOverFiveProducts() throws Exception {
		mockMvc.perform(post("/internal/v1/product-candidates/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "출근용 구두를 찾아줘",
							  "query": "구두",
							  "limit": 6
							}
							"""))
				.andExpect(status().isBadRequest());
	}

	/**
	 * AI 조건 구조화 결과를 저장하면 상품 검색 없이 DRAFT 조사 세션만 생성되는지 검증한다.
	 *
	 * @throws Exception HTTP 요청 또는 JSON 처리에 실패한 경우
	 */
	@Test
	void createsDraftResearchSessionWithoutSearchingProducts() throws Exception {
		mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[]")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.runtime").value("codex"))
				.andExpect(jsonPath("$.pluginId").value("purchase-research-agent"))
				.andExpect(jsonPath("$.conditions.productType.value").value("구두"))
				.andExpect(jsonPath("$.conditions.productType.priority").value("required"))
				.andExpect(jsonPath("$.result").doesNotExist());

		assertThat(researchSessionRepository.count()).isEqualTo(1);
	}

	/**
	 * 사용자가 조건을 확인하기 전에 검색을 요청하면 DB 상품 노출 없이 409로 차단하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청 또는 JSON 처리에 실패한 경우
	 */
	@Test
	void blocksProductSearchBeforeUserConfirmation() throws Exception {
		String response = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[]")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(response).get("sessionId").asText();

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESEARCH_SESSION_NOT_READY"));
	}

	/**
	 * AI가 추가 질문이 필요하다고 표시한 조건은 사용자가 확정할 수 없는지 검증한다.
	 *
	 * @throws Exception HTTP 요청 또는 JSON 처리에 실패한 경우
	 */
	@Test
	void rejectsConfirmationWhileRequiredConditionsAreMissing() throws Exception {
		String response = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[\"사이즈\"]")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(response).get("sessionId").asText();

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + purchaseCondition("[\"사이즈\"]") + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESEARCH_SESSION_NOT_READY"));
	}

	/**
	 * 사용자 확인 뒤 별도 검색 요청에서만 실제 PostgreSQL 후보 최대 3개를 반환하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void searchesCandidatesOnlyAfterResearchSessionConfirmation() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		String response = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[]")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(response).get("sessionId").asText();

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + purchaseCondition("[]") + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.result").doesNotExist());

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.result.candidates.length()").value(1))
				.andExpect(jsonPath("$.result.candidates[0].merchant").value("abcmart"))
				.andExpect(jsonPath("$.result.candidates[0].source.sourceUrl").exists())
				.andExpect(jsonPath("$.result.assessments[0].sizeStatus").value("MATCH"))
				.andExpect(jsonPath("$.result.assessments[0].colorStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.result.assessments[0].keywordScore").value(1.0))
				.andExpect(jsonPath("$.result.assessments[0].semanticScore").doesNotExist())
				.andExpect(jsonPath("$.result.assessments[0].wikiConceptScore").doesNotExist())
				.andExpect(jsonPath("$.result.assessments[0].evidenceCompletenessScore").value(1.0))
				.andExpect(jsonPath("$.result.assessments[0].matchReasons[0]").value("사이즈 270 재고 확인"));
	}

	/**
	 * 확정 세션의 Agent Run이 실제 PostgreSQL 후보를 READY로 반환하고 같은 세션 재요청에서
	 * 실행을 중복 생성하지 않는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void startsIdempotentReadyAgentRunThroughHttpEndpoint() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		String draft = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[]")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(draft).get("sessionId").asText();
		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + purchaseCondition("[]") + "}"))
				.andExpect(status().isOk());

		String first = mockMvc.perform(post("/internal/v1/agent-runs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"sessionId\":\"" + sessionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.research.result.candidates.length()").value(1))
				.andExpect(jsonPath("$.events.length()").value(3))
				.andExpect(jsonPath("$.nextAction").value("SELECT_AND_VERIFY"))
				.andReturn().getResponse().getContentAsString();
		String runId = objectMapper.readTree(first).get("runId").asText();

		mockMvc.perform(post("/internal/v1/agent-runs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"sessionId\":\"" + sessionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runId").value(runId));
		assertThat(agentRunRepository.count()).isEqualTo(1);
	}

	/**
	 * 실제 DB 후보가 없는 확정 세션은 RabbitMQ 수집 job과 Agent Run 연결을 저장하고
	 * COLLECTING을 반환하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청, Queue 발행 또는 JSON 처리에 실패한 경우
	 */
	@Test
	void startsCollectingAgentRunForMissingDatabaseCandidates() throws Exception {
		String productType = "격리미존재상품";
		String draft = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content(researchSessionRequest("[]").replace("구두", productType)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(draft).get("sessionId").asText();
		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + purchaseCondition("[]").replace("구두", productType) + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/v1/agent-runs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"sessionId\":\"" + sessionId + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("COLLECTING"))
				.andExpect(jsonPath("$.collectionJobs.length()").value(1))
				.andExpect(jsonPath("$.collectionJobs[0].dataStatus").value("MISSING"))
				.andExpect(jsonPath("$.collectionJobs[0].status").value("QUEUED"))
				.andExpect(jsonPath("$.nextAction").value("POLL_ADVANCE"));
	}

	/**
	 * 사용자 확인 최대 가격보다 비싼 최신 상품은 조사 세션 후보에서 제외되는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void appliesConfirmedPriceConditionToResearchCandidates() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		String condition = purchaseCondition("[]").replace("\"max\": 100000", "\"max\": 60000");
		String response = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "6만 원 이하 구두",
							  "runtime": "codex",
							  "pluginId": "purchase-research-agent",
							  "conditions": %s
							}
							""".formatted(condition)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(response).get("sessionId").asText();

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + condition + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(0))
				.andExpect(jsonPath("$.result.candidates").isEmpty());
	}

	/**
	 * 같은 판매처의 브랜드/상품명/카테고리가 같은 색상별 판매 행을 카드 하나로 묶고
	 * 원본 판매처 상품과 선택 속성은 모두 보존하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void groupsSameProductFamilyWithoutDiscardingMerchantListings() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		CollectorResult blackVariant = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("backend-test-001", "backend-test-variant-002")
						.replace("1010110882", "1010110883")
						.replace("\"color\": null", "\"color\": \"BLACK\""),
				CollectorResult.class);
		collectorResultStoreService.store(blackVariant);
		CollectorResult brownWithoutRequestedSize = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("backend-test-001", "backend-test-variant-003")
						.replace("1010110882", "1010110884")
						.replace("\"270\"", "\"260\"")
						.replace("\"color\": null", "\"color\": \"BROWN\""),
				CollectorResult.class);
		collectorResultStoreService.store(brownWithoutRequestedSize);
		String sessionId = createConfirmedResearchSession("270 검정 구두", purchaseCondition("[]"));

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(2))
				.andExpect(jsonPath("$.result.candidates.length()").value(1))
				.andExpect(jsonPath("$.result.groups.length()").value(1))
				.andExpect(jsonPath("$.result.groups[0].groupingBasis").value("DERIVED"))
				.andExpect(jsonPath("$.result.groups[0].listings.length()").value(3))
				.andExpect(jsonPath("$.result.groups[0].listings[*].product.externalId")
						.value(org.hamcrest.Matchers.containsInAnyOrder("1010110882", "1010110883", "1010110884")))
				.andExpect(jsonPath("$.result.groups[0].listings[?(@.product.externalId == '1010110883')].attributes.color[0]")
						.value("BLACK"))
				.andExpect(jsonPath("$.result.groups[0].listings[?(@.product.externalId == '1010110884')].attributes.color[0]")
						.value("BROWN"))
				.andExpect(jsonPath("$.result.groups[0].listings[?(@.product.externalId == '1010110884')].assessment.sizeStatus")
						.value("MISMATCH"));
	}

	/**
	 * 판매처가 색상 값을 제공하지 않은 상품은 선호 색상 때문에 제거하지 않지만 같은 색상을
	 * 필수로 확인하면 후보에서 제외하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void appliesRequiredAndPreferredColorStrengthToResearchCandidates() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		String preferredColor = purchaseCondition("[]").replace(
				"\"colors\": []",
				"\"colors\": [{\"value\": \"갈색\", \"priority\": \"preferred\"}]");
		String preferredSessionId = createConfirmedResearchSession("갈색이면 좋은 구두", preferredColor);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", preferredSessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(1))
				.andExpect(jsonPath("$.result.assessments[0].colorStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.result.assessments[0].relaxedConditions")
						.value(org.hamcrest.Matchers.hasItem("색상 갈색 판매처 정보 없음")));

		String requiredColor = preferredColor.replace("\"priority\": \"preferred\"", "\"priority\": \"required\"");
		String requiredSessionId = createConfirmedResearchSession("갈색이어야 하는 구두", requiredColor);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", requiredSessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(0));
	}

	/**
	 * 선호 가격 상한은 후보를 제거하지 않고 같은 상한을 필수로 확인한 경우에만 제외하는지
	 * 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void appliesRequiredAndPreferredPriceStrengthToResearchCandidates() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());
		String preferredPrice = purchaseCondition("[]")
				.replace("\"max\": 100000", "\"max\": 60000")
				.replace("\"currency\": \"KRW\", \"priority\": \"required\"",
						"\"currency\": \"KRW\", \"priority\": \"preferred\"");
		String preferredSessionId = createConfirmedResearchSession("6만 원대면 좋은 구두", preferredPrice);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", preferredSessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(1));

		String requiredPrice = preferredPrice.replace(
				"\"currency\": \"KRW\", \"priority\": \"preferred\"",
				"\"currency\": \"KRW\", \"priority\": \"required\"");
		String requiredSessionId = createConfirmedResearchSession("6만 원 이하여야 하는 구두", requiredPrice);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", requiredSessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(0));
	}

	/**
	 * 현재 SQL AND baseline이 상품명에 없는 수집 검색어와 정확한 옵션 조건을 사용해 후보를
	 * 찾는지 검증한다.
	 *
	 * @throws Exception fixture 저장에 실패한 경우
	 */
	@Test
	void measuresExactMatchForSqlAndCandidateBaseline() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());

		var result = merchantProductRepository.searchCandidates(
				"abcmart",
				"구두",
				null,
				100000L,
				"KRW",
				",270,",
				null,
				false,
				null,
				null,
				null,
				PageRequest.of(0, 20));

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent())
				.singleElement()
				.satisfies(product -> assertThat(product.getExternalId()).isEqualTo("1010110882"));
	}

	/**
	 * 현재 SQL AND baseline이 사람 판정상 구두 후보인 페니 로퍼를 상품명과 수집 검색어의
	 * 문자열 불일치 때문에 찾지 못하는 false zero를 측정한다.
	 *
	 * @throws Exception fixture 저장에 실패한 경우
	 */
	@Test
	void measuresSemanticFalseZeroForSqlAndCandidateBaseline() throws Exception {
		CollectorResult semanticCandidate = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("\"query\": \"구두\"", "\"query\": \"로퍼\""),
				CollectorResult.class);
		collectorResultStoreService.store(semanticCandidate);

		var result = merchantProductRepository.searchCandidates(
				"abcmart",
				"구두",
				null,
				100000L,
				"KRW",
				",270,",
				null,
				false,
				null,
				null,
				null,
				PageRequest.of(0, 20));

		assertThat(result.getTotalElements()).isZero();
	}

	/**
	 * SQL 정확 일치 baseline이 놓치는 한국어 검색어 오타를 pg_trgm 후보 검색이 수집 검색
	 * 문맥에서 복구하는지 검증한다.
	 *
	 * @throws Exception fixture 저장 또는 HTTP 요청에 실패한 경우
	 */
	@Test
	void recoversKoreanQueryTypoWithPostgresTrigramSearch() throws Exception {
		collectorResultStoreService.store(loadAbcmartCollectorResult());

		var baseline = merchantProductRepository.searchCandidates(
				"abcmart",
				"구두우",
				null,
				100000L,
				"KRW",
				",270,",
				null,
				false,
				null,
				null,
				null,
				PageRequest.of(0, 20));
		assertThat(baseline.getTotalElements()).isZero();

		String typoCondition = purchaseCondition("[]").replace(
				"\"value\": \"구두\"",
				"\"value\": \"구두우\"");
		String sessionId = createConfirmedResearchSession("구두우를 찾아줘", typoCondition);

		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/search", sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCount").value(1))
				.andExpect(jsonPath("$.result.candidates[0].externalId").value("1010110882"));
	}

	/**
	 * 전문 검색으로 찾지 못하는 상품을 같은 1024차원 fixture embedding의 cosine 유사도로
	 * 회수하면서 가격/재고/사이즈 필터는 그대로 적용하는지 검증한다.
	 *
	 * @throws Exception fixture 저장에 실패한 경우
	 */
	@Test
	void retrievesStructurallyEligibleCandidateWithPgvector() throws Exception {
		CollectorResult semanticCandidate = objectMapper.readValue(
				Files.readString(abcmartFixturePath())
						.replace("\"query\": \"구두\"", "\"query\": \"로퍼\""),
				CollectorResult.class);
		collectorResultStoreService.store(semanticCandidate);
		long merchantProductId = merchantProductRepository.findAll().getFirst().getId();
		String vector = unitVectorLiteral(0);
		jdbcTemplate.update("""
				INSERT INTO product_embeddings
				    (merchant_product_id, provider, model, model_version, content_hash, embedding)
				VALUES (?, 'fixture', 'semantic', 'test', ?, CAST(? AS vector))
				""", merchantProductId, "0".repeat(64), vector);

		var result = merchantProductRepository.searchCandidates(
				"abcmart",
				"정장 구두",
				null,
				100000L,
				"KRW",
				",270,",
				null,
				true,
				"fixture",
				"semantic@test",
				vector,
				PageRequest.of(0, 20));

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().getFirst().getExternalId()).isEqualTo("1010110882");
		var signals = merchantProductRepository.findCandidateRetrievalSignals(
				List.of(merchantProductId),
				"정장 구두",
				"fixture",
				"semantic@test",
				vector);
		assertThat(signals).singleElement()
				.satisfies(signal -> {
					assertThat(signal.getCandidateId()).isEqualTo(merchantProductId);
					assertThat(signal.getKeywordScore()).isBetween(0.0, 1.0);
					assertThat(signal.getSemanticScore()).isEqualTo(1.0);
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
	 * OpenAPI JSON에 수동 적재, 상품 조회, 작업 상태와 짧은 페이지 수집 예시가 포함되고
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
				.andExpect(jsonPath("$.paths['/internal/v1/product-candidates/search'].post")
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
	 * 원문 운동화에는 exact 일치하지 않지만 Wiki 확장어 러닝화와 265 옵션에는 일치하는 fixture를 만든다.
	 *
	 * @return 러닝화 검색 회귀 테스트용 CollectorResult
	 * @throws Exception fixture 파일을 읽거나 JSON을 변환할 수 없는 경우
	 */
	private CollectorResult loadRunningShoeCollectorResult() throws Exception {
		String fixture = Files.readString(abcmartFixturePath())
				.replace("backend-test-001", "backend-test-running-001")
				.replace("1010110882", "running-test-001")
				.replace("\"query\": \"구두\"", "\"query\": \"러닝화\"")
				.replace("\"name\": \"페니 로퍼\"", "\"name\": \"테스트 러닝 페이서\"")
				.replace("[\"신발\", \"구두\", \"로퍼\"]", "[\"신발\", \"스포츠\", \"러닝화\"]")
				.replace("\"270\"", "\"265\"")
				.replace("\"color\": null", "\"color\": \"BLACK\"");
		return objectMapper.readValue(fixture, CollectorResult.class);
	}

	/**
	 * 원문 운동화에는 exact 일치하지 않지만 Wiki 확장어 워킹화와 265 옵션에는 일치하는 fixture를 만든다.
	 *
	 * @return 워킹화 검색 회귀 테스트용 CollectorResult
	 * @throws Exception fixture 파일을 읽거나 JSON을 변환할 수 없는 경우
	 */
	private CollectorResult loadWalkingShoeCollectorResult() throws Exception {
		String fixture = Files.readString(abcmartFixturePath())
				.replace("backend-test-001", "backend-test-walking-001")
				.replace("1010110882", "walking-test-001")
				.replace("\"query\": \"구두\"", "\"query\": \"워킹화\"")
				.replace("\"name\": \"페니 로퍼\"", "\"name\": \"테스트 워킹 밸런스\"")
				.replace("[\"신발\", \"구두\", \"로퍼\"]", "[\"신발\", \"스포츠\", \"워킹화\"]")
				.replace("\"270\"", "\"265\"")
				.replace("\"color\": null", "\"color\": \"BLACK\"");
		return objectMapper.readValue(fixture, CollectorResult.class);
	}

	/**
	 * 검색어는 구두지만 실제 상품 분류는 워킹화인 false-positive fixture를 만든다.
	 *
	 * @return 필수 상품 종류 fail-closed 회귀 테스트용 결과
	 * @throws Exception fixture JSON 변환에 실패한 경우
	 */
	private CollectorResult loadWalkingShoeCollectedAsDressShoes() throws Exception {
		String fixture = Files.readString(abcmartFixturePath())
				.replace("backend-test-001", "backend-test-unrelated-001")
				.replace("1010110882", "unrelated-test-001")
				.replace("\"name\": \"페니 로퍼\"", "\"name\": \"테스트 워킹 밸런스\"")
				.replace("[\"신발\", \"구두\", \"로퍼\"]", "[\"신발\", \"스포츠\", \"워킹화\"]")
				.replace("\"color\": null", "\"color\": \"BLACK\"");
		return objectMapper.readValue(fixture, CollectorResult.class);
	}

	/** 검토자와 출처가 연결된 테스트용 PUBLISHED 운동화 분류 page를 만든다. */
	private WikiPageDocument publishedRunningShoeWikiPage() {
		return new WikiPageDocument(
				"running-shoes-taxonomy",
				1,
				"PUBLISHED",
				"운동화 분류",
				"integration-test-reviewer",
				OffsetDateTime.parse("2026-08-08T18:00:00+09:00"),
				null,
				List.of(
						new WikiClaimDocument(
								"running-shoes",
								"운동화",
								"narrower",
								"러닝화",
								true,
								0.9,
								List.of("test-source"),
								List.of("test.category=러닝화")),
						new WikiClaimDocument(
								"walking-shoes",
								"운동화",
								"narrower",
								"워킹화",
								true,
								0.85,
								List.of("test-source"),
								List.of("test.category=워킹화"))));
	}

	/** 테스트에서만 사용하는 사람 검토 완료 구두 synonym page를 만든다. */
	private WikiPageDocument publishedDressShoeSynonymPage() {
		return new WikiPageDocument(
				"dress-shoes-synonym",
				1,
				"PUBLISHED",
				"구두 동의어",
				"integration-test-reviewer",
				OffsetDateTime.parse("2026-08-10T23:00:00+09:00"),
				null,
				List.of(new WikiClaimDocument(
						"dress-shoes-formal",
						"구두",
						"synonym",
						"정장신발",
						true,
						0.95,
						List.of("test-source"),
						List.of("test.category=구두"))));
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

	/**
	 * 조사 세션 생성 API에 사용할 Codex 실행 결과 JSON을 만든다.
	 *
	 * @param missingConditions 추가 확인이 필요한 조건 JSON 배열
	 * @return 조사 세션 생성 요청 JSON
	 */
	private String researchSessionRequest(String missingConditions) {
		return """
				{
				  "question": "출근용 검정 구두를 찾아줘",
				  "runtime": "codex",
				  "pluginId": "purchase-research-agent",
				  "conditions": %s
				}
				""".formatted(purchaseCondition(missingConditions));
	}

	/**
	 * 테스트용 구매 조건을 DRAFT 조사 세션으로 저장하고 사용자 확인 상태로 전환한다.
	 *
	 * @param question 테스트 사용자 질문
	 * @param condition PurchaseCondition JSON
	 * @return 확인된 조사 세션 식별자
	 * @throws Exception HTTP 요청 또는 JSON 처리에 실패한 경우
	 */
	private String createConfirmedResearchSession(String question, String condition) throws Exception {
		String response = mockMvc.perform(post("/internal/v1/research-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "%s",
							  "runtime": "codex",
							  "pluginId": "purchase-research-agent",
							  "conditions": %s
							}
							""".formatted(question, condition)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(response).get("sessionId").asText();
		mockMvc.perform(post("/internal/v1/research-sessions/{sessionId}/confirm", sessionId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"conditions\":" + condition + "}"))
				.andExpect(status().isOk());
		return sessionId;
	}

	/**
	 * 지정 위치만 1이고 나머지는 0인 1024차원 pgvector fixture를 생성한다.
	 *
	 * @param unitIndex 값 1을 둘 차원 위치
	 * @return pgvector 대괄호 literal
	 */
	private String unitVectorLiteral(int unitIndex) {
		String[] values = new String[1024];
		java.util.Arrays.fill(values, "0");
		values[unitIndex] = "1";
		return "[" + String.join(",", values) + "]";
	}

	/**
	 * 테스트에서 사용자 확인 전후에 사용할 구매 조건 JSON을 만든다.
	 *
	 * @param missingConditions 추가 확인이 필요한 조건 JSON 배열
	 * @return PurchaseCondition JSON
	 */
	private String purchaseCondition(String missingConditions) {
		return """
				{
				  "productType": {"value": "구두", "priority": "required"},
				  "usage": [{"value": "출근", "priority": "preferred"}],
				  "price": {"min": null, "max": 100000, "currency": "KRW", "priority": "required"},
				  "colors": [],
				  "sizes": [{"value": "270", "priority": "required"}],
				  "requirements": [{"value": "편안함", "priority": "preferred"}],
				  "merchant": "abcmart",
				  "missingConditions": %s,
				  "assumptions": [],
				  "confidence": 0.92,
				  "requiresConfirmation": true
				}
				""".formatted(missingConditions);
	}
}
