package com.purchasesearch.product_backend.collection.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchFilters;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage.SearchPayload;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionTaskException;
import com.purchasesearch.product_backend.collection.messaging.CollectionQueueNames;

import tools.jackson.databind.ObjectMapper;

/**
 * CollectionTaskPublisher는 HTTP 검색 조건을 Queue v1 작업으로 정규화하고 RabbitMQ broker 확인까지 기다린다.
 */
@Service
public class CollectionTaskPublisher {

	private static final int DEFAULT_PAGE = 1;
	private static final int DEFAULT_PAGE_COUNT = 1;
	private static final int MAX_PAGE = 200;
	private static final int DEFAULT_LIMIT = 30;
	private static final int DEFAULT_PRIORITY = 20;
	private static final int DEFAULT_MAX_ATTEMPTS = 2;
	private static final String DEFAULT_LOCALE = "ko-KR";
	private static final String DEFAULT_CURRENCY = "KRW";
	private static final long CONFIRM_TIMEOUT_SECONDS = 5;

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * 작업 JSON 생성과 RabbitMQ 발행에 필요한 의존성을 연결한다.
	 *
	 * @param rabbitTemplate Spring AMQP 발행 도구
	 * @param objectMapper Spring Boot 공통 JSON mapper
	 */
	public CollectionTaskPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 검색 요청을 정규화해 persistent 메시지로 발행하고 broker ACK 뒤 접수 결과를 반환한다.
	 *
	 * @param request Swagger 또는 관리 API가 받은 검색 조건
	 * @return 발행된 작업 추적 정보
	 * @throws InvalidCollectionTaskException 현재 지원 범위나 필터 의미를 위반한 경우
	 * @throws CollectionTaskPublishException 직렬화, RabbitMQ 발행 또는 confirm에 실패한 경우
	 */
	public CollectionTaskResponse publish(CollectionTaskRequest request) {
		CollectionTaskMessage task = createTask(request);
		publishTask(task);
		return CollectionTaskResponse.queued(task);
	}

	/**
	 * 연속된 검색 페이지를 같은 jobId의 독립 작업으로 나눠 순서대로 발행한다.
	 *
	 * @param request 시작 페이지와 페이지 수가 포함된 검색 조건
	 * @return 전체 작업을 묶는 jobId와 발행 범위
	 * @throws InvalidCollectionTaskException 마지막 페이지가 최대 범위를 벗어난 경우
	 * @throws CollectionTaskPublishException 일부 또는 전체 작업의 broker 확인에 실패한 경우
	 */
	public BulkCollectionTaskResponse publishPages(BulkCollectionTaskRequest request) {
		int startPage = request.startPage() == null ? DEFAULT_PAGE : request.startPage();
		int pageCount = request.pageCount() == null ? DEFAULT_PAGE_COUNT : request.pageCount();
		long endPageValue = (long) startPage + pageCount - 1;
		if (startPage < 1 || pageCount < 1 || endPageValue > MAX_PAGE) {
			throw new InvalidCollectionTaskException("검색 페이지 범위는 1부터 200까지여야 합니다.");
		}

		int endPage = (int) endPageValue;
		String jobId = "job-" + UUID.randomUUID();
		OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
		int publishedCount = 0;
		for (int page = startPage; page <= endPage; page++) {
			CollectionTaskMessage task = createTask(request.toPageRequest(page), jobId, requestedAt);
			try {
				publishTask(task);
				publishedCount++;
			} catch (CollectionTaskPublishException exception) {
				throw new CollectionTaskPublishException(
						"전체 " + pageCount + "개 중 " + publishedCount
								+ "개 작업만 접수된 뒤 RabbitMQ 발행이 실패했습니다.",
						exception);
			}
		}

		return new BulkCollectionTaskResponse(
				jobId,
				"QUEUED",
				request.merchant(),
				"search",
				startPage,
				endPage,
				publishedCount,
				requestedAt);
	}

	/**
	 * 작업 한 건을 persistent 메시지로 발행하고 RabbitMQ broker ACK를 확인한다.
	 *
	 * @param task 발행할 단일 페이지 작업
	 * @throws CollectionTaskPublishException 직렬화, RabbitMQ 발행 또는 confirm에 실패한 경우
	 */
	private void publishTask(CollectionTaskMessage task) {
		CorrelationData correlationData = new CorrelationData(task.taskId());
		Message message = createMessage(task);

		try {
			rabbitTemplate.send(
					CollectionQueueNames.COLLECTION_EXCHANGE,
					CollectionQueueNames.SEARCH_ROUTING_KEY,
					message,
					correlationData);
			CorrelationData.Confirm confirm = correlationData.getFuture()
					.get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!confirm.ack()) {
				throw new CollectionTaskPublishException(
						"RabbitMQ가 수집 작업을 확인하지 않았습니다: " + confirm.reason());
			}
			if (correlationData.getReturned() != null) {
				throw new CollectionTaskPublishException("수집 작업을 받을 RabbitMQ Queue가 없습니다.");
			}
		} catch (CollectionTaskPublishException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new CollectionTaskPublishException("RabbitMQ 작업 확인 대기가 중단됐습니다.", exception);
		} catch (Exception exception) {
			throw new CollectionTaskPublishException("RabbitMQ에 수집 작업을 발행하지 못했습니다.", exception);
		}
	}

	/**
	 * API 입력에 기본값과 의미 검증을 적용해 Go Worker가 읽는 Queue 메시지를 생성한다.
	 *
	 * @param request 원본 검색 요청
	 * @return Queue v1 검색 작업
	 * @throws InvalidCollectionTaskException page, 가격 범위, 중복 목록 또는 attributes가 잘못된 경우
	 */
	CollectionTaskMessage createTask(CollectionTaskRequest request) {
		return createTask(
				request,
				"job-" + UUID.randomUUID(),
				OffsetDateTime.now(ZoneOffset.UTC));
	}

	/**
	 * 지정한 jobId와 요청 시각을 공유하는 단일 페이지 Queue 작업을 생성한다.
	 *
	 * @param request 원본 검색 요청
	 * @param jobId 여러 페이지 작업을 묶을 식별자
	 * @param requestedAt 전체 작업 공통 요청 시각
	 * @return Queue v1 검색 작업
	 * @throws InvalidCollectionTaskException 페이지, 가격 범위, 중복 목록 또는 attributes가 잘못된 경우
	 */
	private CollectionTaskMessage createTask(
			CollectionTaskRequest request,
			String jobId,
			OffsetDateTime requestedAt) {
		int page = request.page() == null ? DEFAULT_PAGE : request.page();
		if (page < 1 || page > MAX_PAGE) {
			throw new InvalidCollectionTaskException("검색 page는 1부터 200까지 지원합니다.");
		}

		SearchFilters filters = normalizeFilters(request.filters());
		if (filters.priceMin() != null && filters.priceMax() != null
				&& filters.priceMin() > filters.priceMax()) {
			throw new InvalidCollectionTaskException("priceMin은 priceMax보다 클 수 없습니다.");
		}

		SearchPayload payload = new SearchPayload(
				request.query().trim(),
				page,
				request.limit() == null ? DEFAULT_LIMIT : request.limit(),
				request.locale() == null ? DEFAULT_LOCALE : request.locale(),
				request.currency() == null ? DEFAULT_CURRENCY : request.currency(),
				filters);
		return new CollectionTaskMessage(
				"1",
				"task-" + UUID.randomUUID(),
				jobId,
				request.merchant(),
				"search",
				request.priority() == null ? DEFAULT_PRIORITY : request.priority(),
				0,
				request.maxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : request.maxAttempts(),
				requestedAt,
				createIdempotencyKey(request.merchant(), payload),
				payload);
	}

	/**
	 * nullable 필터를 빈 목록과 빈 map으로 바꾸고 계약이 허용하는 값만 복사한다.
	 *
	 * @param input API 필터 입력
	 * @return Queue에 기록할 정규화 필터
	 * @throws InvalidCollectionTaskException 목록 중복 또는 지원하지 않는 attributes 값이 있는 경우
	 */
	private SearchFilters normalizeFilters(CollectionTaskRequest.SearchFilters input) {
		if (input == null) {
			return new SearchFilters(null, null, List.of(), List.of(), List.of(), false, Map.of());
		}
		List<String> categories = normalizeList("categories", input.categories());
		List<String> sizes = normalizeList("sizes", input.sizes());
		List<String> colors = normalizeList("colors", input.colors());
		Map<String, Object> attributes = normalizeAttributes(input.attributes());
		return new SearchFilters(
				input.priceMin(),
				input.priceMax(),
				categories,
				sizes,
				colors,
				Boolean.TRUE.equals(input.inStockOnly()),
				attributes);
	}

	/**
	 * 문자열 목록을 방어적으로 복사하고 Queue schema의 uniqueItems 조건을 검사한다.
	 *
	 * @param fieldName 오류에 표시할 필드명
	 * @param values 원본 문자열 목록
	 * @return 수정할 수 없는 문자열 목록
	 * @throws InvalidCollectionTaskException 중복 값이 있는 경우
	 */
	private List<String> normalizeList(String fieldName, List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> copy = List.copyOf(values);
		if (new HashSet<>(copy).size() != copy.size()) {
			throw new InvalidCollectionTaskException(fieldName + "에는 중복 값을 넣을 수 없습니다.");
		}
		return copy;
	}

	/**
	 * 판매처 확장 속성을 key 순서로 정렬하고 계약이 허용한 scalar 또는 문자열 목록인지 검사한다.
	 *
	 * @param attributes 원본 판매처 확장 속성
	 * @return key가 정렬된 수정 불가능한 map
	 * @throws InvalidCollectionTaskException 지원하지 않는 값 또는 잘못된 문자열 목록인 경우
	 */
	private Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
		if (attributes == null || attributes.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> normalized = new TreeMap<>();
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof String text) {
				if (text.length() > 200) {
					throw new InvalidCollectionTaskException("attributes 문자열은 200자를 넘을 수 없습니다.");
				}
				normalized.put(entry.getKey(), text);
			} else if (value instanceof Number || value instanceof Boolean) {
				normalized.put(entry.getKey(), value);
			} else if (value instanceof List<?> values) {
				normalized.put(entry.getKey(), normalizeAttributeList(entry.getKey(), values));
			} else {
				throw new InvalidCollectionTaskException(
						"attributes." + entry.getKey() + " 값 형식을 지원하지 않습니다.");
			}
		}
		return Collections.unmodifiableMap(normalized);
	}

	/**
	 * attributes 배열이 비어 있지 않은 고유 문자열 목록인지 확인한다.
	 *
	 * @param key 속성 key
	 * @param values 검사할 원본 목록
	 * @return 문자열로 확인된 수정 불가능한 목록
	 * @throws InvalidCollectionTaskException 빈 목록, 문자열이 아닌 값, 길이 초과 또는 중복이 있는 경우
	 */
	private List<String> normalizeAttributeList(String key, List<?> values) {
		if (values.isEmpty() || values.size() > 50) {
			throw new InvalidCollectionTaskException("attributes." + key + " 목록 개수가 올바르지 않습니다.");
		}
		List<String> normalized = new ArrayList<>();
		for (Object value : values) {
			if (!(value instanceof String text) || text.isBlank() || text.length() > 100) {
				throw new InvalidCollectionTaskException(
						"attributes." + key + " 목록은 1자 이상 100자 이하 문자열만 허용합니다.");
			}
			normalized.add(text);
		}
		if (new HashSet<>(normalized).size() != normalized.size()) {
			throw new InvalidCollectionTaskException("attributes." + key + "에는 중복 값을 넣을 수 없습니다.");
		}
		return List.copyOf(normalized);
	}

	/**
	 * 판매처와 검색 payload의 정렬된 JSON을 SHA-256으로 계산해 Queue v1 멱등성 키를 만든다.
	 *
	 * @param merchant 판매처 식별자
	 * @param payload 정규화된 검색 payload
	 * @return collection:v1 접두사가 붙은 64자리 SHA-256 key
	 * @throws CollectionTaskPublishException canonical JSON 생성 또는 SHA-256 사용에 실패한 경우
	 */
	private String createIdempotencyKey(String merchant, SearchPayload payload) {
		Map<String, Object> canonical = new TreeMap<>();
		canonical.put("merchant", merchant);
		canonical.put("operation", "search");
		canonical.put("payload", payload);
		try {
			byte[] json = objectMapper.writeValueAsBytes(canonical);
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
			return "collection:v1:" + HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new CollectionTaskPublishException("SHA-256 멱등성 키를 만들 수 없습니다.", exception);
		} catch (Exception exception) {
			throw new CollectionTaskPublishException("수집 조건을 멱등성 JSON으로 만들 수 없습니다.", exception);
		}
	}

	/**
	 * Queue 작업을 persistent JSON RabbitMQ 메시지로 직렬화한다.
	 *
	 * @param task 발행할 수집 작업
	 * @return 메시지 ID와 우선순위가 포함된 RabbitMQ 메시지
	 * @throws CollectionTaskPublishException JSON 직렬화에 실패한 경우
	 */
	private Message createMessage(CollectionTaskMessage task) {
		try {
			byte[] body = objectMapper.writeValueAsBytes(task);
			return MessageBuilder.withBody(body)
					.setContentType(MessageProperties.CONTENT_TYPE_JSON)
					.setContentEncoding(StandardCharsets.UTF_8.name())
					.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
					.setMessageId(task.taskId())
					.setPriority(task.priority())
					.setTimestamp(java.util.Date.from(task.requestedAt().toInstant()))
					.build();
		} catch (Exception exception) {
			throw new CollectionTaskPublishException("CollectionTask JSON을 생성하지 못했습니다.", exception);
		}
	}
}
