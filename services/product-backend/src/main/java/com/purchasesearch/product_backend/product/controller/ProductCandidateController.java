package com.purchasesearch.product_backend.product.controller;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.product.dto.ProductCandidateRequest;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.service.ProductCandidateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * ProductCandidateController는 Next.js와 MCP Server가 재사용할 사용자 질문 기반 상품 후보 API를 제공한다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/product-candidates")
@Tag(name = "Product Candidates", description = "사용자 질문에 연결할 PostgreSQL 상품 후보 조회")
public class ProductCandidateController {

	private final ProductCandidateService productCandidateService;

	/**
	 * 상품 후보 조회 use case를 연결한다.
	 *
	 * @param productCandidateService 질문 문맥을 포함한 상품 후보 서비스
	 */
	public ProductCandidateController(ProductCandidateService productCandidateService) {
		this.productCandidateService = productCandidateService;
	}

	/**
	 * 사용자 질문과 명시적 검색어를 받아 최신 DB 상품 후보를 반환한다.
	 *
	 * @param request 원본 질문과 DB 검색 조건
	 * @return 가격, 재고, 판매처와 출처를 포함한 후보
	 */
	@PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "사용자 질문용 상품 후보 조회",
			description = "자연어 원본 질문과 명시적 검색어를 분리해 받고 PostgreSQL의 최신 상품 후보를 반환한다.")
	public ProductCandidateResponse search(@Valid @RequestBody ProductCandidateRequest request) {
		return productCandidateService.findCandidates(request);
	}
}
