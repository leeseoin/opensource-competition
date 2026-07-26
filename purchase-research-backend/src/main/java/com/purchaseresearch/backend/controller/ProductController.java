package com.purchaseresearch.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.purchaseresearch.backend.dto.ProductBatchRequest;
import com.purchaseresearch.backend.dto.ProductBatchResponse;
import com.purchaseresearch.backend.dto.ProductSearchResponse;
import com.purchaseresearch.backend.service.CrawlTriggerService;
import com.purchaseresearch.backend.service.ProductIngestService;
import com.purchaseresearch.backend.service.ProductQueryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductIngestService productIngestService;
	private final ProductQueryService productQueryService;
	private final CrawlTriggerService crawlTriggerService;

	@PostMapping("/batch")
	public ProductBatchResponse ingestBatch(@Valid @RequestBody ProductBatchRequest request) {
		return productIngestService.ingest(request);
	}

	@GetMapping("/search")
	public ProductSearchResponse search(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer maxPrice
	) {
		return productQueryService.search(keyword, maxPrice);
	}

	@PostMapping("/crawl-trigger")
	public ProductBatchResponse triggerCrawl(
			@RequestParam String keyword,
			@RequestParam(defaultValue = "abcmart") String site,
			@RequestParam(defaultValue = "30") int maxItems
	) {
		return crawlTriggerService.triggerAndIngest(site, keyword, maxItems);
	}
}
