package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;

/** RetrievalEvaluationIntegrationTests는 고정 snapshot에서 후보 검색 단계별 품질을 비교한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RetrievalEvaluationIntegrationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MerchantProductRepository merchantProductRepository;

	/**
	 * 60개 DRAFT 질문으로 SQL baseline, FTS/trigram과 DRAFT Wiki 확장의 Recall@20,
	 * nDCG@3, false zero 및 정답 없음 정확도를 같은 PostgreSQL에서 측정한다.
	 *
	 * @throws Exception 평가 data 또는 snapshot을 읽거나 저장할 수 없는 경우
	 */
	@Test
	void comparesSqlBaselineAndFullTextRetrievalOnDraftDataset() throws Exception {
		EvaluationDataset dataset = objectMapper.readValue(
				Files.readString(repositoryPath("knowledge", "eval", "retrieval-v1.json")),
				EvaluationDataset.class);
		List<SnapshotProduct> products = objectMapper.readValue(
				Files.readString(repositoryPath(
						"contracts", "collector", "unified", "examples",
						"unified_구두_top20_20260803_002024.json")),
				objectMapper.getTypeFactory().constructCollectionType(List.class, SnapshotProduct.class));
		WikiPage wikiPage = objectMapper.readValue(
				Files.readString(repositoryPath("knowledge", "wiki", "shoes-taxonomy-v1.json")),
				WikiPage.class);
		seedSnapshot(products);

		EvaluationMetrics baseline = evaluate(dataset.cases(), false, null);
		EvaluationMetrics fullText = evaluate(dataset.cases(), true, null);
		EvaluationMetrics draftWiki = evaluate(dataset.cases(), true, wikiPage);

		System.out.printf(Locale.ROOT,
				"retrieval-eval SQL recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f%n",
				baseline.recallAt20(), baseline.ndcgAt3(), baseline.falseZeroRate(), baseline.noResultAccuracy());
		System.out.printf(Locale.ROOT,
				"retrieval-eval FTS recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f%n",
				fullText.recallAt20(), fullText.ndcgAt3(), fullText.falseZeroRate(), fullText.noResultAccuracy());
		System.out.printf(Locale.ROOT,
				"retrieval-eval DRAFT-WIKI recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f%n",
				draftWiki.recallAt20(), draftWiki.ndcgAt3(), draftWiki.falseZeroRate(),
				draftWiki.noResultAccuracy());

		assertThat(dataset.cases()).hasSize(60);
		assertThat(wikiPage.status()).isEqualTo("DRAFT");
		assertThat(fullText.recallAt20()).isGreaterThanOrEqualTo(baseline.recallAt20());
		assertThat(fullText.falseZeroRate()).isLessThanOrEqualTo(baseline.falseZeroRate());
	}

	/** 평가 질문을 repository 후보 검색으로 실행하고 macro 품질 지표를 계산한다. */
	private EvaluationMetrics evaluate(List<EvaluationCase> cases, boolean enableFullText, WikiPage wikiPage) {
		double recall = 0;
		double ndcg = 0;
		int relevantQueries = 0;
		int falseZeros = 0;
		int noResultQueries = 0;
		int correctNoResults = 0;

		for (EvaluationCase evaluationCase : cases) {
			Set<String> retrieved = search(evaluationCase, enableFullText, wikiPage);
			if (evaluationCase.expectNoResults()) {
				noResultQueries++;
				if (retrieved.isEmpty()) {
					correctNoResults++;
				}
				continue;
			}
			Map<String, Integer> relevance = new HashMap<>();
			for (Judgment judgment : evaluationCase.judgments()) {
				if (judgment.relevance() > 0) {
					relevance.put(key(judgment.merchant(), judgment.externalId()), judgment.relevance());
				}
			}
			if (relevance.isEmpty()) {
				continue;
			}
			relevantQueries++;
			long hits = retrieved.stream().filter(relevance::containsKey).count();
			recall += (double) hits / relevance.size();
			if (hits == 0) {
				falseZeros++;
			}
			ndcg += ndcgAt3(new ArrayList<>(retrieved), relevance);
		}

		return new EvaluationMetrics(
				recall / relevantQueries,
				ndcg / relevantQueries,
				(double) falseZeros / relevantQueries,
				(double) correctNoResults / noResultQueries);
	}

	/** 평가 조건과 선택적인 DRAFT Wiki 확장을 repository parameter로 변환해 후보 key를 반환한다. */
	private Set<String> search(EvaluationCase evaluationCase, boolean enableFullText, WikiPage wikiPage) {
		String query = firstValue(evaluationCase, "productType");
		if (query == null) {
			query = firstValue(evaluationCase, "brand");
		}
		if (query == null) {
			query = evaluationCase.question();
		}
		String merchant = firstRequiredValue(evaluationCase, "merchant");
		Long maxPrice = parseLong(firstRequiredValue(evaluationCase, "priceMax"));
		String sizes = toCsv(values(evaluationCase.required(), "size"));
		String colors = toCsv(values(evaluationCase.required(), "color"));

		Map<String, Double> reciprocalRankScores = new LinkedHashMap<>();
		for (String expandedQuery : expandQueries(query, wikiPage)) {
			List<String> ranked = merchantProductRepository.searchCandidates(
					merchant,
					expandedQuery,
					null,
					maxPrice,
					maxPrice == null ? null : "KRW",
					sizes,
					colors,
					enableFullText,
					null,
					null,
					null,
					PageRequest.of(0, 20)).getContent().stream()
					.map(product -> key(product.getMerchant(), product.getExternalId()))
					.toList();
			for (int rank = 0; rank < ranked.size(); rank++) {
				reciprocalRankScores.merge(ranked.get(rank), 1.0 / (60 + rank + 1), Double::sum);
			}
		}
		return reciprocalRankScores.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(20)
				.map(Map.Entry::getKey)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

	/** DRAFT Wiki의 직접 narrower/synonym 관계만 검색어 후보로 확장한다. */
	private List<String> expandQueries(String query, WikiPage wikiPage) {
		LinkedHashSet<String> queries = new LinkedHashSet<>();
		queries.add(query);
		if (wikiPage == null) {
			return List.copyOf(queries);
		}
		for (WikiClaim claim : wikiPage.claims()) {
			if (claim.subject().equalsIgnoreCase(query)) {
				queries.add(claim.object());
			}
			if (claim.relation().equals("synonym") && claim.object().equalsIgnoreCase(query)) {
				queries.add(claim.subject());
			}
		}
		return List.copyOf(queries);
	}

	/** 검색 순서 상위 3개와 relevance 판정으로 정규화 누적 이득을 계산한다. */
	private double ndcgAt3(List<String> retrieved, Map<String, Integer> relevance) {
		double dcg = 0;
		for (int index = 0; index < Math.min(3, retrieved.size()); index++) {
			int grade = relevance.getOrDefault(retrieved.get(index), 0);
			dcg += (Math.pow(2, grade) - 1) / (Math.log(index + 2) / Math.log(2));
		}
		List<Integer> ideal = relevance.values().stream().sorted(java.util.Comparator.reverseOrder()).toList();
		double idcg = 0;
		for (int index = 0; index < Math.min(3, ideal.size()); index++) {
			idcg += (Math.pow(2, ideal.get(index)) - 1) / (Math.log(index + 2) / Math.log(2));
		}
		return idcg == 0 ? 0 : dcg / idcg;
	}

	/** 통합 상품 snapshot 20개를 운영 schema의 최소 검색 사실로 적재한다. */
	private void seedSnapshot(List<SnapshotProduct> products) throws Exception {
		int sequence = 0;
		for (SnapshotProduct product : products) {
			long productId = jdbcTemplate.queryForObject("""
					INSERT INTO products (name, brand, category_path, image_urls)
					VALUES (?, ?, CAST(? AS jsonb), '[]'::jsonb)
					RETURNING id
					""", Long.class,
					product.title(), product.brand(),
					objectMapper.writeValueAsString(List.of(product.categoryPath().split(" > "))));
			long merchantProductId = jdbcTemplate.queryForObject("""
					INSERT INTO merchant_products
					    (product_id, merchant, external_id, product_url, last_collected_at)
					VALUES (?, ?, ?, ?, '2026-08-03T00:20:24+09:00')
					RETURNING id
					""", Long.class, productId, product.site(), product.sourceProductId(), product.link());
			String requestId = "retrieval-eval-" + (++sequence);
			jdbcTemplate.update("""
					INSERT INTO collection_search_contexts
					    (request_id, merchant, search_query, filters, collected_at, collector_version)
					VALUES (?, ?, '구두', '{}'::jsonb, '2026-08-03T00:20:24+09:00', 'eval-v1')
					""", requestId, product.site());
			long snapshotId = jdbcTemplate.queryForObject("""
					INSERT INTO offer_snapshots
					    (merchant_product_id, request_id, price_amount, currency, stock_status,
					     rating, review_count, source_url, collected_at, collector_version)
					VALUES (?, ?, ?, 'KRW', ?, ?, ?, ?, '2026-08-03T00:20:24+09:00', 'eval-v1')
					RETURNING id
					""", Long.class,
					merchantProductId, requestId, parsePrice(product.price()),
					Boolean.TRUE.equals(product.inStock()) ? "available" : "unavailable",
					product.rating(), product.reviewCount(), product.link());
			seedOptions(snapshotId, product);
		}
	}

	/** 평가 snapshot의 색상/사이즈 조합을 최신 재고 option 행으로 적재한다. */
	private void seedOptions(long snapshotId, SnapshotProduct product) {
		List<String> colors = product.options().colors().isEmpty()
				? java.util.Collections.singletonList(null)
				: product.options().colors();
		List<String> sizes = product.options().sizes().isEmpty()
				? java.util.Collections.singletonList(null)
				: product.options().sizes();
		for (String color : colors) {
			for (String size : sizes) {
				jdbcTemplate.update("""
						INSERT INTO product_options
						    (offer_snapshot_id, label, size, color, stock_status, source_url,
						     collected_at, collector_version)
						VALUES (?, ?, ?, ?, 'available', ?, '2026-08-03T00:20:24+09:00', 'eval-v1')
						""", snapshotId, String.valueOf(color) + "/" + String.valueOf(size),
						size, color, product.link());
			}
		}
	}

	/** 조건 목록에서 필수/선호를 가리지 않고 첫 값을 찾는다. */
	private String firstValue(EvaluationCase evaluationCase, String field) {
		String required = first(evaluationCase.required(), field);
		return required == null ? first(evaluationCase.preferred(), field) : required;
	}

	/** 필수 조건 목록에서 첫 값을 찾는다. */
	private String firstRequiredValue(EvaluationCase evaluationCase, String field) {
		return first(evaluationCase.required(), field);
	}

	/** 조건 목록에서 field와 일치하는 첫 값을 찾는다. */
	private String first(List<EvaluationCondition> conditions, String field) {
		return conditions.stream().filter(condition -> condition.field().equals(field))
				.map(EvaluationCondition::value).findFirst().orElse(null);
	}

	/** 조건 목록에서 field와 일치하는 모든 값을 소문자로 정규화한다. */
	private List<String> values(List<EvaluationCondition> conditions, String field) {
		return conditions.stream().filter(condition -> condition.field().equals(field))
				.map(EvaluationCondition::value).map(value -> value.toLowerCase(Locale.ROOT)).toList();
	}

	/** SQL option 목록 비교용 쉼표 경계 문자열을 만든다. */
	private String toCsv(List<String> values) {
		return values.isEmpty() ? null : "," + String.join(",", values) + ",";
	}

	/** nullable 숫자 문자열을 Long으로 변환한다. */
	private Long parseLong(String value) {
		return value == null ? null : Long.valueOf(value);
	}

	/** 원화 표시 문자열을 정수 금액으로 변환한다. */
	private long parsePrice(String value) {
		return Long.parseLong(value.replace(",", "").replace("원", ""));
	}

	/** 판매처와 외부 상품번호를 relevance 비교 key로 결합한다. */
	private String key(String merchant, String externalId) {
		return merchant + ":" + externalId;
	}

	/** repository 상대 경로를 현재 service 실행 위치에 맞게 계산한다. */
	private Path repositoryPath(String... parts) {
		Path root = Path.of(System.getProperty("user.dir"), "..", "..").normalize();
		for (String part : parts) {
			root = root.resolve(part);
		}
		return root;
	}

	/** EvaluationDataset은 60개 검색 품질 평가 질문을 감싼다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvaluationDataset(List<EvaluationCase> cases) {
	}

	/** EvaluationCase는 한 질문의 조건, relevance와 정답 없음 판정을 표현한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvaluationCase(
			String question,
			List<EvaluationCondition> required,
			List<EvaluationCondition> preferred,
			List<Judgment> judgments,
			boolean expectNoResults) {
	}

	/** EvaluationCondition은 평가용 field/value 조건이다. */
	private record EvaluationCondition(String field, String value) {
	}

	/** Judgment는 판매처 상품의 사람 판정 relevance를 표현한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Judgment(String merchant, String externalId, int relevance) {
	}

	/** WikiPage는 운영 미연결 DRAFT 확장 관계와 상태만 읽는다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WikiPage(String status, List<WikiClaim> claims) {
	}

	/** WikiClaim은 평가 중 직접 확장할 subject/relation/object를 표현한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WikiClaim(String subject, String relation, String object) {
	}

	/** SnapshotProduct는 통합 크롤러 snapshot의 검색 평가 필드만 읽는다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SnapshotProduct(
			@JsonProperty("source_product_id") String sourceProductId,
			String title,
			String brand,
			String price,
			String link,
			String site,
			@JsonProperty("category_path") String categoryPath,
			@JsonProperty("in_stock") Boolean inStock,
			java.math.BigDecimal rating,
			@JsonProperty("review_count") Integer reviewCount,
			SnapshotOptions options) {
	}

	/** SnapshotOptions는 평가에 사용할 공개 색상과 사이즈 목록이다. */
	private record SnapshotOptions(List<String> colors, List<String> sizes) {
	}

	/** EvaluationMetrics는 한 검색 단계의 macro 품질 지표다. */
	private record EvaluationMetrics(
			double recallAt20,
			double ndcgAt3,
			double falseZeroRate,
			double noResultAccuracy) {
	}
}
