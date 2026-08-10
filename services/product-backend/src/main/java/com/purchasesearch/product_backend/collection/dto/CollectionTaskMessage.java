package com.purchasesearch.product_backend.collection.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CollectionTaskMessage는 Spring Boot가 Go Collector Worker에 발행하는 Queue v1 계약이다.
 *
 * @param schemaVersion Queue 계약 버전
 * @param taskId 실행 단위 식별자
 * @param jobId 사용자 요청 단위 식별자
 * @param merchant 판매처 식별자
 * @param operation 작업 종류
 * @param priority 작업 우선순위
 * @param attempt 현재 시도 번호
 * @param maxAttempts 최초 실행을 포함한 최대 시도 횟수
 * @param requestedAt 수집 요청 시각
 * @param idempotencyKey 동일 조건 중복 판별용 키
 * @param payload 판매처 검색 조건
 */
public record CollectionTaskMessage(
		String schemaVersion,
		String taskId,
		String jobId,
		String merchant,
		String operation,
		int priority,
		int attempt,
		int maxAttempts,
		OffsetDateTime requestedAt,
		String idempotencyKey,
		SearchPayload payload) {

	/**
	 * SearchPayload는 Go Worker가 검색 요청으로 변환할 정규화된 조건이다.
	 *
	 * @param query 공백을 정리한 검색어
	 * @param page 검색 페이지
	 * @param limit 수집 상품 최대 개수
	 * @param locale 검색 지역 형식
	 * @param currency 통화 코드
	 * @param filters 정규화된 검색 필터
	 */
	public record SearchPayload(
			String query,
			int page,
			int limit,
			String locale,
			String currency,
			SearchFilters filters) {
	}

	/**
	 * SearchFilters는 Queue에 항상 완전한 형태로 기록하는 공통 검색 필터다.
	 *
	 * @param priceMin 최소 가격
	 * @param priceMax 최대 가격
	 * @param categories 카테고리 조건
	 * @param sizes 사이즈 조건
	 * @param colors 색상 조건
	 * @param inStockOnly 재고 보유 상품만 요청할지 여부
	 * @param attributes 판매처 확장 검색 속성
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SearchFilters(
			Integer priceMin,
			Integer priceMax,
			List<String> categories,
			List<String> sizes,
			List<String> colors,
			boolean inStockOnly,
			Map<String, Object> attributes) {
	}
}
