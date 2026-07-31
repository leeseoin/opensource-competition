package com.purchasesearch.product_backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestcontainersConfiguration은 통합 테스트에 필요한 PostgreSQL과 RabbitMQ 컨테이너를
 * Spring Boot 서비스 연결로 등록한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/**
	 * PostgreSQL 테스트 컨테이너를 생성한다.
	 *
	 * @return Spring Boot가 DataSource로 연결할 PostgreSQL 컨테이너
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

	/**
	 * RabbitMQ 테스트 컨테이너를 생성한다.
	 *
	 * @return Spring Boot가 AMQP 연결로 사용할 RabbitMQ 컨테이너
	 */
	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.2-management-alpine"));
	}

}
