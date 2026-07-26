package com.purchaseresearch.backend.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.purchaseresearch.backend.dto.FastApiProductItem;
import com.purchaseresearch.backend.dto.FastApiSearchResponse;
import com.purchaseresearch.backend.dto.ProductBatchRequest;
import com.purchaseresearch.backend.dto.ProductBatchResponse;
import com.purchaseresearch.backend.dto.ProductPayload;

import lombok.RequiredArgsConstructor;

/**
 * §3.3의 "크롤링 트리거(pull)" 구현체.
 * Spring이 purchase-research-agent(FastAPI)의 /api/v1/search를 직접 호출(pull)해서
 * 크롤링시키고, 받은 결과를 곧바로 ProductIngestService로 적재한다. Python이 별도로
 * /api/v1/products/batch를 다시 호출할 필요 없이 한 번의 트리거로 끝난다.
 */
@Service
@RequiredArgsConstructor
public class CrawlTriggerService {

	private final RestClient crawlerServiceRestClient;
	private final ProductIngestService productIngestService;

	public ProductBatchResponse triggerAndIngest(String site, String keyword, int maxItems) {
		FastApiSearchResponse response = crawlerServiceRestClient.post()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/search")
						.queryParam("keyword", keyword)
						.queryParam("site", site)
						.queryParam("max_items", maxItems)
						.build())
				.retrieve()
				.body(FastApiSearchResponse.class);

		List<FastApiProductItem> items = (response != null && response.items() != null)
				? response.items()
				: List.of();

		if (items.isEmpty()) {
			return new ProductBatchResponse(0, 0, 0, List.of());
		}

		List<ProductPayload> payloads = items.stream()
				.map(this::toPayload)
				.toList();

		ProductBatchRequest request = new ProductBatchRequest(site, keyword, OffsetDateTime.now(), payloads);
		return productIngestService.ingest(request);
	}

	private ProductPayload toPayload(FastApiProductItem item) {
		return new ProductPayload(
				item.sourceProductId(),
				item.title(),
				item.brand(),
				parsePrice(item.price()),
				parsePrice(item.priceOriginal()),
				item.discountPercent(),
				item.imageUrl(),
				item.styleCode(),
				item.link(),
				0,
				null,
				null
		);
	}

	private Integer parsePrice(String priceStr) {
		if (priceStr == null || priceStr.isBlank()) {
			return null;
		}
		String digits = priceStr.replaceAll("[^0-9]", "");
		return digits.isEmpty() ? null : Integer.parseInt(digits);
	}
}
