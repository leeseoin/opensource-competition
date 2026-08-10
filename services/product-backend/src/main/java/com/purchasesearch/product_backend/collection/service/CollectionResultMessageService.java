package com.purchasesearch.product_backend.collection.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectionResultEnvelope;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionResultMessageException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

/**
 * CollectionResultMessageService는 Queue 결과 JSON을 계약 검증하고 기존 PostgreSQL 저장 흐름에 연결한다.
 */
@Service
public class CollectionResultMessageService {

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final CollectorResultStoreService collectorResultStoreService;
	private final CollectionJobService collectionJobService;

	/**
	 * Queue JSON 처리에 필요한 JSON mapper, validator와 저장 서비스를 연결한다.
	 *
	 * @param objectMapper Spring Boot 공통 JSON mapper
	 * @param validator Jakarta Bean Validation validator
	 * @param collectorResultStoreService 검증된 Collector 결과 저장 서비스
	 * @param collectionJobService 추적 중인 작업의 결과 상태 저장 서비스
	 */
	public CollectionResultMessageService(
			ObjectMapper objectMapper,
			Validator validator,
			CollectorResultStoreService collectorResultStoreService,
			CollectionJobService collectionJobService) {
		this.objectMapper = objectMapper;
		this.validator = validator;
		this.collectorResultStoreService = collectorResultStoreService;
		this.collectionJobService = collectionJobService;
	}

	/**
	 * Queue 상태 또는 결과 한 건을 해석하고 시작 상태를 갱신하거나 최종 결과를 저장한다.
	 *
	 * @param body RabbitMQ message body
	 * @return 상품 저장 또는 정상 작업 실패 처리 결과
	 * @throws InvalidCollectionResultMessageException JSON 해석이나 상태 의미 검증에 실패한 경우
	 * @throws ConstraintViolationException Queue 또는 CollectorResult 필드 제약을 위반한 경우
	 */
	@Transactional
	public ProcessingOutcome process(byte[] body) {
		CollectionResultEnvelope envelope = decode(body);
		Set<ConstraintViolation<CollectionResultEnvelope>> violations = validator.validate(envelope);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
		envelope.validateSemantics();

		if ("running".equals(envelope.status())) {
			collectionJobService.recordRunning(envelope);
			return ProcessingOutcome.TASK_RUNNING;
		}
		if ("failed".equals(envelope.status())) {
			collectionJobService.recordFailed(envelope);
			return ProcessingOutcome.TASK_FAILED;
		}
		CollectorResultStoreService.StoreReport report = collectorResultStoreService.store(envelope.collectorResult());
		collectionJobService.recordCompleted(envelope, report.productCount());
		return ProcessingOutcome.STORED;
	}

	/**
	 * RabbitMQ message body 하나만 CollectionResultEnvelope로 변환한다.
	 *
	 * @param body JSON byte 배열
	 * @return 해석된 Queue 결과
	 * @throws InvalidCollectionResultMessageException JSON이 계약 DTO로 해석되지 않는 경우
	 */
	private CollectionResultEnvelope decode(byte[] body) {
		try {
			return objectMapper.readValue(body, CollectionResultEnvelope.class);
		} catch (Exception exception) {
			throw new InvalidCollectionResultMessageException(
					"CollectionResult Queue JSON을 해석할 수 없습니다.", exception);
		}
	}

	/**
	 * ProcessingOutcome은 Queue 결과가 저장됐는지 정상 실패로 종료됐는지 구분한다.
	 */
	public enum ProcessingOutcome {
		TASK_RUNNING,
		STORED,
		TASK_FAILED
	}
}
