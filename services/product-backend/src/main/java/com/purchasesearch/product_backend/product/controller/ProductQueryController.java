package com.purchasesearch.product_backend.product.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;
import com.purchasesearch.product_backend.product.service.ProductQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ProductQueryController는 MCP Server와 Next.js server가 사용할 내부 상품 검색 API를
 * 제공한다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/products")
@Tag(name = "Products", description = "PostgreSQL에 저장된 최신 판매처 상품 조회")
public class ProductQueryController {

	private final ProductQueryService productQueryService;

	/**
	 * 상품 검색 use case를 연결한다.
	 *
	 * @param productQueryService 상품 검색 서비스
	 */
	public ProductQueryController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	/**
	 * 저장된 최신 판매처 상품을 판매처와 상품명 또는 브랜드 조건으로 검색한다.
	 *
	 * @param merchant 선택 판매처
	 * @param query 선택 상품 검색어
	 * @param limit 최대 반환 상품 수(1~10,000). 관리자 화면의 대량 재검증 대상 조회처럼
	 *     큰 값이 필요한 경우를 감안해 상한을 10,000으로 둔다.
	 * @param sort "oldest"면 마지막 수집이 오래된 순, 그 외(기본 "latest")면 최근 수집 순.
	 *     관리자 화면이 재검증이 시급한 상품을 고를 때 "oldest"를 쓴다.
	 * @param staleHours 지정하면 마지막 수집이 이 시간(시) 이전인 상품만 반환한다. 예를 들어
	 *     5를 주면 "5시간 이상 지난 상품만" 조회된다.
	 * @return 전체 개수와 상품 목록
	 */
	@GetMapping
	@Operation(
			summary = "저장된 상품 검색",
			description = "판매처, 상품명 또는 브랜드 검색어와 최대 반환 개수, 정렬 방향, 최신성 경과 시간으로 상품을 조회한다.")
	public ProductSearchResponse search(
			@RequestParam(required = false)
			@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
			@Size(max = 64)
			String merchant,
			@RequestParam(required = false)
			@Size(max = 200)
			String query,
			@RequestParam(defaultValue = "20")
			@Min(1)
			@Max(10_000)
			int limit,
			@RequestParam(defaultValue = "latest")
			@Pattern(regexp = "^(latest|oldest)$")
			String sort,
			@RequestParam(required = false)
			@Min(0)
			Integer staleHours) {
		return productQueryService.search(merchant, query, limit, sort, staleHours);
	}
}
