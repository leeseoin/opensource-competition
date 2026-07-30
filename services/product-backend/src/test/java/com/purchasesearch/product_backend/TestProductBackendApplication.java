package com.purchasesearch.product_backend;

import org.springframework.boot.SpringApplication;

/**
 * TestProductBackendApplication은 로컬 테스트 실행 시 Testcontainers 설정을 포함해
 * Product Backend를 시작한다.
 */
public class TestProductBackendApplication {

	/**
	 * 테스트용 PostgreSQL과 RabbitMQ를 연결한 Product Backend를 시작한다.
	 *
	 * @param args Spring Boot에 전달할 실행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.from(ProductBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
