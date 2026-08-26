package com.purchasesearch.product_backend.product.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.product.dto.ProductComparisonRequest;
import com.purchasesearch.product_backend.product.dto.ProductComparisonResponse;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse.EvidenceView;
import com.purchasesearch.product_backend.product.service.ProductDetailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** ProductDetailController는 MCP와 Web용 상품 상세/근거/비교 REST API를 제공한다. */
@Validated
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Product Research", description = "상품 상세 근거와 후보 비교")
public class ProductDetailController {

	private final ProductDetailService productDetailService;

	/** @param productDetailService 상품 사실과 비교 use case */
	public ProductDetailController(ProductDetailService productDetailService) {
		this.productDetailService = productDetailService;
	}

	/** @param productId 판매처 상품 ID @return 최신 상품 상세와 근거 */
	@GetMapping("/products/{productId}")
	@Operation(summary = "상품 상세와 최신성 조회")
	public ProductDetailResponse getProduct(@PathVariable long productId) {
		return productDetailService.getProduct(productId);
	}

	/** @param productId 판매처 상품 ID @return 공개 근거 목록 */
	@GetMapping("/products/{productId}/evidence")
	@Operation(summary = "상품 사실의 공개 근거 조회")
	public List<EvidenceView> getEvidence(@PathVariable long productId) {
		return productDetailService.getEvidence(productId);
	}

	/** @param request 비교 상품 ID 2개 이상 5개 이하 @return 주요 사실 비교 */
	@PostMapping(path = "/product-comparisons", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "여러 상품 후보 비교")
	public ProductComparisonResponse compare(@Valid @RequestBody ProductComparisonRequest request) {
		return productDetailService.compare(request.productIds());
	}
}
