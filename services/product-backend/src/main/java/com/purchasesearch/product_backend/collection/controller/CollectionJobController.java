package com.purchasesearch.product_backend.collection.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse.ErrorResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionJobNotFoundException;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * CollectionJobController는 RabbitMQ 수집 job의 전체 진행률과 결과 집계를 조회한다.
 */
@RestController
@RequestMapping("/internal/v1/collection-jobs")
@Tag(name = "Collection Jobs", description = "수집 job 진행 상태와 상품 및 검증 결과 조회")
public class CollectionJobController {

	private final CollectionJobService collectionJobService;

	/**
	 * job 상태 조회 서비스를 HTTP API에 연결한다.
	 *
	 * @param collectionJobService 수집 job 상태 서비스
	 */
	public CollectionJobController(CollectionJobService collectionJobService) {
		this.collectionJobService = collectionJobService;
	}

	/**
	 * jobId로 전체 작업 상태, 처리 상품 수와 verificationSummary를 조회한다.
	 *
	 * @param jobId 작업 등록 API에서 받은 job 식별자
	 * @return 페이지별 상태를 포함한 현재 job 집계
	 * @throws CollectionJobNotFoundException jobId가 존재하지 않는 경우
	 */
	@GetMapping("/{jobId}")
	@Operation(
			summary = "수집 job 상태 조회",
			description = "작업 등록 응답의 jobId를 사용해 완료 페이지, 상품 수와 JSON/HTML 검증 결과를 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "job 상태 조회 성공"),
		@ApiResponse(
				responseCode = "404",
				description = "존재하지 않는 jobId",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public CollectionJobResponse get(@PathVariable String jobId) {
		return collectionJobService.get(jobId);
	}

	/**
	 * 존재하지 않는 job 조회를 404 응답으로 변환한다.
	 *
	 * @param exception 찾지 못한 job 예외
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(CollectionJobNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(CollectionJobNotFoundException exception) {
		return new ErrorResponse("COLLECTION_JOB_NOT_FOUND", exception.getMessage());
	}
}
