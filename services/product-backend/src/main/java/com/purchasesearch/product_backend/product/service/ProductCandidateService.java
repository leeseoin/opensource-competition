package com.purchasesearch.product_backend.product.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.product.dto.ProductCandidateRequest;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;

/**
 * ProductCandidateService는 사용자 질문 문맥을 보존하면서 기존 DB 상품 검색 결과를 후보 계약으로 변환한다.
 */
@Service
public class ProductCandidateService {

	private static final int DEFAULT_LIMIT = 3;
	private static final Map<String, String> COLOR_ALIASES = Map.ofEntries(
			Map.entry("검정", "black"),
			Map.entry("검은색", "black"),
			Map.entry("흰색", "white"),
			Map.entry("하양", "white"),
			Map.entry("베이지", "beige"),
			Map.entry("갈색", "brown"),
			Map.entry("회색", "gray"),
			Map.entry("파랑", "blue"),
			Map.entry("남색", "navy"),
			Map.entry("빨강", "red"));

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

	/**
	 * 사용자가 확인한 구매 조건을 최신 가격, 옵션과 재고가 일치하는 DB 후보로 변환한다.
	 *
	 * @param question 사용자 원문 질문
	 * @param conditions 사용자 확인을 마친 구매 조건
	 * @return 공통 조건을 적용한 상품 후보 최대 3개
	 */
	public ProductCandidateResponse findCandidates(String question, PurchaseCondition conditions) {
		String sizesCsv = toCsv(conditions.sizes(), true);
		String colorsCsv = toCsv(conditions.colors(), false);
		ProductSearchResponse result = productQueryService.searchCandidates(
				conditions.merchant(),
				conditions.productType(),
				conditions.price().min(),
				conditions.price().max(),
				conditions.price().currency(),
				sizesCsv,
				colorsCsv,
				DEFAULT_LIMIT);
		return new ProductCandidateResponse(
				question.trim(),
				conditions.productType().trim(),
				result.totalCount(),
				result.hasNext(),
				result.products());
	}

	/** 사용자 조건 목록을 SQL의 정확한 목록 비교에 사용할 쉼표 경계 문자열로 변환한다. */
	private String toCsv(List<String> values, boolean size) {
		if (values.isEmpty()) {
			return null;
		}
		String joined = values.stream()
				.map(value -> normalizeValue(value, size))
				.filter(value -> !value.isBlank())
				.distinct()
				.reduce((left, right) -> left + "," + right)
				.orElse("");
		return joined.isBlank() ? null : "," + joined + ",";
	}

	/** 화면 표기 사이즈와 한국어 색상 별칭을 Collector 옵션 값에 맞게 정규화한다. */
	private String normalizeValue(String value, boolean size) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (size && normalized.matches("[0-9]+(?:\\.[0-9]+)?mm")) {
			normalized = normalized.substring(0, normalized.length() - 2);
		}
		return size ? normalized : COLOR_ALIASES.getOrDefault(normalized, normalized);
	}
}
