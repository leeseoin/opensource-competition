package com.purchasesearch.product_backend.product.embedding;

import java.util.List;

/** EmbeddingProvider는 상품 검색 문서와 질문을 같은 차원의 vector로 변환하는 port다. */
public interface EmbeddingProvider {

	/** @return 실제 embedding 호출을 사용할 수 있으면 true */
	boolean enabled();

	/** @return embedding 제공자 식별자 */
	String provider();

	/** @return embedding model 식별자 */
	String model();

	/** @return 재생성 판단에 사용할 model version */
	String modelVersion();

	/**
	 * 입력 순서를 보존한 1024차원 embedding을 생성한다.
	 *
	 * @param inputs 상품 검색 문서 또는 사용자 질문
	 * @return 입력과 같은 개수의 embedding
	 * @throws EmbeddingProviderException timeout, 연결 실패 또는 잘못된 model 응답
	 */
	List<float[]> embed(List<String> inputs);
}
