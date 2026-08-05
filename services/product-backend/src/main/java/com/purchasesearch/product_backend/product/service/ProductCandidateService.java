package com.purchasesearch.product_backend.product.service;

import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.product.dto.ProductCandidateRequest;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;

/**
 * ProductCandidateService는 사용자 질문 문맥을 보존하면서 기존 DB 상품 검색 결과를 후보 계약으로 변환한다.
 */
@Service
public class ProductCandidateService {

	private static final int DEFAULT_LIMIT = 3;

	private final ProductQueryService productQueryService;

	/**
	 * 기존 상품 조회 서비스를 후보 조회 use case에 연결한다.
	 *
	 * @param productQueryService PostgreSQL 최신 상품 조회 서비스
	 */
	public ProductCandidateService(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	/**
	 * 명시적 검색어로 DB 상품을 조회하고 원본 질문과 함께 후보를 반환한다.
	 *
	 * @param request 원본 질문, 검색어와 후보 제한
	 * @return 최신 상품 후보와 검색 결과 범위
	 */
	public ProductCandidateResponse findCandidates(ProductCandidateRequest request) {
		int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
		String question = request.question().trim();
		String query = request.query().trim();
		String merchant = request.merchant() == null ? null : request.merchant().trim();
		ProductSearchResponse result = productQueryService.search(merchant, query, limit);

		return new ProductCandidateResponse(
				question,
				query,
				result.totalCount(),
				result.hasNext(),
				result.products());
	}
}
