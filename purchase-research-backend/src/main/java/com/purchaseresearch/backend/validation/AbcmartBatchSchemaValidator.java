package com.purchaseresearch.backend.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import com.purchaseresearch.backend.dto.ProductPayload;

/**
 * contracts/collector/v1-abcmart/product-batch-request.schema.json을 런타임에 강제한다.
 * DB 컬럼 제약(VARCHAR 길이 등)을 위반하는 상품은 SQL 오류로 배치 전체를 흔들기 전에
 * 여기서 걸러 failures로 반환한다.
 *
 * 앱 전역 ObjectMapper 빈을 주입받지 않고 전용 인스턴스를 직접 만든다. 두 가지 이유다.
 * 1) 이 프로젝트의 spring-boot-starter-webmvc 조합에서는 JacksonAutoConfiguration이
 *    ObjectMapper 빈을 등록하지 않는다(실제로 컨텍스트 기동 시 NoSuchBeanDefinitionException).
 * 2) 설령 전역 빈이 있어도 다른 설정(네이밍 전략 등)이 얹혀 있으면 계약이 기대하는
 *    camelCase 필드 모양과 어긋날 수 있다 — 검증 목적에는 기본 설정 그대로가 맞다.
 */
@Component
public class AbcmartBatchSchemaValidator {

	private static final String SCHEMA_RESOURCE = "/contracts/product-batch-request.schema.json";

	private final ObjectMapper objectMapper;
	private final JsonSchema schema;

	public AbcmartBatchSchemaValidator() {
		this.objectMapper = new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		this.schema = loadSchema();
	}

	private JsonSchema loadSchema() {
		JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
		try (InputStream in = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException(
						"classpath에서 " + SCHEMA_RESOURCE + "를 찾을 수 없다. "
								+ "build.gradle의 copyContractSchema 태스크가 실행됐는지 확인할 것.");
			}
			return factory.getSchema(in);
		} catch (IOException e) {
			throw new IllegalStateException("product-batch-request.schema.json 로드 실패", e);
		}
	}

	/**
	 * 상품 하나를 {site, products:[payload]} 형태의 최소 배치 봉투로 감싸서 검증한다.
	 * 스키마 자체가 배치 단위(ProductBatchRequest)로 정의돼 있어 개별 상품만 따로
	 * 검증할 대상 스키마가 없기 때문이다.
	 */
	public List<String> validate(String site, ProductPayload payload) {
		ObjectNode envelope = objectMapper.createObjectNode();
		envelope.put("site", site);
		envelope.putNull("keyword");
		envelope.putNull("collectedAt");
		ArrayNode products = envelope.putArray("products");
		products.add(objectMapper.valueToTree(payload));

		JsonNode node = envelope;
		Set<ValidationMessage> messages = schema.validate(node);
		return messages.stream().map(ValidationMessage::getMessage).toList();
	}
}
