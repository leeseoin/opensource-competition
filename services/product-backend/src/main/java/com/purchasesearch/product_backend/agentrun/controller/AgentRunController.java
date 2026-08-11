package com.purchasesearch.product_backend.agentrun.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.agentrun.dto.AgentRunResponse;
import com.purchasesearch.product_backend.agentrun.dto.StartAgentRunRequest;
import com.purchasesearch.product_backend.agentrun.dto.VerifyAgentRunRequest;
import com.purchasesearch.product_backend.agentrun.exception.AgentRunException;
import com.purchasesearch.product_backend.agentrun.service.AgentRunService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** AgentRunController는 복구 가능한 구매 조사 실행과 상태 전진 REST API를 제공한다. */
@RestController
@RequestMapping("/internal/v1/agent-runs")
@Tag(name = "Agent Runs", description = "DB 검색, 조건부 수집과 구매 직전 재검증 실행")
public class AgentRunController {

	private final AgentRunService service;

	/** @param service 구매 조사 상태 전이 서비스 */
	public AgentRunController(AgentRunService service) {
		this.service = service;
	}

	/** @param request 확정 조사 세션과 판매처 범위 @return 시작 또는 기존 실행 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "상태 기반 구매 조사 시작")
	public ResponseEntity<AgentRunResponse> start(@Valid @RequestBody StartAgentRunRequest request) {
		AgentRunResponse response = service.start(request);
		HttpStatus status = response.status().name().equals("COLLECTING") ? HttpStatus.ACCEPTED : HttpStatus.OK;
		return ResponseEntity.status(status).body(response);
	}

	/** @param runId 실행 ID @return 현재 상태와 사건 및 연결 작업 */
	@GetMapping("/{runId}")
	@Operation(summary = "구매 조사 실행 상태 조회")
	public AgentRunResponse get(@PathVariable UUID runId) {
		return service.get(runId);
	}

	/** @param runId 실행 ID @return 수집 또는 재검증 확인 뒤 상태 */
	@PostMapping("/{runId}/advance")
	@Operation(summary = "구매 조사 실행 한 단계 진행")
	public AgentRunResponse advance(@PathVariable UUID runId) {
		return service.advance(runId);
	}

	/** @param runId 실행 ID @param request 선택 상품 @return 재검증 상태 */
	@PostMapping(path = "/{runId}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "선택 상품 구매 직전 재검증")
	public ResponseEntity<AgentRunResponse> verify(
			@PathVariable UUID runId,
			@Valid @RequestBody VerifyAgentRunRequest request) {
		return ResponseEntity.accepted().body(service.verify(runId, request.productId()));
	}

	/** Agent Run 상태 오류를 안전한 HTTP 응답으로 변환한다. */
	@ExceptionHandler(AgentRunException.class)
	public ResponseEntity<AgentRunErrorResponse> handle(AgentRunException exception) {
		return ResponseEntity.status(exception.getStatus())
				.body(new AgentRunErrorResponse(exception.getCode(), exception.getMessage()));
	}

	/** @param code 안정적인 오류 코드 @param message 사용자에게 공개 가능한 설명 */
	public record AgentRunErrorResponse(String code, String message) {
	}
}
