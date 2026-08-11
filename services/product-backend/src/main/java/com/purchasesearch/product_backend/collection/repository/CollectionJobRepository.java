package com.purchasesearch.product_backend.collection.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.purchasesearch.product_backend.collection.entity.CollectionJob;

/**
 * CollectionJobRepository는 수집 job의 상태와 조회 정보를 PostgreSQL에 저장한다.
 */
public interface CollectionJobRepository extends JpaRepository<CollectionJob, String> {

	/** StatusCount는 상태 문자열 하나와 그 상태인 job 개수다. */
	interface StatusCount {

		/** @return job 상태 문자열 */
		String getStatus();

		/** @return 해당 상태인 job 개수 */
		long getCount();
	}

	/**
	 * 요청 시각이 창 안에 있는 job을 상태별로 센다.
	 *
	 * @param since 창 시작 시각(포함)
	 * @param until 창 끝 시각(미포함)
	 * @return 상태별 job 개수
	 */
	@Query("""
			SELECT job.status AS status, COUNT(job) AS count
			FROM CollectionJob job
			WHERE job.requestedAt >= :since AND job.requestedAt < :until
			GROUP BY job.status
			""")
	List<StatusCount> countByStatusInWindow(@Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);
}
