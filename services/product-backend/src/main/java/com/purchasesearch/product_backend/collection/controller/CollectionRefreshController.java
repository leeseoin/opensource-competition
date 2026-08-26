package com.purchasesearch.product_backend.collection.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.service.CollectionRefreshService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** CollectionRefreshController는 MCP/Web의 조건부 최신 상품 수집 요청을 받는다. */
@Validated
@RestController
@RequestMapping("/internal/v1/collection-requests")
@Tag(name = "Collection Refresh", description = "DB 데이터 없음 또는 만료 시 수집 요청")
public class CollectionRefreshController {

	private final CollectionRefreshService collectionRefreshService;

	/** @param collectionRefreshService 최신성 판정과 Queue 발행 서비스 */
	public CollectionRefreshController(CollectionRefreshService collectionRefreshService) {
		this.collectionRefreshService = collectionRefreshService;
	}

	/** @param request 판매처/검색어/강제 여부 @return 데이터 상태와 수집 job */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "없거나 오래된 검색 데이터 수집 요청")
	public ResponseEntity<CollectionRefreshResponse> request(@Valid @RequestBody CollectionRefreshRequest request) {
		CollectionRefreshResponse response = collectionRefreshService.request(request);
		return ResponseEntity.status(response.collectionRequested() ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body(response);
	}
}
