package com.purchasesearch.product_backend.product.embedding;

import java.util.List;

/**
 * ProductSearchDocumentsChanged는 Collector transaction 완료 뒤 갱신할 상품 검색 문서를
 * 전달한다.
 *
 * @param documents 판매처 상품 ID와 현재 공개 사실 기반 검색 문서
 */
public record ProductSearchDocumentsChanged(List<ProductSearchDocument> documents) {

	/**
	 * ProductSearchDocument는 embedding 대상 판매처 상품과 content를 표현한다.
	 *
	 * @param merchantProductId 판매처 상품 내부 식별자
	 * @param content 상품명/브랜드/category/수집 검색어 기반 문서
	 */
	public record ProductSearchDocument(long merchantProductId, String content) {
	}
}
