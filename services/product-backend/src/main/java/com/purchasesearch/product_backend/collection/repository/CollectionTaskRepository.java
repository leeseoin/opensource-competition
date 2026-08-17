package com.purchasesearch.product_backend.collection.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.collection.entity.CollectionTask;

/**
 * CollectionTaskRepository는 job에 속한 페이지 작업을 저장하고 페이지 순서로 조회한다.
 */
public interface CollectionTaskRepository extends JpaRepository<CollectionTask, String> {

	/** StatusCount는 상태 문자열 하나와 그 상태인 페이지 작업 개수다. */
	interface StatusCount {

		/** @return 페이지 작업 상태 문자열 */
		String getStatus();

		/** @return 해당 상태인 페이지 작업 개수 */
		long getCount();
	}

	/**
	 * 한 job의 페이지 작업을 사용자 확인 순서로 반환한다.
	 *
	 * @param jobId 상위 job 식별자
	 * @return 페이지 오름차순 작업 목록
	 */
	List<CollectionTask> findAllByJobJobIdOrderByPageAsc(String jobId);

	/**
	 * 같은 idempotencyKey를 가진 작업이 지정한 상태 중 하나로 아직 진행 중인지 확인한다.
	 *
	 * @param idempotencyKey 판매처/검색 조건에서 계산한 멱등성 key
	 * @param statuses 진행 중으로 간주할 작업 상태 목록
	 * @return 진행 중인 동일 조건 작업이 있으면 true
	 */
	boolean existsByIdempotencyKeyAndStatusIn(String idempotencyKey, Collection<String> statuses);

	/**
	 * 요청 시각이 창 안에 있는 페이지 작업을 상태별로 센다.
	 *
	 * @param since 창 시작 시각(포함)
	 * @param until 창 끝 시각(미포함)
	 * @return 상태별 페이지 작업 개수
	 */
	@Query("""
			SELECT task.status AS status, COUNT(task) AS count
			FROM CollectionTask task
			WHERE task.requestedAt >= :since AND task.requestedAt < :until
			GROUP BY task.status
			""")
	List<StatusCount> countByStatusInWindow(@Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);
}
