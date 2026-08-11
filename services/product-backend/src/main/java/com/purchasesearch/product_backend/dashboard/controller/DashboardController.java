package com.purchasesearch.product_backend.dashboard.controller;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.ErrorResponse;
import com.purchasesearch.product_backend.dashboard.exception.InvalidDashboardWindowException;
import com.purchasesearch.product_backend.dashboard.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * DashboardController는 지정한 시간 창의 수집/검증 현황을 판매처와 상태별로 집계해 제공한다.
 */
@RestController
@RequestMapping("/internal/v1/dashboard")
@Tag(name = "Dashboard", description = "수집 job/작업, 판매처 상품, 검증 현황 집계 조회")
public class DashboardController {

	private final DashboardService dashboardService;

	/**
	 * 대시보드 집계 service를 HTTP API에 연결한다.
	 *
	 * @param dashboardService 집계 service
	 */
	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	/**
	 * 지정하거나 기본값(최근 24시간)인 시간 창의 수집/검증 현황을 집계한다.
	 *
	 * @param since 창 시작 시각이며 없으면 until - 24시간
	 * @param until 창 끝 시각이며 없으면 현재 시각
	 * @return 판매처와 상태별로 집계한 대시보드 요약
	 */
	@GetMapping("/summary")
	@Operation(
			summary = "대시보드 요약 집계 조회",
			description = "since/until로 지정하거나 기본값(최근 24시간)인 시간 창의 job/작업 상태, 판매처별 수집 상품 수, "
					+ "검증 상태와 일치율을 집계한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "집계 조회 성공"),
		@ApiResponse(
				responseCode = "400",
				description = "since가 until보다 늦거나 같음",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public DashboardSummaryResponse summary(
			@RequestParam(required = false) OffsetDateTime since,
			@RequestParam(required = false) OffsetDateTime until) {
		return dashboardService.summarize(since, until);
	}

	/**
	 * 잘못된 집계 시간 창을 400 응답으로 변환한다.
	 *
	 * @param exception 시간 창 검증 실패
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(InvalidDashboardWindowException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidWindow(InvalidDashboardWindowException exception) {
		return new ErrorResponse("INVALID_DASHBOARD_WINDOW", exception.getMessage());
	}
}
