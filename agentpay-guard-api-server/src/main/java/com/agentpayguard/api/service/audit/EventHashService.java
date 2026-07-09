package com.agentpayguard.api.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * 감사 이벤트 payload를 재현 가능한 canonical JSON으로 직렬화하고 SHA-256 hash를 만든다.
 * 같은 payload가 같은 eventHash를 만들도록 property와 map key 정렬을 강제한다.
 */
@Service
public class EventHashService {

    private final ObjectMapper objectMapper;

    public EventHashService(ObjectMapper objectMapper) {
        ObjectMapper canonicalObjectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        canonicalObjectMapper.setConfig(canonicalObjectMapper.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        this.objectMapper = canonicalObjectMapper;
    }

    /**
     * hash 입력값으로 사용할 canonical JSON 문자열을 생성한다.
     */
    public String toCanonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to create canonical JSON", e);
        }
    }

    /**
     * canonical JSON을 SHA-256으로 해시하고 API 내부 표현인 "sha256:{hex}" 형식으로 반환한다.
     */
    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
