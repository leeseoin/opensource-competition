package com.purchasesearch.product_backend.product.embedding;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** ProductEmbeddingRefreshListener는 상품 transaction commit 뒤 선택적 embedding을 갱신한다. */
@Component
public class ProductEmbeddingRefreshListener {

	private final ProductEmbeddingService embeddingService;

	/**
	 * 상품 embedding application service를 연결한다.
	 *
	 * @param embeddingService content hash 기반 embedding 서비스
	 */
	public ProductEmbeddingRefreshListener(ProductEmbeddingService embeddingService) {
		this.embeddingService = embeddingService;
	}

	/** commit된 상품 사실만 외부 model에 전달하고 실패 시 저장 transaction과 분리한다. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void refresh(ProductSearchDocumentsChanged event) {
		embeddingService.refresh(event.documents());
	}
}
