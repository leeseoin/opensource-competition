package com.purchasesearch.product_backend.collection.exception;

/**
 * CollectionJobNotFoundException은 요청한 수집 job이 저장돼 있지 않을 때 발생한다.
 */
public class CollectionJobNotFoundException extends RuntimeException {

	/**
	 * 찾지 못한 job 식별자를 포함한 예외를 생성한다.
	 *
	 * @param jobId 조회한 job 식별자
	 */
	public CollectionJobNotFoundException(String jobId) {
		super("수집 job을 찾을 수 없습니다: " + jobId);
	}
}
