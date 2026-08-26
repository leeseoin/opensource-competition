package com.purchasesearch.product_backend.product.embedding;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** DisabledEmbeddingProvider는 model 미설정 환경에서 전문 검색 fallback만 사용하게 한다. */
@Component
@ConditionalOnProperty(
		prefix = "purchase.embedding",
		name = "provider",
		havingValue = "disabled",
		matchIfMissing = true)
public class DisabledEmbeddingProvider implements EmbeddingProvider {

	/** @return 항상 false */
	@Override
	public boolean enabled() {
		return false;
	}

	/** @return 비활성 제공자 식별자 */
	@Override
	public String provider() {
		return "disabled";
	}

	/** @return 비활성 model 식별자 */
	@Override
	public String model() {
		return "disabled";
	}

	/** @return 비활성 model version */
	@Override
	public String modelVersion() {
		return "disabled";
	}

	/** @throws EmbeddingProviderException 비활성 provider를 직접 호출한 경우 */
	@Override
	public List<float[]> embed(List<String> inputs) {
		throw new EmbeddingProviderException("embedding provider가 비활성화되어 있습니다.");
	}
}
