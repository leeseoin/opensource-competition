package com.purchasesearch.product_backend.product.dto;

import java.util.List;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;

/**
 * ProductCandidateResponse는 사용자 질문에 연결된 DB 상품 후보와 검색 결과 범위를 반환한다.
 *
 * @param question 사용자가 입력한 원본 구매 질문
 * @param query 실제 PostgreSQL 조회에 사용한 검색어
 * @param totalCount 검색 조건과 일치하는 전체 상품 수
 * @param hasNext 현재 후보 뒤에 추가 상품이 있는지 여부
 * @param candidates 최신 가격과 재고 및 출처를 포함한 상품 후보
 */
public record ProductCandidateResponse(
		String question,
		String query,
		long totalCount,
		boolean hasNext,
		List<ProductSummary> candidates) {
}
