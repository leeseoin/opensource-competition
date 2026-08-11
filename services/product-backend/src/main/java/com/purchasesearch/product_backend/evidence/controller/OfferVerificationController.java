package com.purchasesearch.product_backend.evidence.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.service.OfferVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OfferVerificationController는 선택 상품의 구매 직전 재검증 요청과 상태 조회 API를 제공한다. */
@RestController
@RequestMapping("/internal/v1/offer-verifications")
@Tag(name = "Offer Verification", description = "선택 상품 가격과 재고 재검증")
public class OfferVerificationController {

	private final OfferVerificationService offerVerificationService;

	/** @param offerVerificationService 우선 수집과 snapshot 비교 서비스 */
	public OfferVerificationController(OfferVerificationService offerVerificationService) {
		this.offerVerificationService = offerVerificationService;
	}

	/** @param productId 재검증할 판매처 상품 ID @return Queue에 접수된 재검증 요청 */
	@PostMapping("/products/{productId}")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "선택 상품 가격과 재고 재검증 요청")
	public OfferVerificationResponse request(@PathVariable long productId) {
		return offerVerificationService.request(productId);
	}

	/** @param verificationId 재검증 요청 ID @return 진행 또는 최종 snapshot 비교 결과 */
	@GetMapping("/{verificationId}")
	@Operation(summary = "재검증 진행 상태와 변경 결과 조회")
	public OfferVerificationResponse get(@PathVariable UUID verificationId) {
		return offerVerificationService.get(verificationId);
	}
}
