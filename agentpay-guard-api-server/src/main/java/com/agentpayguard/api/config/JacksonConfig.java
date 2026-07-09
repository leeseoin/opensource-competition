package com.agentpayguard.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper를 명시적으로 등록하는 설정이다.
 * Spring Boot 4.1 현재 구성에서는 ObjectMapper Bean이 자동 등록되지 않아 감사 hash 서비스와 JSON 응답 처리가 같은 mapper를 공유하도록 한다.
 */
@Configuration
public class JacksonConfig {

    /**
     * API JSON 직렬화와 canonical hash 생성의 기준이 되는 기본 ObjectMapper를 제공한다.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }
}
