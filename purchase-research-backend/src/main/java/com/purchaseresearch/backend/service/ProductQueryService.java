package com.purchaseresearch.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.purchaseresearch.backend.domain.Product;
import com.purchaseresearch.backend.dto.ProductSearchResponse;
import com.purchaseresearch.backend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * §3.3 조회/추천 파이프라인의 "Spring Boot API: 조회 조건 기반 DB SELECT" 부분.
 * 임계값(10건) 판단과 크롤링 트리거 호출은 CLI(Python) 쪽에서 한다 — 이 서비스는
 * 순수 조회만 담당한다 (§12 Open Question #16: 오케스트레이션을 어디에 둘지 관련,
 * 이번 구현에서는 CLI 쪽에 둔 것으로 결정).
 */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

	private final ProductRepository productRepository;

	public ProductSearchResponse search(String keyword, Integer maxPrice) {
		List<Product> products = productRepository.search(blankToNull(keyword), maxPrice);
		List<ProductSearchResponse.ProductSummary> summaries = products.stream()
				.map(p -> new ProductSearchResponse.ProductSummary(
						p.getTitle(), p.getBrand(), p.getPrice(), p.getDiscountPercent(), p.getImageUrl(), p.getLink()))
				.toList();
		return new ProductSearchResponse(summaries.size(), summaries);
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}
}
