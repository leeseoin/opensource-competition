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
	private static final List<String> INITIAL_HUMAN_REVIEW_IDS = List.of(
			"exact-001",
			"exact-007",
			"exact-012",
			"exact-013",
			"reverification-002",
			"semantic-001",
			"semantic-007",
			"relaxation-002",
			"relaxation-008",
			"noresult-001");

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MerchantProductRepository merchantProductRepository;

	/**
	 * 60개 DRAFT 질문으로 도입 전 strict AND, 현재 FTS/trigram과 DRAFT Wiki 확장의
	 * Recall@20, nDCG@3, false zero 및 정답 없음 정확도를 같은 PostgreSQL에서 측정한다.
	 *
	 * @throws Exception 평가 data 또는 snapshot을 읽거나 저장할 수 없는 경우
	 */
	@Test
	void comparesLegacyStrictAndCurrentFullTextRetrievalOnDraftDataset() throws Exception {
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

		Map<String, SnapshotProduct> productsByKey = products.stream()
				.collect(java.util.stream.Collectors.toMap(
						product -> key(product.site(), product.sourceProductId()),
						product -> product));
		EvaluationRun baseline = evaluate(dataset.cases(), RetrievalVariant.LEGACY_STRICT, null, productsByKey);
		EvaluationRun fullText = evaluate(dataset.cases(), RetrievalVariant.CURRENT_FTS, null, productsByKey);
		EvaluationRun draftWiki = evaluate(dataset.cases(), RetrievalVariant.DRAFT_WIKI, wikiPage, productsByKey);

		System.out.printf(Locale.ROOT,
				"retrieval-eval LEGACY-STRICT recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f useful@3=%.4f violation@3=%.4f%n",
				baseline.metrics().recallAt20(), baseline.metrics().ndcgAt3(),
				baseline.metrics().falseZeroRate(), baseline.metrics().noResultAccuracy(),
				baseline.metrics().usefulCandidateRateAt3(), baseline.metrics().hardConstraintViolationRateAt3());
		System.out.printf(Locale.ROOT,
				"retrieval-eval FTS recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f useful@3=%.4f violation@3=%.4f%n",
				fullText.metrics().recallAt20(), fullText.metrics().ndcgAt3(),
				fullText.metrics().falseZeroRate(), fullText.metrics().noResultAccuracy(),
				fullText.metrics().usefulCandidateRateAt3(), fullText.metrics().hardConstraintViolationRateAt3());
		System.out.printf(Locale.ROOT,
				"retrieval-eval DRAFT-WIKI recall@20=%.4f ndcg@3=%.4f falseZero=%.4f noResult=%.4f%n",
				draftWiki.metrics().recallAt20(), draftWiki.metrics().ndcgAt3(),
				draftWiki.metrics().falseZeroRate(), draftWiki.metrics().noResultAccuracy());

		if (Boolean.parseBoolean(System.getenv().getOrDefault("RETRIEVAL_AB_REPORT_ENABLED", "false"))) {
			writeAbReport(dataset, productsByKey, baseline, fullText);
		}

		assertThat(dataset.cases()).hasSize(60);
		assertThat(wikiPage.status()).isEqualTo("DRAFT");
		assertThat(fullText.metrics().recallAt20()).isGreaterThanOrEqualTo(baseline.metrics().recallAt20());
		assertThat(fullText.metrics().falseZeroRate()).isLessThanOrEqualTo(baseline.metrics().falseZeroRate());
		assertThat(fullText.metrics().hardConstraintViolationRateAt3()).isZero();
	}

	/** 평가 질문을 repository 후보 검색으로 실행하고 macro 품질 지표를 계산한다. */
	private EvaluationRun evaluate(
			List<EvaluationCase> cases,
			RetrievalVariant variant,
			WikiPage wikiPage,
			Map<String, SnapshotProduct> productsByKey) {
		double recall = 0;
		double ndcg = 0;
		int relevantQueries = 0;
		int falseZeros = 0;
		int usefulTopThreeQueries = 0;
		int noResultQueries = 0;
		int correctNoResults = 0;
		int topThreeCandidates = 0;
		int hardConstraintViolations = 0;
		List<EvaluationCaseResult> caseResults = new ArrayList<>();

		for (EvaluationCase evaluationCase : cases) {
			List<String> retrieved = search(evaluationCase, variant, wikiPage);
			List<String> topThree = retrieved.stream().limit(3).toList();
			for (String candidate : topThree) {
				topThreeCandidates++;
				if (violatesHardCondition(candidate, evaluationCase, productsByKey)) {
					hardConstraintViolations++;
				}
			}
			if (evaluationCase.expectNoResults()) {
				noResultQueries++;
				if (retrieved.isEmpty()) {
					correctNoResults++;
				}
				caseResults.add(new EvaluationCaseResult(evaluationCase, retrieved, 0));
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
			long topThreeHits = topThree.stream().filter(relevance::containsKey).count();
			recall += (double) hits / relevance.size();
			if (hits == 0) {
				falseZeros++;
			}
			if (topThreeHits > 0) {
				usefulTopThreeQueries++;
			}
			ndcg += ndcgAt3(new ArrayList<>(retrieved), relevance);
			caseResults.add(new EvaluationCaseResult(evaluationCase, retrieved, Math.toIntExact(topThreeHits)));
		}

		EvaluationMetrics metrics = new EvaluationMetrics(
				recall / relevantQueries,
				ndcg / relevantQueries,
				(double) falseZeros / relevantQueries,
				(double) correctNoResults / noResultQueries,
				(double) usefulTopThreeQueries / relevantQueries,
				topThreeCandidates == 0 ? 0 : (double) hardConstraintViolations / topThreeCandidates);
		return new EvaluationRun(metrics, caseResults);
	}

	/** 평가 조건과 선택적인 DRAFT Wiki 확장을 repository parameter로 변환해 후보 key를 반환한다. */
	private List<String> search(
			EvaluationCase evaluationCase,
			RetrievalVariant variant,
			WikiPage wikiPage) {
		String query = firstValue(evaluationCase, "productType");
		if (query == null) {
			query = firstValue(evaluationCase, "brand");
		}
		if (query == null) {
			query = evaluationCase.question();
		}
		boolean legacyStrict = variant == RetrievalVariant.LEGACY_STRICT;
		String merchant = legacyStrict
				? firstValue(evaluationCase, "merchant")
				: firstRequiredValue(evaluationCase, "merchant");
		Long maxPrice = parseLong(legacyStrict
				? firstValue(evaluationCase, "priceMax")
				: firstRequiredValue(evaluationCase, "priceMax"));
		String sizes = toCsv(legacyStrict
				? values(evaluationCase.required(), evaluationCase.preferred(), "size")
				: values(evaluationCase.required(), "size"));
		String colors = toCsv(legacyStrict
				? values(evaluationCase.required(), evaluationCase.preferred(), "color")
				: values(evaluationCase.required(), "color"));

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
					variant != RetrievalVariant.LEGACY_STRICT,
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
				.toList();
	}

	/** 구조화된 필수 조건을 독립적으로 다시 검사해 상위 후보의 위반 여부를 판정한다. */
	private boolean violatesHardCondition(
			String candidateKey,
			EvaluationCase evaluationCase,
			Map<String, SnapshotProduct> productsByKey) {
		SnapshotProduct product = productsByKey.get(candidateKey);
		if (product == null) {
			return true;
		}
		String merchant = firstRequiredValue(evaluationCase, "merchant");
		if (merchant != null && !product.site().equalsIgnoreCase(merchant)) {
			return true;
		}
		Long priceMax = parseLong(firstRequiredValue(evaluationCase, "priceMax"));
		if (priceMax != null && parsePrice(product.price()) > priceMax) {
			return true;
		}
		Long priceMin = parseLong(firstRequiredValue(evaluationCase, "priceMin"));
		if (priceMin != null && parsePrice(product.price()) < priceMin) {
			return true;
		}
		List<String> requiredSizes = values(evaluationCase.required(), "size");
		if (!requiredSizes.isEmpty() && requiredSizes.stream().noneMatch(
				required -> containsIgnoreCase(product.options().sizes(), required))) {
			return true;
		}
		List<String> requiredColors = values(evaluationCase.required(), "color");
		if (!requiredColors.isEmpty() && requiredColors.stream().noneMatch(
				required -> containsIgnoreCase(product.options().colors(), required))) {
			return true;
		}
		String stock = firstRequiredValue(evaluationCase, "stock");
		return "available".equalsIgnoreCase(stock) && !Boolean.TRUE.equals(product.inStock());
	}

	/** 옵션 문자열 목록에서 대소문자 차이를 무시한 정확 일치 항목을 찾는다. */
	private boolean containsIgnoreCase(List<String> values, String expected) {
		return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
	}

	/** 명시적 A/B 실행에서 자동 요약 JSON과 사람용 Markdown 및 원자료 CSV를 build report로 생성한다. */
	private void writeAbReport(
			EvaluationDataset dataset,
			Map<String, SnapshotProduct> productsByKey,
			EvaluationRun baseline,
			EvaluationRun fullText) throws Exception {
		String configuredOutput = System.getenv().get("RETRIEVAL_AB_OUTPUT_DIR");
		Path outputDirectory = configuredOutput == null || configuredOutput.isBlank()
				? Path.of("build", "reports", "retrieval-ab").toAbsolutePath().normalize()
				: Path.of(configuredOutput).toAbsolutePath().normalize();
		Files.createDirectories(outputDirectory);

		Map<String, EvaluationCaseResult> baselineById = resultsById(baseline);
		Map<String, EvaluationCaseResult> fullTextById = resultsById(fullText);
		int changedTopThree = 0;
		int recoveredZeros = 0;
		int becameZeros = 0;
		for (EvaluationCase evaluationCase : dataset.cases()) {
			List<String> before = baselineById.get(evaluationCase.id()).topThree();
			List<String> after = fullTextById.get(evaluationCase.id()).topThree();
			if (!before.equals(after)) {
				changedTopThree++;
			}
			if (before.isEmpty() && !after.isEmpty()) {
				recoveredZeros++;
			}
			if (!before.isEmpty() && after.isEmpty()) {
				becameZeros++;
			}
		}

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("datasetVersion", dataset.version());
		summary.put("datasetReviewStatus", dataset.reviewStatus());
		summary.put("humanReviewRequired", dataset.humanReviewRequired());
		summary.put("snapshot", dataset.snapshot());
		summary.put("caseCount", dataset.cases().size());
		summary.put("hardConstraintFields", List.of("merchant", "priceMin", "priceMax", "size", "color", "stock"));
		summary.put("before", metricsMap(baseline.metrics()));
		summary.put("after", metricsMap(fullText.metrics()));
		summary.put("delta", deltaMetrics(baseline.metrics(), fullText.metrics()));
		summary.put("changedTopThreeQueries", changedTopThree);
		summary.put("recoveredZeroQueries", recoveredZeros);
		summary.put("becameZeroQueries", becameZeros);
		summary.put("initialHumanReviewCaseIds", INITIAL_HUMAN_REVIEW_IDS);
		Files.writeString(
				outputDirectory.resolve("summary.json"),
				objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + System.lineSeparator());
		Files.writeString(
				outputDirectory.resolve("human-review.csv"),
				humanReviewCsv(dataset, productsByKey, baselineById, fullTextById));
		Files.writeString(
				outputDirectory.resolve("human-review-first10.md"),
				humanReviewMarkdown(dataset, productsByKey, baselineById, fullTextById));
		System.out.printf(Locale.ROOT, "retrieval A/B report=%s%n", outputDirectory);
	}

	/** 실행 결과를 질문 ID로 조회할 수 있는 순서 보존 map으로 변환한다. */
	private Map<String, EvaluationCaseResult> resultsById(EvaluationRun run) {
		return run.caseResults().stream().collect(java.util.stream.Collectors.toMap(
				result -> result.evaluationCase().id(),
				result -> result,
				(left, right) -> left,
				LinkedHashMap::new));
	}

	/** 지표 record를 JSON에 사용할 안정적인 key/value map으로 변환한다. */
	private Map<String, Double> metricsMap(EvaluationMetrics metrics) {
		Map<String, Double> values = new LinkedHashMap<>();
		values.put("recallAt20", metrics.recallAt20());
		values.put("ndcgAt3", metrics.ndcgAt3());
		values.put("falseZeroRate", metrics.falseZeroRate());
		values.put("noResultAccuracy", metrics.noResultAccuracy());
		values.put("usefulCandidateRateAt3", metrics.usefulCandidateRateAt3());
		values.put("hardConstraintViolationRateAt3", metrics.hardConstraintViolationRateAt3());
		return values;
	}

	/** after에서 before를 뺀 지표 변화량을 계산한다. */
	private Map<String, Double> deltaMetrics(EvaluationMetrics before, EvaluationMetrics after) {
		Map<String, Double> values = new LinkedHashMap<>();
		values.put("recallAt20", after.recallAt20() - before.recallAt20());
		values.put("ndcgAt3", after.ndcgAt3() - before.ndcgAt3());
		values.put("falseZeroRate", after.falseZeroRate() - before.falseZeroRate());
		values.put("noResultAccuracy", after.noResultAccuracy() - before.noResultAccuracy());
		values.put("usefulCandidateRateAt3", after.usefulCandidateRateAt3() - before.usefulCandidateRateAt3());
		values.put(
				"hardConstraintViolationRateAt3",
				after.hardConstraintViolationRateAt3() - before.hardConstraintViolationRateAt3());
		return values;
	}

	/** 60개 질문의 기존/현재 Top 3와 사람 판정 입력 칸을 CSV로 직렬화한다. */
	private String humanReviewCsv(
			EvaluationDataset dataset,
			Map<String, SnapshotProduct> productsByKey,
			Map<String, EvaluationCaseResult> baselineById,
			Map<String, EvaluationCaseResult> fullTextById) {
		List<String> lines = new ArrayList<>();
		lines.add(String.join(",", List.of(
				"review_priority", "case_id", "type", "question", "required", "preferred",
				"expect_no_results", "automatic_change", "baseline_auto_hits_at3", "fts_auto_hits_at3",
				"baseline_rank1", "baseline_rank2", "baseline_rank3",
				"fts_rank1", "fts_rank2", "fts_rank3",
				"baseline_rank1_human", "baseline_rank2_human", "baseline_rank3_human",
				"fts_rank1_human", "fts_rank2_human", "fts_rank3_human",
				"preferred_variant", "review_notes")));
		for (EvaluationCase evaluationCase : dataset.cases()) {
			EvaluationCaseResult before = baselineById.get(evaluationCase.id());
			EvaluationCaseResult after = fullTextById.get(evaluationCase.id());
			List<String> beforeTopThree = paddedCandidateLabels(before.topThree(), productsByKey);
			List<String> afterTopThree = paddedCandidateLabels(after.topThree(), productsByKey);
			List<String> cells = new ArrayList<>();
			cells.add(INITIAL_HUMAN_REVIEW_IDS.contains(evaluationCase.id()) ? "FIRST_10" : "LATER");
			cells.add(evaluationCase.id());
			cells.add(evaluationCase.type());
			cells.add(evaluationCase.question());
			cells.add(conditionSummary(evaluationCase.required()));
			cells.add(conditionSummary(evaluationCase.preferred()));
			cells.add(Boolean.toString(evaluationCase.expectNoResults()));
			cells.add(changeType(before.topThree(), after.topThree()));
			cells.add(Integer.toString(before.relevantHitsAt3()));
			cells.add(Integer.toString(after.relevantHitsAt3()));
			cells.addAll(beforeTopThree);
			cells.addAll(afterTopThree);
			cells.addAll(java.util.Collections.nCopies(8, ""));
			lines.add(cells.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(",")));
		}
		return String.join(System.lineSeparator(), lines) + System.lineSeparator();
	}

	/** 첫 10개 질문을 비개발자도 순서대로 판정할 수 있는 설명형 Markdown으로 만든다. */
	private String humanReviewMarkdown(
			EvaluationDataset dataset,
			Map<String, SnapshotProduct> productsByKey,
			Map<String, EvaluationCaseResult> baselineById,
			Map<String, EvaluationCaseResult> fullTextById) {
		Map<String, EvaluationCase> casesById = dataset.cases().stream().collect(
				java.util.stream.Collectors.toMap(EvaluationCase::id, evaluationCase -> evaluationCase));
		StringBuilder markdown = new StringBuilder();
		markdown.append("# 상품 검색 A/B 첫 10개 사람 검토표\n\n")
				.append("- 상태: DRAFT 사람 검토용\n")
				.append("- 평가 대상: 같은 구매 질문에 대한 A / 옛 검색과 B / 새 검색의 상위 후보\n\n")
				.append("## 먼저 읽을 설명\n\n")
				.append("10개 평가는 코드를 작성하거나 지표를 계산하는 작업이 아니다. ")
				.append("아래 10개 구매 질문을 하나씩 읽고 두 검색 결과 중 어느 쪽이 사용자에게 더 도움이 되는지 판단하는 예비 검토다. ")
				.append("첫 10개에서 판정 기준이 이해 가능하고 일관되는지 확인한 뒤 나머지 50개로 확대한다.\n\n")
				.append("- `필수 조건`: 어기면 부적합이다. 예를 들어 필수 사이즈가 없거나 최고 가격을 넘으면 부적합이다.\n")
				.append("- `선호 조건`: 만족하면 더 좋지만, 맞지 않거나 정보가 없다는 이유만으로 후보를 제거하지 않는다.\n")
				.append("- `적합`: 필수 조건을 모두 만족하고 질문 의도에도 맞는 근거가 충분하다.\n")
				.append("- `애매`: 필수 조건은 만족하지만 용도나 스타일처럼 판단 근거가 부족하다.\n")
				.append("- `부적합`: 필수 조건을 어기거나 명백히 다른 상품이다.\n")
				.append("- 상품 페이지는 현재 상태가 달라질 수 있으므로, 이 표의 수집 정보와 링크를 함께 보고 판단한다.\n\n")
				.append("예를 들어 `면접용 갈색 구두 265 10만 원 이하`에서 구두/265/10만 원 이하는 필수이고 면접용/갈색은 선호다. ")
				.append("가격과 사이즈는 맞지만 면접 적합성을 확인할 근거가 부족하면 `애매`로 판단하면 된다.\n\n")
				.append("## 검토 질문\n\n");

		int index = 1;
		for (String caseId : INITIAL_HUMAN_REVIEW_IDS) {
			EvaluationCase evaluationCase = casesById.get(caseId);
			if (evaluationCase == null) {
				continue;
			}
			EvaluationCaseResult before = baselineById.get(caseId);
			EvaluationCaseResult after = fullTextById.get(caseId);
			markdown.append("### ").append(index++).append(". ")
					.append(markdownText(evaluationCase.question())).append("\n\n")
					.append("- 질문 ID: `").append(caseId).append("`\n")
					.append("- 필수 조건: ").append(readableConditionSummary(evaluationCase.required())).append("\n")
					.append("- 선호 조건: ").append(readableConditionSummary(evaluationCase.preferred())).append("\n")
					.append("- 정답이 0건이어야 하는 질문: ")
					.append(evaluationCase.expectNoResults() ? "예" : "아니요").append("\n")
					.append("- 자동 비교 요약: ").append(readableChangeType(before.topThree(), after.topThree())).append("\n\n");
			appendCandidateSection(markdown, "A / 옛 검색", before.topThree(), productsByKey);
			appendCandidateSection(markdown, "B / 새 검색", after.topThree(), productsByKey);
			markdown.append("판정:\n\n")
					.append("- [ ] A / 옛 검색이 더 낫다\n")
					.append("- [ ] B / 새 검색이 더 낫다\n")
					.append("- [ ] 비슷하다\n")
					.append("- 판단 메모:\n\n");
		}
		markdown.append("## 검토가 끝나면\n\n")
				.append("각 후보의 체크박스와 질문별 최종 판정을 채운다. 판단이 어려웠던 조건은 메모에 적는다. ")
				.append("첫 10개 결과를 함께 확인해 기준을 고친 뒤에만 나머지 50개를 평가한다. ")
				.append("CSV는 이후 전체 결과를 집계할 때 사용하는 원자료이므로 지금은 열지 않아도 된다.\n");
		return markdown.toString();
	}

	/** 한 검색 방식의 상위 후보와 사람이 선택할 적합성 체크박스를 Markdown에 추가한다. */
	private void appendCandidateSection(
			StringBuilder markdown,
			String title,
			List<String> candidateKeys,
			Map<String, SnapshotProduct> productsByKey) {
		markdown.append("#### ").append(title).append("\n\n");
		if (candidateKeys.isEmpty()) {
			markdown.append("검색 결과 없음\n\n")
					.append("- [ ] 0건 반환이 적절하다\n")
					.append("- [ ] 찾았어야 할 후보를 놓쳤다\n\n");
			return;
		}
		int rank = 1;
		for (String candidateKey : candidateKeys.stream().limit(3).toList()) {
			SnapshotProduct product = productsByKey.get(candidateKey);
			if (product == null) {
				markdown.append(rank++).append(". `").append(candidateKey).append("` / snapshot 정보 없음\n\n");
				continue;
			}
			markdown.append(rank++).append(". [").append(markdownText(product.title())).append("](")
					.append(product.link()).append(")\n\n")
					.append("   - 판매처/상품 ID: ").append(markdownText(product.site())).append("/")
					.append(markdownText(product.sourceProductId())).append("\n")
					.append("   - 브랜드/분류: ").append(displayValue(product.brand())).append("/")
					.append(displayValue(product.categoryPath())).append("\n")
					.append("   - 수집 가격/재고: ").append(displayPrice(product.price())).append("/")
					.append(Boolean.TRUE.equals(product.inStock()) ? "재고 있음" : "재고 없음 또는 미확인").append("\n")
					.append("   - 수집 색상: ").append(displayColors(product)).append("\n")
					.append("   - 수집 사이즈: ").append(displayValues(product.options().sizes())).append("\n")
					.append("   - 내 판정: [ ] 적합  [ ] 애매  [ ] 부적합\n")
					.append("   - 판정 이유:\n\n");
		}
	}

	/** 평가 field 이름을 한국어 설명으로 바꿔 사람이 읽을 조건 요약을 만든다. */
	private String readableConditionSummary(List<EvaluationCondition> conditions) {
		if (conditions.isEmpty()) {
			return "없음";
		}
		Map<String, String> labels = Map.of(
				"brand", "브랜드",
				"productType", "상품 종류",
				"size", "사이즈",
				"color", "색상",
				"priceMax", "최고 가격",
				"priceMin", "최저 가격",
				"merchant", "판매처",
				"usage", "용도",
				"stock", "재고");
		return conditions.stream()
				.map(condition -> labels.getOrDefault(condition.field(), condition.field())
						+ "=" + markdownText(condition.value()))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	/** Markdown link label에서 대괄호와 역슬래시가 문법으로 해석되지 않게 변환한다. */
	private String markdownText(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
	}

	/** 비어 있는 단일 snapshot 값을 사람용 문구로 변환한다. */
	private String displayValue(String value) {
		return value == null || value.isBlank() ? "수집 정보 없음" : markdownText(value);
	}

	/** 가격 문자열에 통화 단위를 중복하지 않고 사람용 문구로 변환한다. */
	private String displayPrice(String price) {
		if (price == null || price.isBlank()) {
			return "수집 정보 없음";
		}
		return markdownText(price) + (price.contains("원") ? "" : "원");
	}

	/** 색상 목록이 사이즈와 동일한 수집 이상 징후를 사람이 놓치지 않게 표시한다. */
	private String displayColors(SnapshotProduct product) {
		List<String> colors = product.options().colors();
		List<String> sizes = product.options().sizes();
		if (colors != null && !colors.isEmpty() && colors.equals(sizes)) {
			return "수집값 이상 가능성: " + displayValues(colors);
		}
		return displayValues(colors);
	}

	/** 비어 있는 snapshot 목록을 사람용 문구로 변환한다. */
	private String displayValues(List<String> values) {
		return values == null || values.isEmpty()
				? "수집 정보 없음"
				: values.stream().map(this::markdownText).collect(java.util.stream.Collectors.joining(", "));
	}

	/** 기존/현재 후보 변화 유형을 비개발자가 이해할 한국어로 설명한다. */
	private String readableChangeType(List<String> before, List<String> after) {
		return switch (changeType(before, after)) {
			case "RECOVERED_ZERO" -> "옛 검색은 0건이었고 새 검색에서 후보를 찾음";
			case "BECAME_ZERO" -> "옛 검색에는 후보가 있었지만 새 검색은 0건";
			case "TOP3_CHANGED" -> "상위 3개 후보 또는 순서가 달라짐";
			default -> "상위 3개 후보와 순서가 같음";
		};
	}

	/** 후보 key 최대 3개를 사람이 읽을 수 있는 판매처/상품명/가격/URL로 채운다. */
	private List<String> paddedCandidateLabels(
			List<String> candidateKeys,
			Map<String, SnapshotProduct> productsByKey) {
		List<String> labels = new ArrayList<>();
		for (String candidateKey : candidateKeys.stream().limit(3).toList()) {
			SnapshotProduct product = productsByKey.get(candidateKey);
			labels.add(product == null
					? candidateKey
					: candidateKey + " | " + product.title() + " | " + product.price() + " | " + product.link());
		}
		while (labels.size() < 3) {
			labels.add("");
		}
		return labels;
	}

	/** 조건 목록을 사람이 확인하기 쉬운 field=value 목록으로 만든다. */
	private String conditionSummary(List<EvaluationCondition> conditions) {
		return conditions.stream()
				.map(condition -> condition.field() + "=" + condition.value())
				.collect(java.util.stream.Collectors.joining("; "));
	}

	/** 기존/현재 Top 3의 0건 복구와 순서 변화를 분류한다. */
	private String changeType(List<String> before, List<String> after) {
		if (before.isEmpty() && !after.isEmpty()) {
			return "RECOVERED_ZERO";
		}
		if (!before.isEmpty() && after.isEmpty()) {
			return "BECAME_ZERO";
		}
		return before.equals(after) ? "UNCHANGED" : "TOP3_CHANGED";
	}

	/** CSV 셀의 따옴표를 RFC 4180 방식으로 escape한다. */
	private String csvCell(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
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

	/** 도입 전 strict AND 재현을 위해 필수/선호 조건의 같은 field 값을 합친다. */
	private List<String> values(
			List<EvaluationCondition> required,
			List<EvaluationCondition> preferred,
			String field) {
		List<String> combined = new ArrayList<>(values(required, field));
		combined.addAll(values(preferred, field));
		return combined;
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

	/** EvaluationDataset은 60개 검색 품질 평가 질문과 검토 상태를 감싼다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvaluationDataset(
			String version,
			String snapshot,
			String reviewStatus,
			boolean humanReviewRequired,
			List<EvaluationCase> cases) {
	}

	/** EvaluationCase는 한 질문의 조건, relevance와 정답 없음 판정을 표현한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvaluationCase(
			String id,
			String type,
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

	/** RetrievalVariant는 도입 전 strict AND와 현재 FTS 및 DRAFT Wiki 비교 단계를 구분한다. */
	private enum RetrievalVariant {
		LEGACY_STRICT,
		CURRENT_FTS,
		DRAFT_WIKI
	}

	/** EvaluationRun은 한 검색 단계의 전체 지표와 질문별 순위 결과를 묶는다. */
	private record EvaluationRun(EvaluationMetrics metrics, List<EvaluationCaseResult> caseResults) {
	}

	/** EvaluationCaseResult는 한 질문의 상위 20개 후보와 자동 Top 3 적중 수를 보관한다. */
	private record EvaluationCaseResult(
			EvaluationCase evaluationCase,
			List<String> retrieved,
			int relevantHitsAt3) {
		/** 사람이 검토할 상위 후보를 최대 3개 반환한다. */
		private List<String> topThree() {
			return retrieved.stream().limit(3).toList();
		}
	}

	/** EvaluationMetrics는 한 검색 단계의 macro 품질 지표다. */
	private record EvaluationMetrics(
			double recallAt20,
			double ndcgAt3,
			double falseZeroRate,
			double noResultAccuracy,
			double usefulCandidateRateAt3,
			double hardConstraintViolationRateAt3) {
	}
}
