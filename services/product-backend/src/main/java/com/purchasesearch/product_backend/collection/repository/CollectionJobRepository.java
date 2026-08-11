package com.purchasesearch.product_backend.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.collection.entity.CollectionJob;

/**
 * CollectionJobRepository는 수집 job의 상태와 조회 정보를 PostgreSQL에 저장한다.
 */
public interface CollectionJobRepository extends JpaRepository<CollectionJob, String> {
}
