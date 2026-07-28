package com.purchaseresearch.backend.domain;

import java.util.Arrays;

/**
 * DB에는 site 문자열 대신 이 코드를 저장한다. API/크롤러 쪽 JSON은 여전히
 * "abcmart", "29cm" 같은 문자열을 쓰고(가독성, Python 크롤러 쪽 영향 최소화),
 * ProductIngestService가 영속화 직전에 이 enum으로 변환한다.
 *
 * code는 여기 정의된 값이 DB의 실제 데이터와 대응되므로, 이미 저장된 코드는
 * 절대 재사용/변경하지 않는다. 새 사이트를 추가할 때는 다음 정수를 이어서 쓴다.
 */
public enum SiteType {

	ABCMART(1, "abcmart"),
	CM29(2, "29cm");

	private final int code;
	private final String key;

	SiteType(int code, String key) {
		this.code = code;
		this.key = key;
	}

	public int getCode() {
		return code;
	}

	public String getKey() {
		return key;
	}

	public static SiteType fromKey(String key) {
		return Arrays.stream(values())
				.filter(t -> t.key.equals(key))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 site: " + key));
	}

	public static SiteType fromCode(int code) {
		return Arrays.stream(values())
				.filter(t -> t.code == code)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("알 수 없는 site_type 코드: " + code));
	}
}
