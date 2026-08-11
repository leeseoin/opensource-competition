package com.purchasesearch.product_backend.knowledge.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * WikiPageDocument는 Git에서 검토한 구매 도메인 Wiki page를 PostgreSQL index 적재 계약으로 표현한다.
 *
 * @param pageId 안정적인 page 식별자
 * @param version page version
 * @param status DRAFT/PUBLISHED/SUPERSEDED 상태
 * @param title 사람이 확인할 page 제목
 * @param reviewedBy PUBLISHED page 검토자
 * @param reviewedAt PUBLISHED page 검토 시각
 * @param supersedes 대체한 이전 page version 식별자
 * @param claims 출처와 confidence를 가진 의미 관계 목록
 */
public record WikiPageDocument(
		String pageId,
		int version,
		String status,
		String title,
		String reviewedBy,
		OffsetDateTime reviewedAt,
		String supersedes,
		List<WikiClaimDocument> claims) {

	/**
	 * WikiClaimDocument는 검색어 확장에 사용할 검토된 개념 관계와 provenance를 표현한다.
	 *
	 * @param claimId page 안에서 안정적인 claim 식별자
	 * @param subject 관계의 시작 개념
	 * @param relation narrower/synonym/merchant_category 관계
	 * @param object 검색에 추가할 대상 개념
	 * @param derived 판매처 원문이 아닌 파생 관계 여부
	 * @param confidence 관계 신뢰도
	 * @param sourceIds 근거 source 식별자
	 * @param evidencePointers source 안의 근거 위치
	 */
	public record WikiClaimDocument(
			String claimId,
			String subject,
			String relation,
			String object,
			boolean derived,
			double confidence,
			List<String> sourceIds,
			List<String> evidencePointers) {
	}
}
