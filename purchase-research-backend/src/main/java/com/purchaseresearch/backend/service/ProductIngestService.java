package com.purchaseresearch.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchaseresearch.backend.domain.PriceHistory;
import com.purchaseresearch.backend.domain.Product;
import com.purchaseresearch.backend.domain.ProductOption;
import com.purchaseresearch.backend.domain.ProductReview;
import com.purchaseresearch.backend.dto.OptionsPayload;
import com.purchaseresearch.backend.dto.ProductBatchRequest;
import com.purchaseresearch.backend.dto.ProductBatchResponse;
import com.purchaseresearch.backend.dto.ProductPayload;
import com.purchaseresearch.backend.dto.ReviewPayload;
import com.purchaseresearch.backend.repository.PriceHistoryRepository;
import com.purchaseresearch.backend.repository.ProductOptionRepository;
import com.purchaseresearch.backend.repository.ProductRepository;
import com.purchaseresearch.backend.repository.ProductReviewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 배치 적재. 재수집 시 처리 순서(설계서 §5.2/§7.2):
 * 1) product는 최신값으로 UPDATE(없으면 INSERT)
 * 2) price_history에는 항상 새 row를 INSERT
 * 3) 옵션은 기존 것을 지우고 새로 채움
 * 4) 리뷰는 review_source_id 기준으로 이미 있으면 스킵(중복 방지, §7.3)
 *
 * 배치 하나(§6.4)는 부분 성공을 허용한다: 상품 하나가 실패해도 나머지는 계속 처리한다.
 * 단, 배치 전체가 하나의 트랜잭션으로 묶여 있어 실패한 상품의 "부분적으로 쓰인" 하위
 * 데이터(가격 이력/옵션 일부)까지 완전히 원자적으로 되돌아가지는 않는다 — 현재 범위에서는
 * 상품 단위 세이브포인트까지는 두지 않았다.
 */
@Service
@RequiredArgsConstructor
public class ProductIngestService {

	private final ProductRepository productRepository;
	private final PriceHistoryRepository priceHistoryRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductReviewRepository productReviewRepository;

	@Transactional
	public ProductBatchResponse ingest(ProductBatchRequest request) {
		List<ProductBatchResponse.FailureItem> failures = new ArrayList<>();
		int successCount = 0;
		LocalDateTime collectedAt = request.collectedAt() != null
				? request.collectedAt().toLocalDateTime()
				: LocalDateTime.now();

		for (ProductPayload payload : request.products()) {
			try {
				ingestOne(request.site(), payload, collectedAt);
				successCount++;
			} catch (Exception e) {
				failures.add(new ProductBatchResponse.FailureItem(payload.sourceProductId(), e.getMessage()));
			}
		}

		int total = request.products().size();
		return new ProductBatchResponse(total, successCount, failures.size(), failures);
	}

	private void ingestOne(String site, ProductPayload payload, LocalDateTime collectedAt) {
		Product product = productRepository.findBySiteAndSourceProductId(site, payload.sourceProductId())
				.orElseGet(Product::new);

		product.setSite(site);
		product.setSourceProductId(payload.sourceProductId());
		product.setTitle(payload.title());
		product.setBrand(payload.brand());
		product.setPrice(payload.price());
		product.setPriceOriginal(payload.priceOriginal());
		product.setDiscountPercent(payload.discountPercent());
		product.setImageUrl(payload.imageUrl());
		product.setStyleCode(payload.styleCode());
		product.setLink(payload.link());
		product.setReviewCount(payload.reviewCount() != null ? payload.reviewCount() : 0);
		product.setCollectedAt(collectedAt);

		Product saved = productRepository.save(product);

		if (payload.price() != null) {
			PriceHistory history = new PriceHistory();
			history.setProductId(saved.getId());
			history.setPrice(payload.price());
			history.setPriceOriginal(payload.priceOriginal());
			history.setDiscountPercent(payload.discountPercent());
			history.setCollectedAt(collectedAt);
			priceHistoryRepository.save(history);
		}

		replaceOptions(saved.getId(), payload.options());
		insertNewReviews(saved.getId(), payload.reviews());
	}

	private void replaceOptions(Long productId, OptionsPayload options) {
		if (options == null) {
			return;
		}
		productOptionRepository.deleteByProductId(productId);

		List<ProductOption> rows = new ArrayList<>();
		for (String color : nullToEmpty(options.colors())) {
			rows.add(newOption(productId, ProductOption.OptionType.COLOR, color));
		}
		for (String size : nullToEmpty(options.sizes())) {
			rows.add(newOption(productId, ProductOption.OptionType.SIZE, size));
		}
		if (!rows.isEmpty()) {
			productOptionRepository.saveAll(rows);
		}
	}

	private void insertNewReviews(Long productId, List<ReviewPayload> reviews) {
		if (reviews == null) {
			return;
		}
		for (ReviewPayload payload : reviews) {
			boolean alreadyExists = productReviewRepository
					.existsByProductIdAndReviewSourceId(productId, payload.reviewSourceId());
			if (alreadyExists) {
				continue;
			}
			ProductReview review = new ProductReview();
			review.setProductId(productId);
			review.setReviewSourceId(payload.reviewSourceId());
			review.setContent(payload.content());
			review.setScore(payload.score());
			review.setReviewDate(payload.reviewDate());
			review.setSize(payload.size());
			productReviewRepository.save(review);
		}
	}

	private ProductOption newOption(Long productId, ProductOption.OptionType type, String value) {
		ProductOption option = new ProductOption();
		option.setProductId(productId);
		option.setOptionType(type);
		option.setOptionValue(value);
		return option;
	}

	private List<String> nullToEmpty(List<String> list) {
		return list != null ? list : List.of();
	}
}
