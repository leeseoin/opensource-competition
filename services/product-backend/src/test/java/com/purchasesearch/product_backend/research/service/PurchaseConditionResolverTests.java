package com.purchasesearch.product_backend.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.purchasesearch.product_backend.knowledge.service.WikiConceptIndexService;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.ConditionPriority;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PriceCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedShortText;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedText;

/** PurchaseConditionResolverTests는 사용자 표현의 표준화와 모호성 확인 계약을 검증한다. */
class PurchaseConditionResolverTests {

	private PurchaseConditionResolver resolver;

	/** 각 테스트가 DB 없이 deterministic 사전과 fuzzy 규칙만 실행하도록 Wiki를 격리한다. */
	@BeforeEach
	void setUp() {
		WikiConceptIndexService wiki = mock(WikiConceptIndexService.class);
		when(wiki.resolveSynonym(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
		resolver = new PurchaseConditionResolver(wiki);
	}

	/** 갈색/BROWN 표현과 mm 사이즈가 같은 canonical 조건과 범용 속성으로 변환되는지 검증한다. */
	@Test
	void resolvesColorAliasesAndSizeIntoGenericAttributes() {
		PurchaseCondition resolved = resolver.resolveDraft(condition("갈색", "270mm"));

		assertThat(resolved.colors().getFirst().normalizedValue()).isEqualTo("brown");
		assertThat(resolved.colors().getFirst().canonicalId()).isEqualTo("color:brown");
		assertThat(resolved.sizes().getFirst().normalizedValue()).isEqualTo("270");
		assertThat(resolved.attributes())
				.extracting(attribute -> attribute.key() + ":" + attribute.effectiveValue())
				.containsExactly("color:brown", "size:270");

		PurchaseCondition english = resolver.resolveDraft(condition("BROWN", "270"));
		assertThat(english.colors().getFirst().canonicalId()).isEqualTo("color:brown");
	}

	/** 한 글자 색상 오타는 자동 필수 적용하지 않고 DRAFT 확인 질문으로 남기는지 검증한다. */
	@Test
	void fuzzyColorRequiresConfirmationUntilUserAcceptsIt() {
		PurchaseCondition draft = resolver.resolveDraft(condition("브로운", "270"));

		assertThat(draft.colors().getFirst().normalizedValue()).isEqualTo("brown");
		assertThat(draft.colors().getFirst().requiresConfirmation()).isTrue();
		assertThat(draft.missingConditions()).anyMatch(value -> value.contains("브로운") && value.contains("brown"));
		assertThat(draft.confidence()).isEqualTo(0.75);

		PurchaseCondition confirmed = resolver.resolveConfirmed(new PurchaseCondition(
				draft.productType(), draft.usage(), draft.price(), draft.colors(), draft.sizes(),
				draft.requirements(), draft.attributes(), draft.merchant(), List.of(), draft.assumptions(),
				draft.confidence(), true));
		assertThat(confirmed.missingConditions()).isEmpty();
		assertThat(confirmed.colors().getFirst().requiresConfirmation()).isFalse();
	}

	/** 테스트 입력용 기존 호환 PurchaseCondition을 생성한다. */
	private PurchaseCondition condition(String color, String size) {
		return new PurchaseCondition(
				new PrioritizedText("구두", ConditionPriority.required),
				List.of(new PrioritizedShortText("출근용", ConditionPriority.preferred)),
				new PriceCondition(null, 100_000L, "KRW", ConditionPriority.required),
				List.of(new PrioritizedShortText(color, ConditionPriority.preferred)),
				List.of(new PrioritizedShortText(size, ConditionPriority.required)),
				List.of(), null, List.of(), List.of(), 0.98, true);
	}
}
