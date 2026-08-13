package com.purchasesearch.product_backend.collection.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	/** JobSummaryProjection은 job 하나와 그 페이지 작업 전체의 성공/실패/상품 수 합계다. */
	interface JobSummaryProjection {

		/** @return 수집 요청 묶음 식별자 */
		String getJobId();

		/** @return 판매처 식별자 */
		String getMerchant();

		/** @return 검색어 */
		String getSearchQuery();

		/** @return 전체 작업 상태 */
		String getStatus();

		/** @return 전체 페이지 작업 수 */
		int getTaskCount();

		/** @return 성공한 페이지 작업 수 */
		long getSucceededTaskCount();

		/** @return 실행 또는 발행에 실패한 페이지 작업 수 */
		long getFailedTaskCount();

		/** @return DB에 처리한 상품 수 합계 */
		long getProductCount();

		/** @return JSON/HTML 검증을 시도한 상품 수 합계 */
		long getVerificationTotal();

		/** @return JSON/HTML이 일치한 상품 수 합계 */
		long getVerificationMatched();

		/** @return job 요청 시각 */
		OffsetDateTime getRequestedAt();

		/** @return 모든 작업 종료 시각 */
		OffsetDateTime getCompletedAt();
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

	/**
	 * 판매처/상태 조건과 일치하는 job을 최신 요청순으로 페이지 작업 집계와 함께 조회한다.
	 *
	 * @param merchant 선택 판매처이며 없으면 전체
	 * @param status 선택 job 상태이며 없으면 전체
	 * @param pageable 페이지 번호와 크기
	 * @return 요청 이력 화면에 필요한 job 요약 page
	 */
	@Query(
			value = """
					SELECT job.jobId AS jobId, job.merchant AS merchant, job.searchQuery AS searchQuery,
					       job.status AS status, job.totalTasks AS taskCount,
					       COALESCE(SUM(CASE WHEN task.status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS succeededTaskCount,
					       COALESCE(SUM(CASE WHEN task.status = 'FAILED' OR task.status = 'PUBLISH_FAILED' THEN 1 ELSE 0 END), 0)
					           AS failedTaskCount,
					       COALESCE(SUM(task.productCount), 0) AS productCount,
					       COALESCE(SUM(task.verificationTotal), 0) AS verificationTotal,
					       COALESCE(SUM(task.verificationMatched), 0) AS verificationMatched,
					       job.requestedAt AS requestedAt, job.completedAt AS completedAt
					FROM CollectionJob job LEFT JOIN CollectionTask task ON task.job = job
					WHERE (:merchant IS NULL OR job.merchant = :merchant)
					  AND (:status IS NULL OR job.status = :status)
					GROUP BY job.jobId, job.merchant, job.searchQuery, job.status, job.totalTasks,
					         job.requestedAt, job.completedAt
					ORDER BY job.requestedAt DESC
					""",
			countQuery = """
					SELECT COUNT(job)
					FROM CollectionJob job
					WHERE (:merchant IS NULL OR job.merchant = :merchant)
					  AND (:status IS NULL OR job.status = :status)
					""")
	Page<JobSummaryProjection> search(
			@Param("merchant") String merchant,
			@Param("status") String status,
			Pageable pageable);
}
