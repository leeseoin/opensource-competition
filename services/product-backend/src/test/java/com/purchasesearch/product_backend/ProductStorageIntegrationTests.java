package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
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
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService.StoreReport;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
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
	private MockMvc mockMvc;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
							  "limit": 3
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
	 * 상품 후보를 3개보다 많이 요청하면 화면 계약을 벗어나므로 400으로 거절하는지 검증한다.
	 *
	 * @throws Exception HTTP 요청에 실패한 경우
	 */
	@Test
	void rejectsCandidateRequestOverThreeProducts() throws Exception {
		mockMvc.perform(post("/internal/v1/product-candidates/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "question": "출근용 구두를 찾아줘",
							  "query": "구두",
							  "limit": 4
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
