package com.purchaseresearch.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * purchase-research-agent(FastAPI) 크롤링 서비스를 호출하기 위한 RestClient.
 * CrawlTriggerService에서 주입받아 /api/v1/search를 pull로 호출한다.
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
