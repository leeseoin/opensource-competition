package com.purchaseresearch.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * purchase-research-agent(FastAPI) 크롤링 서비스를 호출하기 위한 RestClient.
 * 실제 프록시/집계 엔드포인트는 아직 구현 전이며, 이 빈을 주입받아 사용한다.
 */
@Configuration
@EnableConfigurationProperties(CrawlerServiceProperties.class)
public class CrawlerServiceClientConfig {

	@Bean
	public RestClient crawlerServiceRestClient(CrawlerServiceProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.build();
	}

}
