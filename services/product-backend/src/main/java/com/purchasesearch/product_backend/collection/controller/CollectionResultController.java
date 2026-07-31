package com.purchasesearch.product_backend.collection.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.purchasesearch.product_backend.collection.dto.CollectionResultStoreResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionResultStoreResponse.ErrorResponse;
import com.purchasesearch.product_backend.collection.dto.CollectorResult;
import com.purchasesearch.product_backend.collection.exception.UnstorableCollectorResultException;
import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;

/**
 * CollectionResultController는 수동 검증과 향후 Queue consumer가 공유할 수 있도록
 * Collector 결과를 Product Backend의 저장 서비스에 연결한다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/collection-results")
@Tag(name = "Collection Results", description = "Go Collector 결과를 검증하고 PostgreSQL에 저장")
public class CollectionResultController {

	private final CollectorResultStoreService collectorResultStoreService;

	/**
	 * 수집 결과 저장 API에 transaction 저장 서비스를 연결한다.
	 *
	 * @param collectorResultStoreService Collector 결과 저장 서비스
	 */
	public CollectionResultController(CollectorResultStoreService collectorResultStoreService) {
		this.collectorResultStoreService = collectorResultStoreService;
	}

	/**
	 * Go Collector의 성공 또는 부분 성공 JSON을 검증하고 PostgreSQL에 저장한다.
	 *
	 * @param result Go Collector 공통 결과
	 * @return 저장한 상품, snapshot, 옵션과 근거 개수
	 * @throws ConstraintViolationException Contract 제약을 만족하지 못한 경우
	 * @throws UnstorableCollectorResultException 저장할 수 없는 Collector 상태인 경우
	 */
	@PostMapping
	@Operation(
			summary = "Collector 결과 수동 적재",
			description = "Go Collector가 반환한 success 또는 partial JSON을 저장하고 처리 개수를 반환한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장 성공"),
		@ApiResponse(
				responseCode = "400",
				description = "Contract 위반 또는 저장할 수 없는 Collector 상태",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public CollectionResultStoreResponse store(@Valid @RequestBody CollectorResult result) {
		return CollectionResultStoreResponse.from(collectorResultStoreService.store(result));
	}

	/**
	 * 저장할 수 없는 상태나 서비스 단계의 Contract 위반을 400 응답으로 변환한다.
	 *
	 * @param exception 입력 검증 실패
	 * @return 클라이언트가 원인을 확인할 수 있는 오류 코드와 메시지
	 */
	@ExceptionHandler({ConstraintViolationException.class, UnstorableCollectorResultException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidCollectorResult(RuntimeException exception) {
		return new ErrorResponse("INVALID_COLLECTOR_RESULT", exception.getMessage());
	}
}
