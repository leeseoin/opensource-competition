package com.purchaseresearch.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crawler.service")
public record CrawlerServiceProperties(String baseUrl) {
}
