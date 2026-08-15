package com.purchasesearch.product_backend.evidence.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.purchasesearch.product_backend.evidence.dto.BulkOfferVerificationRequest;
import com.purchasesearch.product_backend.evidence.dto.BulkVerificationBatchResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.service.BulkOfferVerificationRunner;
import com.purchasesearch.product_backend.evidence.service.BulkVerificationBatchTracker;
import com.purchasesearch.product_backend.evidence.service.OfferVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** OfferVerificationController는 선택 상품의 구매 직전 재검증 요청과 상태 조회 API를 제공한다. */
@Validated
@RestController
@RequestMapping("/internal/v1/offer-verifications")
@Tag(name = "Offer Verification", description = "선택 상품 가격과 재고 재검증")
public class OfferVerificationController {

	private final OfferVerificationService offerVerificationService;
	private final BulkVerificationBatchTracker batchTracker;
	private final BulkOfferVerificationRunner bulkRunner;

	/**
	 * @param offerVerificationService 우선 수집과 snapshot 비교 서비스
	 * @param batchTracker 대량 재검증 배치 진행 상태 기록소
	 * @param bulkRunner 대량 재검증 배경 처리기
	 */
	public OfferVerificationController(
			OfferVerificationService offerVerificationService,
			BulkVerificationBatchTracker batchTracker,
			BulkOfferVerificationRunner bulkRunner) {
		this.offerVerificationService = offerVerificationService;
		this.batchTracker = batchTracker;
		this.bulkRunner = bulkRunner;
	}

	/** @param productId 재검증할 판매처 상품 ID @return Queue에 접수된 재검증 요청 */
	@PostMapping("/products/{productId}")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "선택 상품 가격과 재고 재검증 요청")
	public OfferVerificationResponse request(@PathVariable long productId) {
		return offerVerificationService.request(productId);
	}

	/**
	 * 관리자 화면에서 여러 상품(최대 10,000개)을 골라 한 번에 재검증을 요청한다.
	 * 상품마다 Queue 발행에 broker confirm 대기가 있어 대량이면 처리에 수 분이 걸릴 수
	 * 있으므로, 배경 스레드에 맡기고 즉시 배치 ID를 반환한다. 진행 상태는
	 * {@link #getBulk}로 따로 조회한다.
	 *
	 * @param request 재검증할 판매처 상품 ID 목록
	 * @return 접수 직후의 배치 상태(PROCESSING)
	 */
	@PostMapping("/products/bulk")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "선택한 여러 상품 가격과 재고 일괄 재검증 요청 접수")
	public BulkVerificationBatchResponse requestBulk(@Valid @RequestBody BulkOfferVerificationRequest request) {
		UUID batchId = batchTracker.create(request.productIds().size());
		bulkRunner.runAsync(batchId, request.productIds());
		return batchTracker.get(batchId).orElseThrow();
	}

	/**
	 * @param batchId 조회할 대량 재검증 배치 ID
	 * @return 지금까지 처리된 상품 수와 결과
	 */
	@GetMapping("/products/bulk/{batchId}")
	@Operation(summary = "대량 재검증 배치 진행 상태 조회")
	public BulkVerificationBatchResponse getBulk(@PathVariable UUID batchId) {
		return batchTracker.get(batchId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배치를 찾을 수 없습니다."));
	}

	/** @param verificationId 재검증 요청 ID @return 진행 또는 최종 snapshot 비교 결과 */
	@GetMapping("/{verificationId}")
	@Operation(summary = "재검증 진행 상태와 변경 결과 조회")
	public OfferVerificationResponse get(@PathVariable UUID verificationId) {
		return offerVerificationService.get(verificationId);
	}
}
