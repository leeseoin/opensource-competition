package com.purchasesearch.product_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * ProductBackendApplicationTests는 PostgreSQL과 RabbitMQ 테스트 컨테이너를 연결한
 * 애플리케이션 문맥이 정상적으로 생성되는지 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductBackendApplicationTests {

	/**
	 * contextLoads는 필수 Bean과 외부 서비스 연결 설정이 충돌 없이 초기화되는지 검증한다.
	 */
	@Test
	void contextLoads() {
	}

}
