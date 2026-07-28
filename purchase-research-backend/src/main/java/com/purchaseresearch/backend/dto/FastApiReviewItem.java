package com.purchaseresearch.backend.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * purchase-research-agent의 DetailFetcher._parse_review()가 반환하는 리뷰 dict.
 * date는 review_date가 아니라 date 키로 온다 — CrawlTriggerService에서 변환할 때
 * ReviewPayload.reviewDate로 이름을 맞춰준다.
 */
public record FastApiReviewItem(
		@JsonProperty("review_source_id") String reviewSourceId,
		String content,
		BigDecimal score,
		String date,
		String size
) {
}
