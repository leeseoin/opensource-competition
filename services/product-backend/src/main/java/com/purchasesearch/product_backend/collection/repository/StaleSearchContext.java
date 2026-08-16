package com.purchasesearch.product_backend.collection.repository;

import java.time.Instant;

/**
 * StaleSearchContext는 사전 갱신 대상인 기본 검색 범위 하나의 최신 수집 시각을 표현한다.
 */
public interface StaleSearchContext {

	/** @return 판매처 식별자 */
	String getMerchant();

	/** @return 원본 검색어 */
	String getSearchQuery();

	/** @return 이 판매처/검색어 조합의 마지막 수집 완료 시각 */
	Instant getCollectedAt();
}
