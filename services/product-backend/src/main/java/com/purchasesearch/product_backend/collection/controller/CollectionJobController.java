package com.purchasesearch.product_backend.collection.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionJobListResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse.ErrorResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionJobNotFoundException;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.exception.DuplicateCollectionTaskException;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionTaskException;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionTaskPublisher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CollectionJobController는 RabbitMQ 수집 job의 전체 진행률과 결과 집계를 조회한다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/collection-jobs")
@Tag(name = "Collection Jobs", description = "수집 job 진행 상태와 상품 및 검증 결과 조회")
public class CollectionJobController {

	private final CollectionJobService collectionJobService;
	private final CollectionTaskPublisher collectionTaskPublisher;

	/**
	 * job 상태 조회와 재실행 발행 서비스를 HTTP API에 연결한다.
	 *
	 * @param collectionJobService 수집 job 상태 서비스
	 * @param collectionTaskPublisher 재실행 작업 발행 서비스
	 */
	public CollectionJobController(
			CollectionJobService collectionJobService, CollectionTaskPublisher collectionTaskPublisher) {
		this.collectionJobService = collectionJobService;
		this.collectionTaskPublisher = collectionTaskPublisher;
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
	 * 판매처/상태로 거르거나 전체 job을 최신 요청순으로 성공률/상품 수와 함께 조회한다.
	 *
	 * @param merchant 선택 판매처
	 * @param status 선택 job 상태
	 * @param page 0부터 시작하는 페이지 번호
	 * @param size 페이지당 최대 job 수
	 * @return 요청 이력 화면이 쓸 job 목록
	 */
	@GetMapping
	@Operation(
			summary = "수집 요청 이력 목록 조회",
			description = "판매처/상태로 거르거나 전체 job을 최신 요청순으로 페이지네이션해 성공률과 상품 수를 함께 반환합니다.")
	public CollectionJobListResponse list(
			@RequestParam(required = false)
			@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
			@Size(max = 64)
			String merchant,
			@RequestParam(required = false)
			@Pattern(regexp = "^(QUEUED|RUNNING|PROCESSING|COMPLETED|PARTIAL|FAILED)$")
			String status,
			@RequestParam(defaultValue = "0")
			@Min(0)
			int page,
			@RequestParam(defaultValue = "20")
			@Min(1)
			@Max(100)
			int size) {
		return collectionJobService.list(merchant, status, page, size);
	}

	/**
	 * 실패하거나 일부만 성공한 job을 등록 당시와 같은 조건으로 새 job에 다시 발행한다.
	 *
	 * @param jobId 재실행할 job 식별자
	 * @return 새로 발행된 job의 jobId와 페이지 범위
	 * @throws CollectionJobNotFoundException jobId가 존재하지 않는 경우
	 * @throws InvalidCollectionTaskException job이 FAILED 또는 PARTIAL 상태가 아닌 경우
	 */
	@PostMapping("/{jobId}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(
			summary = "수집 job 재실행",
			description = "FAILED 또는 PARTIAL 상태인 job을 등록 당시의 merchant/query/페이지범위/조건 그대로 새 job에 다시 발행합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "202", description = "재실행 job 접수 성공"),
		@ApiResponse(
				responseCode = "400",
				description = "FAILED 또는 PARTIAL 상태가 아닌 job",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "404",
				description = "존재하지 않는 jobId",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "409",
				description = "동일 조건의 다른 작업이 이미 진행 중",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "503",
				description = "RabbitMQ 발행 실패",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public BulkCollectionTaskResponse retry(@PathVariable String jobId) {
		return collectionTaskPublisher.retryJob(jobId);
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

	/**
	 * 재실행할 수 없는 상태의 job 요청을 400 응답으로 변환한다.
	 *
	 * @param exception 재실행 불가 상태 예외
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(InvalidCollectionTaskException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidTask(InvalidCollectionTaskException exception) {
		return new ErrorResponse("INVALID_COLLECTION_TASK", exception.getMessage());
	}

	/**
	 * 이미 진행 중인 동일 조건 작업과의 중복을 409 응답으로 변환한다.
	 *
	 * @param exception 중복 거부 예외
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(DuplicateCollectionTaskException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleDuplicateTask(DuplicateCollectionTaskException exception) {
		return new ErrorResponse("DUPLICATE_COLLECTION_TASK", exception.getMessage());
	}

	/**
	 * RabbitMQ 발행 또는 broker 확인 실패를 재시도 가능한 503 응답으로 변환한다.
	 *
	 * @param exception 작업 발행 실패
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(CollectionTaskPublishException.class)
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public ErrorResponse handlePublishFailure(CollectionTaskPublishException exception) {
		return new ErrorResponse("COLLECTION_QUEUE_UNAVAILABLE", exception.getMessage());
	}
}
