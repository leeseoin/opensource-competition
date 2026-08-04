package com.purchasesearch.product_backend.collection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.collection.entity.CollectionTask;

/**
 * CollectionTaskRepository는 job에 속한 페이지 작업을 저장하고 페이지 순서로 조회한다.
 */
public interface CollectionTaskRepository extends JpaRepository<CollectionTask, String> {

	/**
	 * 한 job의 페이지 작업을 사용자 확인 순서로 반환한다.
	 *
	 * @param jobId 상위 job 식별자
	 * @return 페이지 오름차순 작업 목록
	 */
	List<CollectionTask> findAllByJobJobIdOrderByPageAsc(String jobId);
}
