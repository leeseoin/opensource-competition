package com.purchaseresearch.backend.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.purchaseresearch.backend.dto.OptionsPayload;
import com.purchaseresearch.backend.dto.ProductPayload;
import com.purchaseresearch.backend.dto.ReviewPayload;

import java.math.BigDecimal;
import java.time.LocalDate;

class AbcmartBatchSchemaValidatorTest {

	private final AbcmartBatchSchemaValidator validator = new AbcmartBatchSchemaValidator();

	@Test
	void validPayloadHasNoErrors() {
		ProductPayload payload = new ProductPayload(
				"10098765",
				"이지 워커 남성 캐주얼 구두",
				"ABC MART",
				89000,
				109000,
				18,
				"https://image.abc-mart.co.kr/goods/10098765/main.jpg",
				"AB1234",
				"https://abcmart.a-rt.com/product?prdtNo=10098765",
				12,
				new OptionsPayload(List.of("블랙", "브라운"), List.of("260", "270")),
				List.of(new ReviewPayload("998877", "발볼이 편해요", new BigDecimal("4.5"), LocalDate.of(2026, 7, 20), "270"))
		);

		List<String> errors = validator.validate("abcmart", payload);

		assertTrue(errors.isEmpty(), "예상치 못한 스키마 오류: " + errors);
	}

	@Test
	void titleOver300CharsFailsSchema() {
		ProductPayload payload = new ProductPayload(
				"10098765",
				"a".repeat(301),
				null, null, null, null, null, null,
				"https://abcmart.a-rt.com/product?prdtNo=10098765",
				null, null, null
		);

		List<String> errors = validator.validate("abcmart", payload);

		assertFalse(errors.isEmpty(), "title 300자 초과인데 스키마가 통과시켰다 — DB VARCHAR(300)에서 잘릴 값이다");
	}

	@Test
	void blankSourceProductIdFailsSchema() {
		ProductPayload payload = new ProductPayload(
				"",
				"제목",
				null, null, null, null, null, null,
				"https://abcmart.a-rt.com/product?prdtNo=10098765",
				null, null, null
		);

		List<String> errors = validator.validate("abcmart", payload);

		assertFalse(errors.isEmpty(), "sourceProductId가 빈 문자열인데 스키마가 통과시켰다");
	}
}
