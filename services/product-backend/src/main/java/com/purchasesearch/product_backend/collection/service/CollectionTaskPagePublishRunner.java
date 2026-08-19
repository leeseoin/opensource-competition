package com.purchasesearch.product_backend.collection.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;

/**
 * CollectionTaskPagePublishRunner는 다중 페이지 검색 수집(최대 200페이지)의 실제 RabbitMQ 발행을
 * 배경 스레드에서 순차 처리한다. 페이지마다 broker confirm 대기(최대 5초)가 있어 페이지 수가
 * 많으면 HTTP 스레드를 오래 붙잡을 수 있으므로, {@link CollectionTaskPublisher#publishPages}가
 * job을 등록한 뒤 이 메서드 호출만 하고 바로 응답하도록 접수와 발행을 분리했다. {@code @Async}가
 * 실제로 배경 스레드에서 실행되려면 프록시를 통한 외부 호출이어야 하므로, 이 클래스는
 * {@link CollectionTaskPublisher}에 의존하지 않고 저수준 발행 Bean인
 * {@link CollectionTaskAmqpGateway}에 직접 의존한다(self-invocation·순환 참조 방지).
 */
@Component
public class CollectionTaskPagePublishRunner {

	private static final Logger log = LoggerFactory.getLogger(CollectionTaskPagePublishRunner.class);

	private final CollectionTaskAmqpGateway collectionTaskAmqpGateway;
	private final CollectionJobService collectionJobService;

	/**
	 * @param collectionTaskAmqpGateway 작업 한 건을 발행하는 저수준 Bean
	 * @param collectionJobService 발행 실패를 기록할 job 상태 서비스
	 */
	public CollectionTaskPagePublishRunner(
			CollectionTaskAmqpGateway collectionTaskAmqpGateway, CollectionJobService collectionJobService) {
		this.collectionTaskAmqpGateway = collectionTaskAmqpGateway;
		this.collectionJobService = collectionJobService;
	}

	/**
	 * 등록된 페이지 작업을 순서대로 발행한다. 한 작업의 발행이 실패하면 broker 연결
	 * 자체가 불안정할 가능성이 높으므로, 나머지 작업도 함께 PUBLISH_FAILED로 기록하고
	 * 중단한다(HTTP 스레드가 이미 응답을 반환했으므로 예외를 던지지 않고 로그만 남긴다).
	 *
	 * @param tasks 이미 QUEUED로 등록된, 같은 jobId를 공유하는 페이지 작업 목록
	 */
	@Async("collectionPagePublishExecutor")
	public void runAsync(List<CollectionTaskMessage> tasks) {
		for (int index = 0; index < tasks.size(); index++) {
			CollectionTaskMessage task = tasks.get(index);
			try {
				collectionTaskAmqpGateway.publish(task);
			} catch (CollectionTaskPublishException exception) {
				log.warn("페이지 작업 발행 실패, 나머지 {}건도 중단합니다. jobId={}, taskId={}",
						tasks.size() - index, task.jobId(), task.taskId(), exception);
				collectionJobService.markPublishFailed(
						tasks.subList(index, tasks.size()).stream().map(CollectionTaskMessage::taskId).toList(),
						exception.getMessage());
				return;
			}
		}
	}
}
