package com.purchaseresearch.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.purchaseresearch.backend.dto.ProductBatchRequest;
import com.purchaseresearch.backend.dto.ProductBatchResponse;
import com.purchaseresearch.backend.service.ProductIngestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductIngestService productIngestService;

	@PostMapping("/batch")
	public ProductBatchResponse ingestBatch(@Valid @RequestBody ProductBatchRequest request) {
		return productIngestService.ingest(request);
	}
}
