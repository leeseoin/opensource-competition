package com.purchasesearch.product_backend.collection.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.purchasesearch.product_backend.collection.exception.UnstorableCollectorResultException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CollectorResult는 Go Collector가 반환하는 v1 검색 결과를 Java에서 검증하고 저장하기
 * 위한 공통 계약이다.
 *
 * @param requestId 요청과 작업을 추적하는 식별자
 * @param operation Collector 작업 종류
 * @param status 수집 완료 상태
 * @param merchant 판매처 식별자
 * @param query 검색 작업에 사용한 원본 검색어
 * @param filters 검색 작업에 적용한 필터
 * @param totalCount 판매처 검색 기준 전체 상품 수
 * @param hasNext 다음 페이지 존재 여부
 * @param collectedAt 결과 수집 완료 시각
 * @param collectorVersion Collector 구현 버전
 * @param products 공통 상품 목록
 * @param warnings 수집 경고 목록
 * @param errors 수집 오류 목록
 */
public record CollectorResult(
		@NotBlank
		@Size(max = 128)
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]*$")
		String requestId,
		@NotBlank
		@Pattern(regexp = "^(search|product|reviews)$")
		String operation,
		@NotBlank
		@Pattern(regexp = "^(success|partial|blocked|unsupported|temporarily_unavailable)$")
		String status,
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
		String merchant,
		@Size(max = 200)
		String query,
		@Valid
		SearchFilters filters,
		@Min(0)
		Integer totalCount,
		Boolean hasNext,
		@NotNull
		OffsetDateTime collectedAt,
		@NotBlank
		@Size(max = 100)
		String collectorVersion,
		@NotNull
		@Size(max = 50)
		List<@Valid Product> products,
		@NotNull
		List<@Valid Issue> warnings,
		@NotNull
		List<@Valid Issue> errors) {

	private static final Set<String> STORABLE_STATUSES = Set.of("success", "partial");

	/**
	 * 저장 가능한 정상 또는 부분 성공 결과인지 확인한다.
	 *
	 * @throws UnstorableCollectorResultException 차단, 미지원 또는 일시 오류 결과인 경우
	 */
	public void validateStorable() {
		if (!STORABLE_STATUSES.contains(status)) {
			throw new UnstorableCollectorResultException(
					"success 또는 partial CollectorResult만 저장할 수 있습니다.");
		}
		if ("search".equals(operation) && (query == null || query.isBlank() || filters == null)) {
			throw new UnstorableCollectorResultException(
					"search CollectorResult에는 query와 filters가 필요합니다.");
		}
	}

	/**
	 * SearchFilters는 Collector가 실제 검색에 적용한 조건을 저장 가능한 공통 구조로 표현한다.
	 *
	 * @param priceMin 최소 가격
	 * @param priceMax 최대 가격
	 * @param categories 카테고리 조건
	 * @param sizes 사이즈 조건
	 * @param colors 색상 조건
	 * @param inStockOnly 재고 보유 상품만 요청했는지 여부
	 * @param attributes 판매처 확장 검색 속성
	 */
	public record SearchFilters(
			@Min(0)
			Integer priceMin,
			@Min(0)
			Integer priceMax,
			List<@NotBlank @Size(max = 100) String> categories,
			List<@NotBlank @Size(max = 100) String> sizes,
			List<@NotBlank @Size(max = 100) String> colors,
			boolean inStockOnly,
			Map<@NotBlank @Size(max = 100) String, Object> attributes) {

		/**
		 * nullable 선택값을 제거하고 JSONB 저장에 사용할 map으로 변환한다.
		 *
		 * @return Collector가 적용한 검색 필터 map
		 */
		public Map<String, Object> toMap() {
			Map<String, Object> values = new LinkedHashMap<>();
			if (priceMin != null) {
				values.put("priceMin", priceMin);
			}
			if (priceMax != null) {
				values.put("priceMax", priceMax);
			}
			putNonEmpty(values, "categories", categories);
			putNonEmpty(values, "sizes", sizes);
			putNonEmpty(values, "colors", colors);
			values.put("inStockOnly", inStockOnly);
			if (attributes != null && !attributes.isEmpty()) {
				values.put("attributes", attributes);
			}
			return values;
		}

		/**
		 * 비어 있지 않은 문자열 목록만 JSONB map에 추가한다.
		 *
		 * @param target 필터를 모으는 map
		 * @param key 저장할 필터 이름
		 * @param values 선택 문자열 목록
		 */
		private void putNonEmpty(Map<String, Object> target, String key, List<String> values) {
			if (values != null && !values.isEmpty()) {
				target.put(key, values);
			}
		}
	}

	/**
	 * Product는 판매처 원본을 공통 구조로 변환한 상품과 최신 사실을 표현한다.
	 *
	 * @param externalId 판매처 상품 식별자
	 * @param name 상품명
	 * @param brand 브랜드명
	 * @param categoryPath 상위에서 하위 순서의 카테고리 경로
	 * @param productUrl 공개 상품 URL
	 * @param imageUrls 공개 상품 이미지 URL 목록
	 * @param price 수집 시점 가격
	 * @param shipping 수집 시점 배송 정보
	 * @param stockStatus 공통 재고 상태
	 * @param rating 공개 평점
	 * @param reviewCount 공개 리뷰 수
	 * @param options 수집된 옵션 목록
	 * @param measurements 수집된 실측값
	 * @param reviews 작성자 식별정보를 제외한 리뷰 목록
	 * @param provenance 상품 사실의 출처
	 */
	public record Product(
			@NotBlank
			@Size(max = 200)
			String externalId,
			@NotBlank
			@Size(max = 500)
			String name,
			@Size(max = 2000)
			String brand,
			@NotNull
			@Size(max = 20)
			List<@NotBlank @Size(max = 200) String> categoryPath,
			@NotBlank
			@Size(max = 2048)
			String productUrl,
			@NotNull
			@Size(max = 20)
			List<@NotBlank @Size(max = 2048) String> imageUrls,
			@Valid
			Money price,
			@NotNull
			@Valid
			Shipping shipping,
			@NotBlank
			@Pattern(regexp = "^(available|low_stock|out_of_stock|unknown)$")
			String stockStatus,
			@DecimalMin("0.0")
			@DecimalMax("5.0")
			BigDecimal rating,
			@Min(0)
			Integer reviewCount,
			@NotNull
			@Size(max = 500)
			List<@Valid Option> options,
			@NotNull
			@Size(max = 100)
			Map<@NotBlank @Size(max = 100) String, @Valid MeasurementValue> measurements,
			@NotNull
			@Size(max = 1000)
			List<@Valid Review> reviews,
			@NotNull
			@Valid
			Provenance provenance) {
	}

	/**
	 * Money는 금액과 ISO 4217 통화 코드를 표현한다.
	 *
	 * @param amount 최소 통화 단위 기준 금액
	 * @param currency 3자리 대문자 통화 코드
	 */
	public record Money(
			@Min(0)
			long amount,
			@NotBlank
			@Pattern(regexp = "^[A-Z]{3}$")
			String currency) {
	}

	/**
	 * Shipping은 검색 단계에서 확인한 배송비, 안내 문구 및 출처를 표현한다.
	 *
	 * @param fee 배송비
	 * @param summary 배송 안내 문구
	 * @param provenance 배송 정보 출처
	 */
	public record Shipping(
			@Valid
			Money fee,
			@Size(max = 2000)
			String summary,
			@NotNull
			@Valid
			Provenance provenance) {
	}

	/**
	 * Option은 수집 시점의 상품 옵션과 가격 및 재고를 표현한다.
	 *
	 * @param externalId 판매처 옵션 식별자
	 * @param label 사용자에게 표시할 옵션명
	 * @param size 사이즈
	 * @param color 색상
	 * @param stockStatus 옵션 재고 상태
	 * @param price 옵션 가격
	 * @param provenance 옵션 사실 출처
	 */
	public record Option(
			@Size(max = 200)
			String externalId,
			@NotBlank
			@Size(max = 300)
			String label,
			@Size(max = 2000)
			String size,
			@Size(max = 2000)
			String color,
			@NotBlank
			@Pattern(regexp = "^(available|low_stock|out_of_stock|unknown)$")
			String stockStatus,
			@Valid
			Money price,
			@NotNull
			@Valid
			Provenance provenance) {
	}

	/**
	 * MeasurementValue는 상품 실측값, 단위 및 공개 출처를 표현한다.
	 *
	 * @param value 실측 숫자 값
	 * @param unit 단위
	 * @param provenance 실측값 출처
	 */
	public record MeasurementValue(
			@NotNull
			BigDecimal value,
			@NotBlank
			@Size(max = 20)
			String unit,
			@NotNull
			@Valid
			Provenance provenance) {
	}

	/**
	 * Review는 작성자 식별정보를 제외한 공개 리뷰 최소 필드를 표현한다.
	 *
	 * @param externalId 판매처 리뷰 식별자
	 * @param rating 평점
	 * @param text 리뷰 본문
	 * @param hasImage 리뷰 사진 존재 여부
	 * @param purchasedOption 구매 옵션 표시값
	 * @param createdAt 리뷰 작성 시각
	 * @param provenance 리뷰 출처
	 */
	public record Review(
			@Size(max = 200)
			String externalId,
			@DecimalMin("0.0")
			@DecimalMax("5.0")
			BigDecimal rating,
			@Size(max = 10000)
			String text,
			boolean hasImage,
			@Size(max = 2000)
			String purchasedOption,
			OffsetDateTime createdAt,
			@NotNull
			@Valid
			Provenance provenance) {
	}

	/**
	 * Provenance는 상품 사실을 확인한 공개 URL, 수집 시각 및 Collector 버전을 표현한다.
	 *
	 * @param sourceUrl 사실을 확인한 공개 URL
	 * @param collectedAt 사실 수집 시각
	 * @param collectorVersion 사실을 만든 Collector 버전
	 */
	public record Provenance(
			@NotBlank
			@Size(max = 2048)
			String sourceUrl,
			@NotNull
			OffsetDateTime collectedAt,
			@NotBlank
			@Size(max = 100)
			String collectorVersion) {
	}

	/**
	 * Issue는 수집 중 발생한 경고 또는 오류를 표현한다.
	 *
	 * @param code 기계가 판별할 수 있는 오류 코드
	 * @param message 사람이 확인할 오류 설명
	 * @param retryable 재시도 가능 여부
	 * @param sourceUrl 오류가 발생한 공개 URL
	 */
	public record Issue(
			@NotBlank
			@Size(max = 100)
			@Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
			String code,
			@NotBlank
			@Size(max = 1000)
			String message,
			boolean retryable,
			@Size(max = 2048)
			String sourceUrl) {
	}
}
