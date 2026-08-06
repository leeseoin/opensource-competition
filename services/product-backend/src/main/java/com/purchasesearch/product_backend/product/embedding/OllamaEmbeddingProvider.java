package com.purchasesearch.product_backend.product.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/** OllamaEmbeddingProvider는 로컬 Ollama BGE-M3 API를 선택적으로 호출한다. */
@Component
@ConditionalOnProperty(prefix = "purchase.embedding", name = "provider", havingValue = "ollama")
public class OllamaEmbeddingProvider implements EmbeddingProvider {

	private static final int DIMENSIONS = 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final URI endpoint;
	private final String model;
	private final String modelVersion;
	private final Duration timeout;

	/**
	 * 로컬 Ollama endpoint와 고정 model 정보를 구성한다.
	 *
	 * @param objectMapper JSON codec
	 * @param baseUrl Ollama server base URL
	 * @param model BGE-M3 model tag
	 * @param modelVersion weight와 양자화 구성을 구분할 version
	 * @param timeoutMs 요청 timeout millisecond
	 */
	public OllamaEmbeddingProvider(
			ObjectMapper objectMapper,
			@Value("${purchase.embedding.base-url:http://127.0.0.1:11434}") String baseUrl,
			@Value("${purchase.embedding.model:bge-m3:567m}") String model,
			@Value("${purchase.embedding.model-version:ollama-bge-m3-567m-q4_0}") String modelVersion,
			@Value("${purchase.embedding.timeout-ms:3000}") long timeoutMs) {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
				objectMapper, baseUrl, model, modelVersion, timeoutMs);
	}

	/** 테스트에서 HTTP client를 대체할 수 있도록 실제 설정 값을 보존한다. */
	OllamaEmbeddingProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String baseUrl,
			String model,
			String modelVersion,
			long timeoutMs) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/embed");
		this.model = model;
		this.modelVersion = modelVersion;
		this.timeout = Duration.ofMillis(timeoutMs);
	}

	/** @return Ollama 설정 시 true */
	@Override
	public boolean enabled() {
		return true;
	}

	/** @return ollama */
	@Override
	public String provider() {
		return "ollama";
	}

	/** @return 설정한 BGE-M3 model tag */
	@Override
	public String model() {
		return model;
	}

	/** @return 설정한 weight와 양자화 version */
	@Override
	public String modelVersion() {
		return modelVersion;
	}

	/** 로컬 Ollama embed API를 호출하고 개수/차원/유한값을 검증한다. */
	@Override
	public List<float[]> embed(List<String> inputs) {
		if (inputs.isEmpty()) {
			return List.of();
		}
		try {
			String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
					"model", model,
					"input", inputs));
			HttpRequest request = HttpRequest.newBuilder(endpoint)
					.timeout(timeout)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new EmbeddingProviderException("Ollama embedding 응답 상태가 정상이 아닙니다.");
			}
			ArrayNode embeddings = (ArrayNode) objectMapper.readTree(response.body()).get("embeddings");
			if (embeddings == null || embeddings.size() != inputs.size()) {
				throw new EmbeddingProviderException("Ollama embedding 응답 개수가 입력과 다릅니다.");
			}
			List<float[]> vectors = new ArrayList<>(embeddings.size());
			for (var embedding : embeddings) {
				if (!embedding.isArray() || embedding.size() != DIMENSIONS) {
					throw new EmbeddingProviderException("Ollama embedding 차원이 1024가 아닙니다.");
				}
				float[] vector = new float[DIMENSIONS];
				for (int index = 0; index < DIMENSIONS; index++) {
					vector[index] = embedding.get(index).floatValue();
					if (!Float.isFinite(vector[index])) {
						throw new EmbeddingProviderException("Ollama embedding에 유한하지 않은 값이 있습니다.");
					}
				}
				vectors.add(vector);
			}
			return vectors;
		} catch (EmbeddingProviderException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new EmbeddingProviderException("Ollama embedding 요청에 실패했습니다.", exception);
		}
	}
}
