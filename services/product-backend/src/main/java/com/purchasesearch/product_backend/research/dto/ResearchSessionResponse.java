package com.purchasesearch.product_backend.research.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.research.entity.ResearchSession;
import com.purchasesearch.product_backend.research.entity.ResearchSessionStatus;

/**
 * ResearchSessionResponse는 구매 조건 상태와 확정 후 상품 후보를 반환한다.
 *
 * @param sessionId 조사 세션 식별자
 * @param question 사용자 원문 질문
 * @param runtime AI 실행 환경
 * @param pluginId 적용한 Plugin 식별자
 * @param status 조건 확인 상태
 * @param conditions 구조화된 구매 조건
 * @param confirmedAt 사용자 확인 시각
 * @param result 확정 후 검색한 상품 후보
 */
public record ResearchSessionResponse(
		UUID sessionId,
		String question,
		String runtime,
		String pluginId,
		ResearchSessionStatus status,
		PurchaseCondition conditions,
		OffsetDateTime confirmedAt,
		ProductCandidateResponse result) {

	/**
	 * ResearchSession entity와 선택 상품 결과를 API 응답으로 변환한다.
	 *
	 * @param session 저장된 조사 세션
	 * @param result 확정 후 상품 후보 또는 null
	 * @return 조사 세션 응답
	 */
	public static ResearchSessionResponse from(ResearchSession session, ProductCandidateResponse result) {
		return new ResearchSessionResponse(
				session.getId(), session.getQuestion(), session.getRuntime(), session.getPluginId(),
				session.getStatus(), session.getConditions(), session.getConfirmedAt(), result);
	}
}
