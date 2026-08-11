package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

/** FlywayMigrationIntegrationTests는 이전 schema의 실제 data가 최신 계약으로 이관되는지 검증한다. */
class FlywayMigrationIntegrationTests {

	/**
	 * V5의 문자열 구매 조건 JSONB를 V6의 필수/선호 조건 구조로 변환하고 pg_trgm을
	 * 활성화하는지 검증한다.
	 *
	 * @throws Exception PostgreSQL 시작, migration, SQL 또는 JSON 처리에 실패한 경우
	 */
	@Test
	void migratesLegacyPurchaseConditionsAndEnablesTrigramSearch() throws Exception {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:0.8.2-pg16"))) {
			postgres.start();
			Flyway.configure()
					.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.target(MigrationVersion.fromVersion("5"))
					.load()
					.migrate();

			try (var connection = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
					var statement = connection.prepareStatement("""
							INSERT INTO research_sessions
							    (id, question, runtime, plugin_id, status, conditions)
							VALUES
							    ('00000000-0000-4000-8000-000000000001', '면접용 갈색 구두',
							     'codex', 'purchase-research-agent', 'DRAFT', ?::jsonb)
							""")) {
				statement.setString(1, """
						{
						  "productType": "구두",
						  "usage": ["면접"],
						  "price": {"min": null, "max": 100000, "currency": "KRW"},
						  "colors": ["brown"],
						  "sizes": ["265"],
						  "requirements": [],
						  "merchant": null,
						  "missingConditions": [],
						  "assumptions": [],
						  "confidence": 0.97,
						  "requiresConfirmation": true
						}
						""");
				statement.executeUpdate();
			}

			Flyway.configure()
					.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.load()
					.migrate();

			try (var connection = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
					var conditionQuery = connection.createStatement();
					var conditions = conditionQuery.executeQuery("""
							SELECT conditions::text
							FROM research_sessions
							WHERE id = '00000000-0000-4000-8000-000000000001'
							""")) {
				assertThat(conditions.next()).isTrue();
				var conditionJson = new ObjectMapper().readTree(conditions.getString(1));
				assertThat(conditionJson.at("/productType/value").asText()).isEqualTo("구두");
				assertThat(conditionJson.at("/productType/priority").asText()).isEqualTo("required");
				assertThat(conditionJson.at("/usage/0/priority").asText()).isEqualTo("preferred");
				assertThat(conditionJson.at("/colors/0/priority").asText()).isEqualTo("preferred");
				assertThat(conditionJson.at("/sizes/0/priority").asText()).isEqualTo("required");
				assertThat(conditionJson.at("/price/priority").asText()).isEqualTo("required");
			}

			try (var connection = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
					var extensionQuery = connection.createStatement();
					var extensions = extensionQuery.executeQuery(
							"SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'")) {
				assertThat(extensions.next()).isTrue();
				assertThat(extensions.getInt(1)).isEqualTo(1);
			}
		}
	}
}
