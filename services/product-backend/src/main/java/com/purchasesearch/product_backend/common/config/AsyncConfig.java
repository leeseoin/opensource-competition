package com.purchasesearch.product_backend.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AsyncConfig는 대량 재검증 배치를 처리할 배경 스레드 풀을 구성한다. 배치 하나는
 * 상품을 순서대로 처리하므로 스레드 하나만 있어도 충분하지만, 여러 배치가 겹쳐
 * 접수될 수 있어 소규모 풀로 둔다.
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
}
