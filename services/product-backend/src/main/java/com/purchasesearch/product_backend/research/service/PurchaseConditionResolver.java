package com.purchasesearch.product_backend.research.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.purchasesearch.product_backend.knowledge.service.WikiConceptIndexService;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.AttributeCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.DerivedBy;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedShortText;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedText;

/** PurchaseConditionResolver는 사용자 원문을 보존하며 검색용 표준 조건과 범용 속성을 생성한다. */
@Service
public class PurchaseConditionResolver {

	private static final Map<String, CanonicalValue> COLORS = aliases(
			canonical("black", "color:black", "검정", "검은색", "블랙", "black"),
			canonical("white", "color:white", "흰색", "하양", "화이트", "white"),
			canonical("brown", "color:brown", "갈색", "브라운", "brown"),
			canonical("beige", "color:beige", "베이지", "beige"),
			canonical("gray", "color:gray", "회색", "그레이", "gray", "grey"),
			canonical("navy", "color:navy", "남색", "네이비", "navy"),
			canonical("blue", "color:blue", "파랑", "파란색", "블루", "blue"),
			canonical("red", "color:red", "빨강", "빨간색", "레드", "red"));
	private static final Map<String, CanonicalValue> PRODUCT_TYPES = aliases(
			canonical("구두", "category:dress-shoes", "구두", "정장화", "dress shoes", "dress-shoes"),
			canonical("운동화", "category:sports-shoes", "운동화", "스포츠화", "sneakers", "sneaker"),
			canonical("러닝화", "category:running-shoes", "러닝화", "런닝화", "running shoes"),
			canonical("로퍼", "category:loafers", "로퍼", "loafer", "loafers"),
			canonical("가방", "category:bags", "가방", "백", "bag", "bags"),
			canonical("노트북", "category:laptops", "노트북", "랩톱", "laptop", "notebook"));
	private static final Map<String, CanonicalValue> USAGES = aliases(
			canonical("출근", "usage:commute", "출근", "출근용", "통근", "commute"),
			canonical("운동", "usage:exercise", "운동", "운동용", "트레이닝", "exercise", "training"),
			canonical("면접", "usage:interview", "면접", "면접용", "interview"));

	private final WikiConceptIndexService wikiConceptIndexService;

	/** @param wikiConceptIndexService 사람이 검토한 synonym 조회 서비스 */
	public PurchaseConditionResolver(WikiConceptIndexService wikiConceptIndexService) {
		this.wikiConceptIndexService = wikiConceptIndexService;
	}

	/** @param input AI가 추출한 조건 @return 모호성을 표시한 DRAFT 표준 조건 */
	public PurchaseCondition resolveDraft(PurchaseCondition input) {
		return resolve(input, false);
	}

	/** @param input 사용자가 확인한 조건 @return 확인된 모호성을 다시 묻지 않는 표준 조건 */
	public PurchaseCondition resolveConfirmed(PurchaseCondition input) {
		return resolve(input, true);
	}

	/** 조건 종류별 정규화와 legacy 색상/사이즈의 범용 속성 투영을 한 번에 수행한다. */
	private PurchaseCondition resolve(PurchaseCondition input, boolean ambiguityAccepted) {
		List<String> missing = new ArrayList<>(input.missingConditions());
		ResolvedValue productTypeValue = resolveValue(input.productType().value(), PRODUCT_TYPES, "category", ambiguityAccepted);
		PrioritizedText productType = text(input.productType(), productTypeValue);
		List<PrioritizedShortText> usages = input.usage().stream()
				.map(value -> shortText(value, resolveValue(value.value(), USAGES, "usage", ambiguityAccepted)))
				.toList();
		List<PrioritizedShortText> colors = input.colors().stream()
				.map(value -> shortText(value, resolveValue(value.value(), COLORS, "color", ambiguityAccepted)))
				.toList();
		List<PrioritizedShortText> sizes = input.sizes().stream()
				.map(value -> shortText(value, resolveSize(value.value())))
				.toList();
		List<PrioritizedText> requirements = input.requirements().stream()
				.map(value -> text(value, original(value.value())))
				.toList();

		if (!ambiguityAccepted) {
			addConfirmation(missing, "상품 종류", productTypeValue);
			for (int index = 0; index < colors.size(); index++) {
				addConfirmation(missing, "색상", resolved(colors.get(index)));
			}
		}
		List<AttributeCondition> attributes = mergeAttributes(input.attributes(), colors, sizes, ambiguityAccepted);
		double confidence = minimumConfidence(input.confidence(), productTypeValue, usages, colors, sizes, attributes);
		return new PurchaseCondition(
				productType,
				usages,
				input.price(),
				colors,
				sizes,
				requirements,
				attributes,
				input.merchant(),
				List.copyOf(new LinkedHashSet<>(missing)),
				input.assumptions(),
				confidence,
				true);
	}

	/** 기존 전용 필드와 신규 범용 필드를 key/value 기준으로 중복 없이 병합한다. */
	private List<AttributeCondition> mergeAttributes(
			List<AttributeCondition> supplied,
			List<PrioritizedShortText> colors,
			List<PrioritizedShortText> sizes,
			boolean ambiguityAccepted) {
		Map<String, AttributeCondition> merged = new LinkedHashMap<>();
		for (AttributeCondition attribute : supplied) {
			ResolvedValue value = switch (attribute.key()) {
				case "color" -> resolveValue(attribute.value(), COLORS, "color", ambiguityAccepted);
				case "size" -> resolveSize(attribute.value());
				default -> original(attribute.value());
			};
			AttributeCondition normalized = new AttributeCondition(
					attribute.key(), attribute.value(), attribute.priority(), value.normalizedValue(),
					value.canonicalId(), value.confidence(), value.derivedBy(),
					value.requiresConfirmation() && !ambiguityAccepted);
			merged.put(attributeKey(normalized), normalized);
		}
		colors.forEach(value -> putLegacyAttribute(merged, "color", value));
		sizes.forEach(value -> putLegacyAttribute(merged, "size", value));
		return List.copyOf(merged.values());
	}

	/** legacy 색상/사이즈를 같은 범용 속성으로 투영한다. */
	private void putLegacyAttribute(
			Map<String, AttributeCondition> attributes,
			String key,
			PrioritizedShortText value) {
		AttributeCondition attribute = new AttributeCondition(
				key, value.value(), value.priority(), value.normalizedValue(), value.canonicalId(),
				value.confidence(), value.derivedBy(), Boolean.TRUE.equals(value.requiresConfirmation()));
		attributes.putIfAbsent(attributeKey(attribute), attribute);
	}

	/** 범용 속성의 정규화 key를 만든다. */
	private String attributeKey(AttributeCondition attribute) {
		return attribute.key() + ":" + attribute.effectiveValue().toLowerCase(Locale.ROOT);
	}

	/** exact 사전, PUBLISHED Wiki synonym, 한 글자 오타 순으로 값을 해석한다. */
	private ResolvedValue resolveValue(
			String rawValue,
			Map<String, CanonicalValue> dictionary,
			String namespace,
			boolean ambiguityAccepted) {
		String normalizedRaw = normalize(rawValue);
		CanonicalValue exact = dictionary.get(normalizedRaw);
		if (exact != null) {
			return new ResolvedValue(rawValue, exact.value(), exact.canonicalId(), 1.0, DerivedBy.dictionary, false);
		}
		var wiki = wikiConceptIndexService.resolveSynonym(rawValue);
		if (wiki.isPresent()) {
			String wikiValue = wiki.get().value().trim();
			CanonicalValue canonical = dictionary.get(normalize(wikiValue));
			return new ResolvedValue(
					rawValue,
					canonical == null ? wikiValue : canonical.value(),
					canonical == null ? canonicalId(namespace, wikiValue) : canonical.canonicalId(),
					wiki.get().confidence(),
					DerivedBy.wiki,
					false);
		}
		Map.Entry<String, CanonicalValue> fuzzy = nearest(normalizedRaw, dictionary);
		if (fuzzy != null) {
			return new ResolvedValue(
					rawValue, fuzzy.getValue().value(), fuzzy.getValue().canonicalId(), 0.75,
					DerivedBy.fuzzy, !ambiguityAccepted);
		}
		return original(rawValue);
	}

	/** 숫자 신발 사이즈의 mm 표기를 검색용 숫자와 canonical ID로 정규화한다. */
	private ResolvedValue resolveSize(String rawValue) {
		String normalized = normalize(rawValue).replace(" ", "");
		if (normalized.matches("[0-9]+(?:\\.[0-9]+)?mm")) {
			normalized = normalized.substring(0, normalized.length() - 2);
		}
		if (normalized.matches("[0-9]+(?:\\.[0-9]+)?")) {
			return new ResolvedValue(rawValue, normalized, "size:" + normalized.replace('.', '_') + "mm",
					1.0, DerivedBy.rule, false);
		}
		return original(rawValue);
	}

	/** 정규화되지 않은 값도 원문과 근거를 명시해 보존한다. */
	private ResolvedValue original(String value) {
		return new ResolvedValue(value, value.trim(), null, 1.0, DerivedBy.original, false);
	}

	/** 두 글자 이상 문자열에서 편집 거리 1인 유일한 사전 항목을 오타 후보로 선택한다. */
	private Map.Entry<String, CanonicalValue> nearest(String value, Map<String, CanonicalValue> dictionary) {
		if (value.length() < 2) {
			return null;
		}
		Map.Entry<String, CanonicalValue> match = null;
		for (Map.Entry<String, CanonicalValue> entry : dictionary.entrySet()) {
			if (editDistance(value, entry.getKey()) <= 1) {
				if (match != null && !match.getValue().canonicalId().equals(entry.getValue().canonicalId())) {
					return null;
				}
				match = entry;
			}
		}
		return match;
	}

	/** 두 문자열의 Levenshtein 편집 거리를 계산한다. */
	private int editDistance(String left, String right) {
		int[] previous = new int[right.length() + 1];
		for (int index = 0; index <= right.length(); index++) {
			previous[index] = index;
		}
		for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
			int[] current = new int[right.length() + 1];
			current[0] = leftIndex;
			for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
				int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
				current[rightIndex] = Math.min(
						Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
						previous[rightIndex - 1] + cost);
			}
			previous = current;
		}
		return previous[right.length()];
	}

	/** 모호한 자동 교정은 사용자가 확인할 문장으로 한 번만 추가한다. */
	private void addConfirmation(List<String> missing, String label, ResolvedValue value) {
		if (value.requiresConfirmation()) {
			missing.add(label + " ‘" + value.rawValue() + "’을(를) ‘" + value.normalizedValue() + "’으로 이해했습니다.");
		}
	}

	/** 기존 긴 텍스트 조건에 정규화 metadata를 결합한다. */
	private PrioritizedText text(PrioritizedText input, ResolvedValue resolved) {
		return new PrioritizedText(input.value(), input.priority(), resolved.normalizedValue(),
				resolved.canonicalId(), resolved.confidence(), resolved.derivedBy(), resolved.requiresConfirmation());
	}

	/** 기존 짧은 텍스트 조건에 정규화 metadata를 결합한다. */
	private PrioritizedShortText shortText(PrioritizedShortText input, ResolvedValue resolved) {
		return new PrioritizedShortText(input.value(), input.priority(), resolved.normalizedValue(),
				resolved.canonicalId(), resolved.confidence(), resolved.derivedBy(), resolved.requiresConfirmation());
	}

	/** 이미 정규화된 짧은 조건을 내부 판정값으로 되돌린다. */
	private ResolvedValue resolved(PrioritizedShortText value) {
		return new ResolvedValue(value.value(), value.effectiveValue(), value.canonicalId(),
				value.confidence() == null ? 1.0 : value.confidence(), value.derivedBy(),
				Boolean.TRUE.equals(value.requiresConfirmation()));
	}

	/** 전체 신뢰도는 AI 추출값과 개별 정규화 최솟값을 넘지 않게 한다. */
	private double minimumConfidence(
			Double inputConfidence,
			ResolvedValue productType,
			List<PrioritizedShortText> usages,
			List<PrioritizedShortText> colors,
			List<PrioritizedShortText> sizes,
			List<AttributeCondition> attributes) {
		double minimum = inputConfidence == null ? 1.0 : inputConfidence;
		minimum = Math.min(minimum, productType.confidence());
		for (PrioritizedShortText value : concat(usages, colors, sizes)) {
			minimum = Math.min(minimum, value.confidence() == null ? 1.0 : value.confidence());
		}
		for (AttributeCondition value : attributes) {
			minimum = Math.min(minimum, value.confidence() == null ? 1.0 : value.confidence());
		}
		return minimum;
	}

	/** 여러 조건 목록을 confidence 순회용 단일 목록으로 합친다. */
	@SafeVarargs
	private final List<PrioritizedShortText> concat(List<PrioritizedShortText>... lists) {
		List<PrioritizedShortText> values = new ArrayList<>();
		for (List<PrioritizedShortText> list : lists) {
			values.addAll(list);
		}
		return values;
	}

	/** 사전 검색을 위해 공백과 대소문자를 통일한다. */
	private String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	/** Wiki만 존재하는 값에 안전한 canonical ID를 생성한다. */
	private String canonicalId(String namespace, String value) {
		String slug = normalize(value).replaceAll("[^a-z0-9가-힣]+", "-").replaceAll("(^-|-$)", "");
		return StringUtils.hasText(slug) ? namespace + ":" + slug : null;
	}

	/** 하나의 표준값과 여러 alias를 묶는다. */
	private static CanonicalDefinition canonical(String value, String canonicalId, String... aliases) {
		return new CanonicalDefinition(new CanonicalValue(value, canonicalId), List.of(aliases));
	}

	/** 표준 정의 목록을 소문자 alias lookup으로 변환한다. */
	private static Map<String, CanonicalValue> aliases(CanonicalDefinition... definitions) {
		Map<String, CanonicalValue> values = new LinkedHashMap<>();
		for (CanonicalDefinition definition : definitions) {
			definition.aliases().forEach(alias -> values.put(alias.toLowerCase(Locale.ROOT), definition.value()));
		}
		return Map.copyOf(values);
	}

	/** CanonicalValue는 표준 검색값과 안정적인 식별자를 묶는다. */
	private record CanonicalValue(String value, String canonicalId) {
	}

	/** CanonicalDefinition은 하나의 표준값이 허용하는 입력 표현 목록이다. */
	private record CanonicalDefinition(CanonicalValue value, List<String> aliases) {
	}

	/** ResolvedValue는 원문과 표준값, 근거 및 모호성을 보존한다. */
	private record ResolvedValue(
			String rawValue,
			String normalizedValue,
			String canonicalId,
			double confidence,
			DerivedBy derivedBy,
			boolean requiresConfirmation) {
	}
}
