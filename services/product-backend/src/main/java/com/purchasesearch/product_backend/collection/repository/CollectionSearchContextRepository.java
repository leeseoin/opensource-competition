package com.purchasesearch.product_backend.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.collection.entity.CollectionSearchContext;

/**
 * CollectionSearchContextRepository는 requestId별 검색 문맥의 저장과 중복 확인을 담당한다.
 */
public interface CollectionSearchContextRepository
		extends JpaRepository<CollectionSearchContext, String> {
}
