package com.purchasesearch.product_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ProductBackendApplication은 상품 조회, 수집 작업 관리, 수집 결과 저장 기능을 제공하는
 * Spring Boot 애플리케이션의 시작점이다.
 */
@SpringBootApplication
public class ProductBackendApplication {

	/**
	 * Product Backend를 시작한다.
	 *
	 * @param args Spring Boot에 전달할 실행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(ProductBackendApplication.class, args);
	}

}
