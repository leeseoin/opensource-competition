package com.purchasesearch.product_backend.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AsyncConfig는 HTTP 스레드를 오래 붙잡을 수 있는 반복 발행 작업(대량 재검증 배치, 다중 페이지
 * 검색 수집 발행)을 처리할 배경 스레드 풀을 구성한다. 배치/페이지 묶음 하나는 순서대로
 * 처리하므로 스레드 하나만 있어도 충분하지만, 여러 요청이 겹쳐 접수될 수 있어 소규모 풀로 둔다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/** @return 대량 재검증 배치 전용 배경 스레드 풀 */
	@Bean("bulkVerificationExecutor")
	public Executor bulkVerificationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("bulk-verify-");
		executor.initialize();
		return executor;
	}

	/** @return 다중 페이지 검색 수집 발행 전용 배경 스레드 풀 */
	@Bean("collectionPagePublishExecutor")
	public Executor collectionPagePublishExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("page-publish-");
		executor.initialize();
		return executor;
	}
}
