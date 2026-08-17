package com.purchasesearch.product_backend.collection.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse.ErrorResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.exception.DuplicateCollectionTaskException;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionTaskException;
import com.purchasesearch.product_backend.collection.service.CollectionTaskPublisher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * CollectionTaskController는 검색 수집 요청을 검증하고 RabbitMQ 작업 Queue에 접수한다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/collection-tasks")
@Tag(name = "Collection Tasks", description = "판매처 검색 수집 작업을 RabbitMQ에 등록")
public class CollectionTaskController {

	private final CollectionTaskPublisher collectionTaskPublisher;

	/**
	 * 수집 요청 API에 RabbitMQ 작업 발행 서비스를 연결한다.
	 *
	 * @param collectionTaskPublisher 작업 발행 서비스
	 */
	public CollectionTaskController(CollectionTaskPublisher collectionTaskPublisher) {
		this.collectionTaskPublisher = collectionTaskPublisher;
	}

	/**
	 * 판매처 검색 조건을 Queue에 등록하고 추적할 taskId와 jobId를 반환한다.
	 *
	 * @param request 판매처와 검색 조건
	 * @return RabbitMQ에 접수된 작업 정보
	 * @throws InvalidCollectionTaskException 의미상 지원하지 않는 검색 조건인 경우
	 * @throws CollectionTaskPublishException RabbitMQ가 작업 발행을 확인하지 못한 경우
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(
			summary = "판매처 검색 수집 작업 등록",
			description = "검색 조건을 CollectionTask v1으로 변환해 RabbitMQ에 등록하고 202 응답을 반환합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "202", description = "작업 Queue 접수 성공"),
		@ApiResponse(
				responseCode = "400",
				description = "입력 또는 현재 지원 범위 위반",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "409",
				description = "동일 조건 작업이 이미 QUEUED 또는 RUNNING으로 진행 중",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "503",
				description = "RabbitMQ 발행 실패",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public CollectionTaskResponse publish(@Valid @RequestBody CollectionTaskRequest request) {
		return collectionTaskPublisher.publish(request);
	}

	/**
	 * 같은 판매처와 검색어의 연속 페이지를 개별 Queue 작업으로 등록한다.
	 *
	 * @param request 시작 페이지, 페이지 수와 공통 검색 조건
	 * @return 공통 jobId와 실제 발행된 페이지 범위
	 * @throws InvalidCollectionTaskException 페이지 범위나 검색 조건이 지원 범위를 벗어난 경우
	 * @throws CollectionTaskPublishException RabbitMQ가 일부 또는 전체 작업 발행을 확인하지 못한 경우
	 */
	@PostMapping("/pages")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(
			summary = "여러 페이지 검색 수집 작업 등록",
			description = "연속된 검색 페이지를 같은 jobId의 CollectionTask v1 작업으로 나눠 RabbitMQ에 등록합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "202", description = "모든 페이지 작업 Queue 접수 성공"),
		@ApiResponse(
				responseCode = "400",
				description = "입력 또는 최대 200페이지 범위 위반",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "409",
				description = "요청한 페이지가 모두 이미 진행 중인 동일 조건 작업과 중복",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(
				responseCode = "503",
				description = "RabbitMQ 발행 실패이며 일부 앞 페이지는 이미 접수됐을 수 있음",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public BulkCollectionTaskResponse publishPages(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
				required = true,
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					examples = @ExampleObject(
						name = "ABC마트 1페이지 소량 수집",
						summary = "손으로 바로 실행할 수 있는 최소 요청",
						value = """
							{
							  "merchant": "abcmart",
							  "query": "구두",
							  "startPage": 1,
							  "pageCount": 1,
							  "limit": 3
							}
							""")))
			@Valid @RequestBody BulkCollectionTaskRequest request) {
		return collectionTaskPublisher.publishPages(request);
	}

	/**
	 * 의미상 잘못된 검색 조건을 400 응답으로 변환한다.
	 *
	 * @param exception 검색 작업 검증 실패
	 * @return 오류 코드와 설명
	 */
	@ExceptionHandler(InvalidCollectionTaskException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidTask(InvalidCollectionTaskException exception) {
		return new ErrorResponse("INVALID_COLLECTION_TASK", exception.getMessage());
	}

	/**
	 * 이미 진행 중인 동일 조건 작업과의 중복을 409 응답으로 변환한다.
	 * {@link CollectionTaskPublishException}의 하위 타입이지만 Spring이 더 구체적인 타입을
	 * 우선 매칭하므로 503이 아닌 이 handler가 적용된다.
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
