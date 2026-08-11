package com.purchasesearch.product_backend.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ProductSearchResponse는 DB에 저장된 최신 판매처 상품을 화면과 MCP Server에 반환한다.
 *
 * @param totalCount 검색 조건과 일치하는 전체 판매처 상품 수
 * @param hasNext 현재 응답 뒤에 다음 상품이 있는지 여부
 * @param products 최신 offer snapshot이 연결된 상품 목록
 */
public record ProductSearchResponse(
		long totalCount,
		boolean hasNext,
		List<ProductSummary> products) {

	/**
	 * ProductSummary는 한 판매처 상품과 최신 가격, 재고, 옵션 및 근거를 표현한다.
	 *
	 * @param id 판매처 상품 내부 식별자
	 * @param merchant 판매처 식별자
	 * @param externalId 판매처 상품번호
	 * @param name 상품명
	 * @param brand 브랜드명
	 * @param categoryPath 카테고리 경로
	 * @param productUrl 공개 상품 URL
	 * @param imageUrls 공개 상품 이미지 URL 목록
	 * @param price 최신 가격
	 * @param shipping 최신 배송 정보
	 * @param stockStatus 최신 재고 상태
	 * @param rating 최신 평점
	 * @param reviewCount 최신 리뷰 수
	 * @param options 최신 옵션 목록
	 * @param source 최신 상품 사실의 공개 출처
	 */
	public record ProductSummary(
			long id,
			String merchant,
			String externalId,
			String name,
			String brand,
			List<String> categoryPath,
			String productUrl,
			List<String> imageUrls,
			MoneyView price,
			ShippingView shipping,
			String stockStatus,
			BigDecimal rating,
			Integer reviewCount,
			List<OptionView> options,
			SourceView source) {
	}

	/**
	 * MoneyView는 API에 노출하는 금액과 통화를 표현한다.
	 *
	 * @param amount 최소 통화 단위 기준 금액
	 * @param currency 3자리 통화 코드
	 */
	public record MoneyView(long amount, String currency) {
	}

	/**
	 * ShippingView는 최신 배송비와 배송 안내를 표현한다.
	 *
	 * @param fee 배송비
	 * @param summary 배송 안내 문구
	 */
	public record ShippingView(MoneyView fee, String summary) {
	}

	/**
	 * OptionView는 최신 offer snapshot에 포함된 옵션 가격과 재고를 표현한다.
	 *
	 * @param externalId 판매처 옵션 식별자
	 * @param label 옵션 표시명
	 * @param size 사이즈
	 * @param color 색상
	 * @param stockStatus 옵션 재고 상태
	 * @param price 옵션 가격
	 */
	public record OptionView(
			String externalId,
			String label,
			String size,
			String color,
			String stockStatus,
			MoneyView price) {
	}

	/**
	 * SourceView는 최신 상품 사실의 출처와 수집 시점을 표현한다.
	 *
	 * @param sourceUrl 공개 출처 URL
	 * @param collectedAt 수집 시각
	 * @param collectorVersion Collector 버전
	 */
	public record SourceView(
			String sourceUrl,
			OffsetDateTime collectedAt,
			String collectorVersion) {
	}
}
