package com.purchasesearch.product_backend.product.dto;

import java.util.List;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;

/**
 * ProductCandidateResponse는 사용자 질문에 연결된 DB 상품 후보와 검색 결과 범위를 반환한다.
 *
 * @param question 사용자가 입력한 원본 구매 질문
 * @param query 실제 PostgreSQL 조회에 사용한 검색어
 * @param totalCount 검색 조건과 일치하는 전체 상품 수
 * @param hasNext 현재 후보 뒤에 추가 상품이 있는지 여부
 * @param candidates 최신 가격과 재고 및 출처를 포함한 상품 후보
 * @param assessments 후보 ID별 옵션 일치, 완화 조건과 근거 부족 판정
 */
public record ProductCandidateResponse(
		String question,
		String query,
		long totalCount,
		boolean hasNext,
		List<ProductSummary> candidates,
		List<CandidateAssessment> assessments) {

	/** MatchStatus는 수집된 최신 옵션과 사용자 조건의 일치 상태를 표현한다. */
	public enum MatchStatus {
		MATCH,
		MISMATCH,
		UNKNOWN
	}

	/**
	 * CandidateAssessment는 후보별 조건 판정과 설명을 상품 사실과 분리해 반환한다.
	 *
	 * @param candidateId 판매처 상품 내부 식별자
	 * @param keywordScore exact/FTS/trigram 중 가장 높은 0부터 1 사이 점수
	 * @param semanticScore 같은 model vector의 cosine 유사도 또는 미사용 시 null
	 * @param wikiConceptScore 검토된 Wiki 점수 또는 운영 미연결 시 null
	 * @param freshnessScore 수집 경과 시간 구간의 0부터 1 사이 점수
	 * @param evidenceCompletenessScore 최신 가격/재고/출처 필드 완전성 점수
	 * @param sizeStatus 최신 재고 옵션의 사이즈 판정
	 * @param colorStatus 최신 재고 옵션의 색상 판정
	 * @param matchReasons 수집 사실으로 확인한 일치 이유
	 * @param relaxedConditions 후보 제외에 사용하지 않은 선호 조건
	 * @param unknownConditions 현재 수집 data로 판정할 수 없는 조건
	 */
	public record CandidateAssessment(
			long candidateId,
			double keywordScore,
			Double semanticScore,
			Double wikiConceptScore,
			double freshnessScore,
			double evidenceCompletenessScore,
			MatchStatus sizeStatus,
			MatchStatus colorStatus,
			List<String> matchReasons,
			List<String> relaxedConditions,
			List<String> unknownConditions) {
	}
}
