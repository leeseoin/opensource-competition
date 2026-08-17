package com.purchasesearch.product_backend.collection.exception;

/**
 * DuplicateCollectionTaskException은 같은 idempotencyKey의 작업이 이미 QUEUED 또는 RUNNING
 * 상태로 진행 중이어서 새 작업을 발행하지 않고 거부했음을 나타낸다.
 *
 * <p>{@link CollectionTaskPublishException}을 상속해, 배치 발행 중 개별 실패를 건너뛰고 계속
 * 진행하는 {@code CollectionRefreshService}/{@code BulkOfferVerificationRunner}의 기존
 * catch 구문이 별도 수정 없이 중복 거부도 똑같이 안전하게 처리하게 한다.
 */
public class DuplicateCollectionTaskException extends CollectionTaskPublishException {

	/**
	 * 중복으로 거부한 조건 설명으로 예외를 생성한다.
	 *
	 * @param message 사용자에게 반환할 중복 사유 설명
	 */
	public DuplicateCollectionTaskException(String message) {
		super(message);
	}
}
