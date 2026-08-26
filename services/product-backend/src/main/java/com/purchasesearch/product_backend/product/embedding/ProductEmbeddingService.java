package com.purchasesearch.product_backend.product.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.product.embedding.ProductSearchDocumentsChanged.ProductSearchDocument;

/** ProductEmbeddingService는 content hash 기반 상품 embedding 저장과 질문 embedding을 담당한다. */
@Service
public class ProductEmbeddingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductEmbeddingService.class);

	private final EmbeddingProvider provider;
	private final JdbcTemplate jdbcTemplate;

	/**
	 * 선택적 embedding provider와 PostgreSQL 저장소를 연결한다.
	 *
	 * @param provider embedding provider 또는 비활성 fallback
	 * @param jdbcTemplate pgvector 저장용 JDBC adapter
	 */
	public ProductEmbeddingService(EmbeddingProvider provider, JdbcTemplate jdbcTemplate) {
		this.provider = provider;
		this.jdbcTemplate = jdbcTemplate;
	}

	/** provider가 활성화된 경우 변경된 상품 검색 문서만 batch embedding으로 갱신한다. */
	@Transactional
	public void refresh(List<ProductSearchDocument> documents) {
		if (!provider.enabled() || documents.isEmpty()) {
			return;
		}
		try {
			List<PendingDocument> pending = documents.stream()
					.map(document -> new PendingDocument(document, hash(document.content())))
					.filter(this::requiresRefresh)
					.toList();
			if (pending.isEmpty()) {
				return;
			}
			List<float[]> vectors = provider.embed(pending.stream()
					.map(item -> item.document().content())
					.toList());
			if (vectors.size() != pending.size()) {
				throw new EmbeddingProviderException("embedding 응답 개수가 검색 문서 개수와 다릅니다.");
			}
			vectors.forEach(this::validateVector);
			for (int index = 0; index < pending.size(); index++) {
				upsert(pending.get(index), vectors.get(index));
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("상품 embedding 갱신을 건너뛰고 전문 검색 fallback을 유지합니다: {}",
					exception.getMessage());
		}
	}

	/** provider가 활성화되고 정상 응답하면 vector 검색용 질문 embedding을 반환한다. */
	public Optional<QueryEmbedding> embedQuery(String query) {
		if (!provider.enabled()) {
			return Optional.empty();
		}
		try {
			List<float[]> vectors = provider.embed(List.of(query));
			if (vectors.size() != 1) {
				throw new EmbeddingProviderException("질문 embedding 응답 개수가 1이 아닙니다.");
			}
			return Optional.of(new QueryEmbedding(
					provider.provider(), provider.model(), provider.modelVersion(), toVectorLiteral(vectors.getFirst())));
		} catch (EmbeddingProviderException exception) {
			LOGGER.warn("질문 embedding을 건너뛰고 전문 검색 fallback을 사용합니다: {}", exception.getMessage());
			return Optional.empty();
		}
	}

	/** 같은 provider/model/version/content hash가 이미 있으면 재생성을 생략한다. */
	private boolean requiresRefresh(PendingDocument pending) {
		List<String> hashes = jdbcTemplate.queryForList("""
				SELECT content_hash
				FROM product_embeddings
				WHERE merchant_product_id = ?
				  AND provider = ?
				  AND model = ?
				  AND model_version = ?
				""", String.class,
				pending.document().merchantProductId(),
				provider.provider(),
				provider.model(),
				provider.modelVersion());
		return hashes.isEmpty() || !hashes.getFirst().equals(pending.contentHash());
	}

	/** 검증한 embedding과 provenance metadata를 판매처 상품 단위로 upsert한다. */
	private void upsert(PendingDocument pending, float[] vector) {
		jdbcTemplate.update("""
				INSERT INTO product_embeddings
				    (merchant_product_id, provider, model, model_version, content_hash, embedding)
				VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
				ON CONFLICT (merchant_product_id, provider, model, model_version)
				DO UPDATE SET
				    content_hash = EXCLUDED.content_hash,
				    embedding = EXCLUDED.embedding,
				    generated_at = CURRENT_TIMESTAMP
				""",
				pending.document().merchantProductId(),
				provider.provider(),
				provider.model(),
				provider.modelVersion(),
				pending.contentHash(),
				toVectorLiteral(vector));
	}

	/** 저장 전에 vector 차원과 모든 값의 유한성을 검증한다. */
	private void validateVector(float[] vector) {
		if (vector.length != 1024) {
			throw new EmbeddingProviderException("저장할 embedding 차원이 1024가 아닙니다.");
		}
		for (float value : vector) {
			if (!Float.isFinite(value)) {
				throw new EmbeddingProviderException("embedding에 유한하지 않은 값이 있습니다.");
			}
		}
	}

	/** 검색 문서 변경 여부를 비교할 SHA-256 hash를 생성한다. */
	private String hash(String content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(content.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

	/** pgvector가 받을 수 있는 대괄호 vector literal을 생성한다. */
	private String toVectorLiteral(float[] vector) {
		List<String> values = new ArrayList<>(vector.length);
		for (float value : vector) {
			if (!Float.isFinite(value)) {
				throw new EmbeddingProviderException("embedding에 유한하지 않은 값이 있습니다.");
			}
			values.add(Float.toString(value));
		}
		return "[" + String.join(",", values) + "]";
	}

	/** PendingDocument는 embedding 전 검색 문서와 content hash를 함께 보존한다. */
	private record PendingDocument(ProductSearchDocument document, String contentHash) {
	}

	/**
	 * QueryEmbedding은 vector 검색에 필요한 provider/model/version과 vector literal이다.
	 *
	 * @param provider 제공자 식별자
	 * @param model model 식별자
	 * @param modelVersion model version
	 * @param vectorLiteral pgvector 입력 문자열
	 */
	public record QueryEmbedding(
			String provider,
			String model,
			String modelVersion,
			String vectorLiteral) {
	}
}
