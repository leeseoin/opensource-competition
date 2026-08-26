package com.purchasesearch.product_backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * OpenApiConfiguration은 Product Backend 내부 API의 Swagger UI 제목, 버전과 설명을
 * 공통으로 설정한다.
 */
@Configuration
public class OpenApiConfiguration {

	/**
	 * Product Backend가 제공하는 내부 API의 OpenAPI 기본 정보를 생성한다.
	 *
	 * @return Swagger UI와 OpenAPI JSON에 표시할 API 정보
	 */
	@Bean
	public OpenAPI productBackendOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Purchase Research Product Backend API")
						.version("v1")
						.description(
								"Collector 결과 수동 적재와 PostgreSQL 상품 조회를 검증하는 내부 API"));
	}
}
