package com.purchasesearch.product_backend.collection.dto;

import com.purchasesearch.product_backend.collection.service.CollectorResultStoreService.StoreReport;

/**
 * CollectionResultStoreResponse는 한 CollectorResult 요청의 상품 처리 수와 추가한
 * 이력 개수를 HTTP 호출자에게 반환한다.
 *
 * @param productCount 처리한 상품 수
 * @param snapshotCount 추가한 가격과 재고 snapshot 수
 * @param optionCount 추가한 옵션 수
 * @param evidenceCount 추가한 출처 근거 수
 * @param verificationCount 추가한 JSON/HTML 검증 결과 수
 */
public record CollectionResultStoreResponse(
		int productCount,
		int snapshotCount,
		int optionCount,
		int evidenceCount,
		int verificationCount) {

	/**
	 * 저장 서비스의 내부 결과를 HTTP 응답 DTO로 변환한다.
	 *
	 * @param report 저장 서비스 결과
	 * @return 외부 계층에 노출할 저장 개수
	 */
	public static CollectionResultStoreResponse from(StoreReport report) {
		return new CollectionResultStoreResponse(
				report.productCount(),
				report.snapshotCount(),
				report.optionCount(),
				report.evidenceCount(),
				report.verificationCount());
	}

	/**
	 * ErrorResponse는 저장할 수 없는 CollectorResult의 오류 코드와 설명을 반환한다.
	 *
	 * @param code 기계가 판별할 오류 코드
	 * @param message 사람이 확인할 오류 설명
	 */
	public record ErrorResponse(String code, String message) {
	}
}
