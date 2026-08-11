package com.purchasesearch.product_backend.research.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.purchasesearch.product_backend.research.dto.ConfirmResearchSessionRequest;
import com.purchasesearch.product_backend.research.dto.ResearchSessionRequest;
import com.purchasesearch.product_backend.research.dto.ResearchSessionResponse;
import com.purchasesearch.product_backend.research.service.ResearchSessionService;
import com.purchasesearch.product_backend.research.exception.ResearchSessionException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** ResearchSessionController는 AI 구매 조건 초안과 사용자 확인 API를 제공한다. */
@RestController
@RequestMapping("/internal/v1/research-sessions")
@Tag(name = "Research Sessions", description = "AI 구매 조건 구조화와 사용자 확인")
public class ResearchSessionController {

	private final ResearchSessionService service;

	/**
	 * 조사 세션 서비스를 연결한다.
	 *
	 * @param service 조사 세션 서비스
	 */
	public ResearchSessionController(ResearchSessionService service) {
		this.service = service;
	}

	/**
	 * AI가 구조화한 구매 조건을 확인 전 상태로 저장한다.
	 *
	 * @param request AI 구조화 결과
	 * @return DRAFT 조사 세션
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "구매 조건 초안 저장")
	public ResearchSessionResponse create(@Valid @RequestBody ResearchSessionRequest request) {
		return service.create(request);
	}

	/**
	 * 사용자가 확인한 조건을 확정하고 상품 후보를 검색한다.
	 *
	 * @param sessionId 조사 세션 식별자
	 * @param request 확정 조건
	 * @return CONFIRMED 상태와 상품 후보
	 */
	@PostMapping(path = "/{sessionId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "구매 조건 확인 후 상품 검색")
	public ResearchSessionResponse confirm(
			@PathVariable UUID sessionId,
			@Valid @RequestBody ConfirmResearchSessionRequest request) {
		return service.confirm(sessionId, request);
	}

	/**
	 * 사용자가 확인한 조사 세션으로만 상품 후보를 검색한다.
	 *
	 * @param sessionId 조사 세션 식별자
	 * @return 상품 후보를 포함한 조사 세션
	 */
	@PostMapping(path = "/{sessionId}/search")
	@Operation(summary = "확정 구매 조건 상품 검색")
	public ResearchSessionResponse search(@PathVariable UUID sessionId) {
		return service.search(sessionId);
	}

	/**
	 * 조사 세션 상태 오류를 예측 가능한 409 응답으로 변환한다.
	 *
	 * @param exception 확인 전 검색 또는 존재하지 않는 세션 오류
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(ResearchSessionException.class)
	public ResponseEntity<ResearchSessionErrorResponse> handleResearchSessionException(
			ResearchSessionException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ResearchSessionErrorResponse("RESEARCH_SESSION_NOT_READY", exception.getMessage()));
	}

	/**
	 * ResearchSessionErrorResponse는 조사 세션 상태 오류 계약이다.
	 *
	 * @param code 안정적인 오류 코드
	 * @param message 사용자에게 보여줄 오류 설명
	 */
	public record ResearchSessionErrorResponse(String code, String message) {
	}
}
