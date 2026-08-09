package com.purchasesearch.product_backend.product.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.product.dto.ProductCandidateRequest;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse.CandidateAssessment;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse.CandidateGroup;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse.CandidateListing;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse.GroupingBasis;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse.MatchStatus;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.OptionView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.product.service.ProductQueryService.CandidateSearchResult;
import com.purchasesearch.product_backend.product.service.ProductQueryService.RetrievalSignal;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.ConditionPriority;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedShortText;

/**
 * ProductCandidateService는 사용자 질문 문맥을 보존하면서 기존 DB 상품 검색 결과를 후보 계약으로 변환한다.
 */
@Service
public class ProductCandidateService {

	private static final int DEFAULT_LIMIT = 5;
	private static final int RETRIEVAL_POOL_LIMIT = 50;
	private static final Map<String, String> COLOR_ALIASES = Map.ofEntries(
			Map.entry("검정", "black"),
			Map.entry("검은색", "black"),
			Map.entry("흰색", "white"),
			Map.entry("하양", "white"),
			Map.entry("베이지", "beige"),
			Map.entry("갈색", "brown"),
			Map.entry("회색", "gray"),
			Map.entry("파랑", "blue"),
			Map.entry("남색", "navy"),
			Map.entry("빨강", "red"));

	private final ProductQueryService productQueryService;

	/**
	 * 기존 상품 조회 서비스를 후보 조회 use case에 연결한다.
	 *
	 * @param productQueryService PostgreSQL 최신 상품 조회 서비스
	 */
	public ProductCandidateService(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	/**
	 * 명시적 검색어로 DB 상품을 조회하고 원본 질문과 함께 후보를 반환한다.
	 *
	 * @param request 원본 질문, 검색어와 후보 제한
	 * @return 최신 상품 후보와 검색 결과 범위
	 */
	public ProductCandidateResponse findCandidates(ProductCandidateRequest request) {
		int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
		String question = request.question().trim();
		String query = request.query().trim();
		String merchant = request.merchant() == null ? null : request.merchant().trim();
		ProductSearchResponse result = productQueryService.search(
				merchant,
				query,
				Math.max(RETRIEVAL_POOL_LIMIT, limit));
		List<RankedCandidate> ranked = result.products().stream()
				.map(product -> new RankedCandidate(product, null))
				.toList();
		List<CandidateGroup> groups = expandGroups(groupCandidates(ranked, limit), null);
		List<ProductSummary> representatives = representativeProducts(groups);

		return new ProductCandidateResponse(
				question,
				query,
				result.totalCount(),
				result.hasNext() || result.products().size() > representatives.size(),
				representatives,
				List.of(),
				groups);
	}

	/**
	 * 사용자가 확인한 구매 조건을 최신 가격, 옵션과 재고가 일치하는 DB 후보로 변환한다.
	 *
	 * @param question 사용자 원문 질문
	 * @param conditions 사용자 확인을 마친 구매 조건
	 * @return 공통 조건을 적용한 상품군 최대 5개와 각 상품군의 전체 판매 행
	 */
	public ProductCandidateResponse findCandidates(String question, PurchaseCondition conditions) {
		boolean requiredPrice = conditions.price().priority() == ConditionPriority.required;
		String sizesCsv = toRequiredCsv(conditions.sizes(), true);
		String colorsCsv = toRequiredCsv(conditions.colors(), false);
		CandidateSearchResult searchResult = productQueryService.searchCandidates(
				conditions.merchant(),
				conditions.productType().value(),
				requiredPrice ? conditions.price().min() : null,
				requiredPrice ? conditions.price().max() : null,
				requiredPrice ? conditions.price().currency() : null,
				sizesCsv,
				colorsCsv,
				RETRIEVAL_POOL_LIMIT);
		ProductSearchResponse result = searchResult.response();
		List<RankedCandidate> ranked = result.products().stream()
				.map(product -> new RankedCandidate(
						product,
						assessCandidate(product, conditions, searchResult.signals().get(product.id()))))
				.sorted(candidateComparator())
				.toList();
		List<CandidateGroup> groups = expandGroups(groupCandidates(ranked, DEFAULT_LIMIT), conditions);
		List<ProductSummary> representatives = representativeProducts(groups);
		List<CandidateAssessment> representativeAssessments = groups.stream()
				.map(CandidateGroup::listings)
				.map(List::getFirst)
				.map(CandidateListing::assessment)
				.toList();
		return new ProductCandidateResponse(
				question.trim(),
				conditions.productType().value().trim(),
				result.totalCount(),
				result.hasNext() || ranked.size() > representatives.size(),
				representatives,
				representativeAssessments,
				groups);
	}

	/** 후보의 상품 종류 관련성과 선호 조건 일치를 우선하는 결정론적 정렬 규칙을 만든다. */
	private Comparator<RankedCandidate> candidateComparator() {
		return Comparator.comparingDouble(
					(RankedCandidate candidate) -> candidate.assessment().keywordScore()).reversed()
				.thenComparing(Comparator.comparingInt(this::preferredMatchCount).reversed())
				.thenComparing(
						candidate -> candidate.assessment().wikiConceptScore(),
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(
						candidate -> candidate.assessment().semanticScore(),
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(Comparator.comparingDouble(
						(RankedCandidate candidate) -> candidate.assessment().freshnessScore()).reversed())
				.thenComparing(Comparator.comparingDouble(
						(RankedCandidate candidate) -> candidate.assessment().evidenceCompletenessScore()).reversed())
				.thenComparing(candidate -> candidate.product().id(), Comparator.reverseOrder());
	}

	/** 선호 조건 중 수집 사실로 확인된 개수를 후보 정렬용 정수로 계산한다. */
	private int preferredMatchCount(RankedCandidate candidate) {
		return candidate.assessment() == null ? 0 : candidate.assessment().matchReasons().size();
	}

	/** 정렬된 판매처 상품을 안전한 파생 key로 묶고 검토 Wiki 하위 개념 다양성을 보존한다. */
	private List<CandidateGroup> groupCandidates(List<RankedCandidate> ranked, int limit) {
		Map<String, List<RankedCandidate>> grouped = new LinkedHashMap<>();
		for (RankedCandidate candidate : ranked) {
			grouped.computeIfAbsent(groupKey(candidate.product()), ignored -> new java.util.ArrayList<>())
					.add(candidate);
		}
		List<Map.Entry<String, List<RankedCandidate>>> entries = List.copyOf(grouped.entrySet());
		List<Map.Entry<String, List<RankedCandidate>>> selected = new java.util.ArrayList<>();
		Set<String> selectedGroups = new HashSet<>();
		Set<String> selectedConcepts = new HashSet<>();
		for (Map.Entry<String, List<RankedCandidate>> entry : entries) {
			String concept = wikiConceptKey(entry.getValue());
			if (concept != null && selectedConcepts.add(concept)) {
				selected.add(entry);
				selectedGroups.add(entry.getKey());
				if (selected.size() == limit) {
					break;
				}
			}
		}
		for (Map.Entry<String, List<RankedCandidate>> entry : entries) {
			if (selected.size() == limit) {
				break;
			}
			if (selectedGroups.add(entry.getKey())) {
				selected.add(entry);
			}
		}
		return selected.stream()
				.map(entry -> toGroup(entry.getKey(), entry.getValue()))
				.toList();
	}

	/** 상품군 대표 판정에서 검토 Wiki 관계 설명을 다양성 key로 추출한다. */
	private String wikiConceptKey(List<RankedCandidate> candidates) {
		CandidateAssessment assessment = candidates.getFirst().assessment();
		if (assessment == null) {
			return null;
		}
		return assessment.matchReasons().stream()
				.filter(reason -> reason.startsWith("검토 Wiki:"))
				.findFirst()
				.orElse(null);
	}

	/** 파생 상품군의 대표 정보와 원본 판매처 상품 및 범용 속성을 응답으로 변환한다. */
	private CandidateGroup toGroup(String groupId, List<RankedCandidate> candidates) {
		ProductSummary representative = candidates.getFirst().product();
		return new CandidateGroup(
				groupId,
				representative.name(),
				representative.brand(),
				representative.categoryPath(),
				GroupingBasis.DERIVED,
				0.8,
				candidates.stream()
						.map(candidate -> new CandidateListing(
								candidate.product(),
								listingAttributes(candidate.product()),
								candidate.assessment()))
						.toList());
	}

	/** 상위 상품군마다 조건 검색에서 빠진 다른 색상과 품절 판매 행까지 다시 조회한다. */
	private List<CandidateGroup> expandGroups(
			List<CandidateGroup> groups,
			PurchaseCondition conditions) {
		return groups.stream()
				.map(group -> expandGroup(group, conditions))
				.toList();
	}

	/** 대표 후보는 첫 선택으로 유지하고 같은 상품군 전체 판매 행에 개별 조건 판정을 붙인다. */
	private CandidateGroup expandGroup(CandidateGroup group, PurchaseCondition conditions) {
		CandidateListing representative = group.listings().getFirst();
		CandidateAssessment representativeAssessment = representative.assessment();
		RetrievalSignal inheritedSignal = representativeAssessment == null
				? null
				: new RetrievalSignal(
						representativeAssessment.keywordScore(),
						representativeAssessment.semanticScore(),
						representativeAssessment.wikiConceptScore(),
						representativeAssessment.matchReasons().stream()
								.filter(reason -> reason.startsWith("검토 Wiki:"))
								.toList());
		List<CandidateListing> expanded = productQueryService.findFamilyListings(representative.product()).stream()
				.sorted(Comparator
						.comparing((ProductSummary product) -> product.id() != representative.product().id())
						.thenComparing(product -> !"available".equals(product.stockStatus()))
						.thenComparing(ProductSummary::externalId))
				.map(product -> new CandidateListing(
						product,
						listingAttributes(product),
						conditions == null ? null : assessCandidate(product, conditions, inheritedSignal)))
				.toList();
		return new CandidateGroup(
				group.groupId(),
				group.name(),
				group.brand(),
				group.categoryPath(),
				group.groupingBasis(),
				group.groupingConfidence(),
				expanded.isEmpty() ? group.listings() : expanded);
	}

	/** 기존 후보 계약과의 호환성을 위해 각 상품군의 첫 판매처 상품을 대표 후보로 반환한다. */
	private List<ProductSummary> representativeProducts(List<CandidateGroup> groups) {
		return groups.stream()
				.map(CandidateGroup::listings)
				.map(List::getFirst)
				.map(CandidateListing::product)
				.toList();
	}

	/** 현재 옵션에서 확인된 색상과 사이즈를 범용 속성 map으로 변환한다. */
	private Map<String, List<String>> listingAttributes(ProductSummary product) {
		Map<String, List<String>> attributes = new LinkedHashMap<>();
		addAttribute(attributes, "color", product.options(), OptionView::color);
		addAttribute(attributes, "size", product.options(), OptionView::size);
		return Map.copyOf(attributes);
	}

	/** 구매 가능한 옵션의 한 속성을 중복 없는 표시 값 목록으로 추가한다. */
	private void addAttribute(
			Map<String, List<String>> attributes,
			String key,
			List<OptionView> options,
			Function<OptionView, String> extractor) {
		List<String> values = options.stream()
				.filter(option -> "available".equals(option.stockStatus()))
				.map(extractor)
				.filter(value -> value != null && !value.isBlank())
				.distinct()
				.collect(Collectors.toList());
		if (!values.isEmpty()) {
			attributes.put(key, List.copyOf(values));
		}
	}

	/** 판매처/브랜드/상품명/카테고리가 모두 같은 행만 묶는 보수적인 파생 key를 만든다. */
	private String groupKey(ProductSummary product) {
		String category = String.join("/", product.categoryPath());
		return "derived:"
				+ normalizeGroupPart(product.merchant()) + ":"
				+ normalizeGroupPart(product.brand()) + ":"
				+ normalizeGroupPart(product.name()) + ":"
				+ normalizeGroupPart(category);
	}

	/** 파생 key의 문자열을 소문자와 단일 공백으로 정규화한다. */
	private String normalizeGroupPart(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	/** RankedCandidate는 정렬 과정에서 판매처 상품과 계산한 판정을 함께 보존한다. */
	private record RankedCandidate(ProductSummary product, CandidateAssessment assessment) {
	}

	/** 최신 상품 사실과 옵션을 구매 조건에 대조해 후보별 설명 가능한 판정을 만든다. */
	private CandidateAssessment assessCandidate(
			ProductSummary product,
			PurchaseCondition conditions,
			RetrievalSignal retrievalSignal) {
		MatchStatus sizeStatus = assessOptions(conditions.sizes(), product.options(), OptionView::size, true);
		MatchStatus colorStatus = assessOptions(conditions.colors(), product.options(), OptionView::color, false);
		List<String> reasons = new java.util.ArrayList<>();
		List<String> relaxed = new java.util.ArrayList<>();
		List<String> unknown = new java.util.ArrayList<>();
		if (retrievalSignal != null) {
			reasons.addAll(retrievalSignal.wikiReasons());
		}

		addOptionAssessment("사이즈", conditions.sizes(), sizeStatus, reasons, relaxed, unknown);
		addOptionAssessment("색상", conditions.colors(), colorStatus, reasons, relaxed, unknown);
		addPriceAssessment(product, conditions, reasons, relaxed, unknown);
		conditions.usage().forEach(condition -> addUnsupportedAssessment(
				"용도", condition.value(), condition.priority(), relaxed, unknown));
		conditions.requirements().forEach(condition -> addUnsupportedAssessment(
				"추가 조건", condition.value(), condition.priority(), relaxed, unknown));

		return new CandidateAssessment(
				product.id(),
				retrievalSignal == null ? 0.0 : roundScore(retrievalSignal.keywordScore()),
				retrievalSignal == null || retrievalSignal.semanticScore() == null
						? null
						: roundScore(retrievalSignal.semanticScore()),
				retrievalSignal == null || retrievalSignal.wikiConceptScore() == null
						? null
						: roundScore(retrievalSignal.wikiConceptScore()),
				freshnessScore(product),
				evidenceCompletenessScore(product),
				sizeStatus,
				colorStatus,
				reasons,
				relaxed,
				unknown);
	}

	/** 수집 경과 시간을 1일/7일/30일 경계의 설명 가능한 최신성 점수로 변환한다. */
	private double freshnessScore(ProductSummary product) {
		if (product.source() == null || product.source().collectedAt() == null) {
			return 0.0;
		}
		OffsetDateTime now = OffsetDateTime.now(product.source().collectedAt().getOffset());
		long ageDays = Math.max(0, Duration.between(product.source().collectedAt(), now).toDays());
		if (ageDays <= 1) {
			return 1.0;
		}
		if (ageDays <= 7) {
			return 0.8;
		}
		if (ageDays <= 30) {
			return 0.5;
		}
		return 0.2;
	}

	/** 최신 가격/재고와 provenance 3개 필드의 제공 비율을 계산한다. */
	private double evidenceCompletenessScore(ProductSummary product) {
		int present = 0;
		present += product.price() == null ? 0 : 1;
		present += product.stockStatus() == null || product.stockStatus().isBlank() ? 0 : 1;
		if (product.source() != null) {
			present += product.source().sourceUrl() == null || product.source().sourceUrl().isBlank() ? 0 : 1;
			present += product.source().collectedAt() == null ? 0 : 1;
			present += product.source().collectorVersion() == null
					|| product.source().collectorVersion().isBlank() ? 0 : 1;
		}
		return roundScore(present / 5.0);
	}

	/** API 점수가 실행 환경의 부동소수점 표현에 흔들리지 않도록 소수 넷째 자리로 고정한다. */
	private double roundScore(double value) {
		return Math.round(Math.max(0.0, Math.min(1.0, value)) * 10_000.0) / 10_000.0;
	}

	/** 최신 재고 옵션에 확인 가능한 값이 있는지와 요청 값의 일치 여부를 판정한다. */
	private MatchStatus assessOptions(
			List<PrioritizedShortText> conditions,
			List<OptionView> options,
			Function<OptionView, String> valueExtractor,
			boolean size) {
		if (conditions.isEmpty()) {
			return MatchStatus.UNKNOWN;
		}
		List<String> observed = options.stream()
				.filter(option -> "available".equals(option.stockStatus()))
				.map(valueExtractor)
				.filter(value -> value != null && !value.isBlank())
				.map(value -> normalizeValue(value, size))
				.distinct()
				.toList();
		if (observed.isEmpty()) {
			return MatchStatus.UNKNOWN;
		}
		boolean matched = conditions.stream()
				.map(PrioritizedShortText::value)
				.map(value -> normalizeValue(value, size))
				.anyMatch(observed::contains);
		return matched ? MatchStatus.MATCH : MatchStatus.MISMATCH;
	}

	/** 옵션 판정을 확인된 이유, 완화된 선호 또는 확인 불가 조건으로 분류한다. */
	private void addOptionAssessment(
			String label,
			List<PrioritizedShortText> conditions,
			MatchStatus status,
			List<String> reasons,
			List<String> relaxed,
			List<String> unknown) {
		if (conditions.isEmpty()) {
			return;
		}
		String values = conditions.stream().map(PrioritizedShortText::value).distinct()
				.reduce((left, right) -> left + "/" + right).orElse("");
		if (status == MatchStatus.MATCH) {
			reasons.add(label + " " + values + " 재고 확인");
			return;
		}
		boolean required = conditions.stream()
				.anyMatch(condition -> condition.priority() == ConditionPriority.required);
		String description = label + " " + values + " "
				+ (status == MatchStatus.UNKNOWN ? "판매처 정보 없음" : "불일치");
		(required ? unknown : relaxed).add(description);
	}

	/** 가격 조건의 충족, 선호 완화 또는 가격 정보 부족을 후보 설명에 반영한다. */
	private void addPriceAssessment(
			ProductSummary product,
			PurchaseCondition conditions,
			List<String> reasons,
			List<String> relaxed,
			List<String> unknown) {
		if (product.price() == null) {
			unknown.add("가격 판매처 정보 없음");
			return;
		}
		boolean matched = product.price().currency().equals(conditions.price().currency())
				&& (conditions.price().min() == null || product.price().amount() >= conditions.price().min())
				&& (conditions.price().max() == null || product.price().amount() <= conditions.price().max());
		if (matched) {
			reasons.add("가격 조건 충족");
		} else if (conditions.price().priority() == ConditionPriority.preferred) {
			relaxed.add("선호 가격 범위 불일치");
		} else {
			unknown.add("필수 가격 범위 불일치");
		}
	}

	/** 아직 검색 근거가 없는 용도와 추가 조건을 강도에 따라 완화 또는 확인 불가로 표시한다. */
	private void addUnsupportedAssessment(
			String label,
			String value,
			ConditionPriority priority,
			List<String> relaxed,
			List<String> unknown) {
		String description = label + " " + value + " 평가 근거 없음";
		(priority == ConditionPriority.required ? unknown : relaxed).add(description);
	}

	/** 필수 조건만 SQL의 정확한 목록 비교에 사용할 쉼표 경계 문자열로 변환한다. */
	private String toRequiredCsv(List<PrioritizedShortText> conditions, boolean size) {
		if (conditions.isEmpty()) {
			return null;
		}
		String joined = conditions.stream()
				.filter(condition -> condition.priority() == ConditionPriority.required)
				.map(PrioritizedShortText::value)
				.map(value -> normalizeValue(value, size))
				.filter(value -> !value.isBlank())
				.distinct()
				.reduce((left, right) -> left + "," + right)
				.orElse("");
		return joined.isBlank() ? null : "," + joined + ",";
	}

	/** 화면 표기 사이즈와 한국어 색상 별칭을 Collector 옵션 값에 맞게 정규화한다. */
	private String normalizeValue(String value, boolean size) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (size && normalized.matches("[0-9]+(?:\\.[0-9]+)?mm")) {
			normalized = normalized.substring(0, normalized.length() - 2);
		}
		return size ? normalized : COLOR_ALIASES.getOrDefault(normalized, normalized);
	}
}
