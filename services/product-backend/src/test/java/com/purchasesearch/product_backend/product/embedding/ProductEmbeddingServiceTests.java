package com.purchasesearch.product_backend.product.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** ProductEmbeddingServiceTests는 질문 embedding 정상 응답과 전문 검색 fallback을 검증한다. */
class ProductEmbeddingServiceTests {

	/** provider의 1024차원 질문 embedding을 pgvector 검색 metadata로 변환하는지 검증한다. */
	@Test
	void createsQueryEmbeddingWhenProviderSucceeds() {
		ProductEmbeddingService service = new ProductEmbeddingService(
				new FixtureEmbeddingProvider(false), new JdbcTemplate());

		var embedding = service.embedQuery("면접용 구두");

		assertThat(embedding).hasValueSatisfying(value -> {
			assertThat(value.provider()).isEqualTo("fixture");
			assertThat(value.model()).isEqualTo("semantic");
			assertThat(value.modelVersion()).isEqualTo("test");
			assertThat(value.vectorLiteral()).startsWith("[1.0,0.0").endsWith("]");
		});
	}

	/** provider가 실패하면 예외를 노출하지 않고 전문 검색용 빈 결과를 반환하는지 검증한다. */
	@Test
	void fallsBackWhenQueryEmbeddingProviderFails() {
		ProductEmbeddingService service = new ProductEmbeddingService(
				new FixtureEmbeddingProvider(true), new JdbcTemplate());

		assertThat(service.embedQuery("면접용 구두")).isEmpty();
	}

	/** FixtureEmbeddingProvider는 고정 vector 또는 실패를 재현하는 test double이다. */
	private static class FixtureEmbeddingProvider implements EmbeddingProvider {

		private final boolean fail;

		/** @param fail embed 호출을 실패시킬지 여부 */
		FixtureEmbeddingProvider(boolean fail) {
			this.fail = fail;
		}

		/** @return 항상 true */
		@Override
		public boolean enabled() {
			return true;
		}

		/** @return fixture */
		@Override
		public String provider() {
			return "fixture";
		}

		/** @return semantic */
		@Override
		public String model() {
			return "semantic";
		}

		/** @return test */
		@Override
		public String modelVersion() {
			return "test";
		}

		/** 고정 단위 vector를 반환하거나 설정에 따라 provider 실패를 발생시킨다. */
		@Override
		public List<float[]> embed(List<String> inputs) {
			if (fail) {
				throw new EmbeddingProviderException("fixture failure");
			}
			float[] vector = new float[1024];
			vector[0] = 1.0f;
			return inputs.stream().map(input -> vector.clone()).toList();
		}
	}
}
