package com.purchasesearch.product_backend.product.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.purchasesearch.product_backend.product.dto.ProductSearchResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.MoneyView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.OptionView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ShippingView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.SourceView;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;
import com.purchasesearch.product_backend.product.entity.ProductOption;
import com.purchasesearch.product_backend.product.embedding.ProductEmbeddingService;
import com.purchasesearch.product_backend.product.embedding.ProductEmbeddingService.QueryEmbedding;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository.CandidateRetrievalSignalProjection;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;

/**
 * ProductQueryService는 저장된 판매처 상품에 최신 offer snapshot과 옵션을 결합해 조회한다.
 */
@Service
public class ProductQueryService {

	/** CandidateSearchResult는 후보 상품과 후보별 원시 검색 점수를 함께 보존한다. */
	public record CandidateSearchResult(
			ProductSearchResponse response,
			Map<Long, RetrievalSignal> signals) {
	}

	/** RetrievalSignal은 가중치 적용 전 keyword/vector 점수를 표현한다. */
	public record RetrievalSignal(double keywordScore, Double semanticScore) {
	}

	private final MerchantProductRepository merchantProductRepository;
	private final OfferSnapshotRepository offerSnapshotRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductEmbeddingService productEmbeddingService;

	/**
	 * 상품 조회에 필요한 repository를 연결한다.
	 *
	 * @param merchantProductRepository 판매처 상품 repository
	 * @param offerSnapshotRepository offer snapshot repository
	 * @param productOptionRepository 상품 옵션 repository
	 * @param productEmbeddingService 선택적 질문 embedding과 전문 검색 fallback 서비스
	 */
	public ProductQueryService(
			MerchantProductRepository merchantProductRepository,
			OfferSnapshotRepository offerSnapshotRepository,
			ProductOptionRepository productOptionRepository,
			ProductEmbeddingService productEmbeddingService) {
		this.merchantProductRepository = merchantProductRepository;
		this.offerSnapshotRepository = offerSnapshotRepository;
		this.productOptionRepository = productOptionRepository;
		this.productEmbeddingService = productEmbeddingService;
	}

	/**
	 * 판매처와 상품명, 브랜드 또는 수집 요청 검색어로 최신 상품 정보를 검색한다.
	 *
	 * @param merchant 선택 판매처
	 * @param query 선택 검색어
	 * @param limit 최대 반환 상품 수
	 * @return 전체 개수와 다음 결과 여부를 포함한 상품 목록
	 */
	@Transactional(readOnly = true)
	public ProductSearchResponse search(String merchant, String query, int limit) {
		String normalizedMerchant = normalize(merchant);
		String normalizedQuery = normalize(query);
		Page<MerchantProduct> page = merchantProductRepository.search(
				normalizedMerchant,
				normalizedQuery,
				PageRequest.of(0, limit));
		List<ProductSummary> products = page.getContent().stream()
				.map(this::toSummary)
				.toList();
		return new ProductSearchResponse(page.getTotalElements(), page.hasNext(), products);
	}

	/**
	 * 사용자 확인 가격, 사이즈, 색상과 판매 중 재고를 최신 snapshot에 적용해 검색한다.
	 *
	 * @param merchant 선택 판매처
	 * @param query 상품 검색어
	 * @param minPrice 최소 가격
	 * @param maxPrice 최대 가격
	 * @param currency 가격 통화
	 * @param sizesCsv 검색할 사이즈 목록
	 * @param colorsCsv 검색할 색상 목록
	 * @param limit 최대 후보 수
	 * @return 조건과 일치하는 전체 개수, 후보 목록과 원시 검색 점수
	 */
	@Transactional(readOnly = true)
	public CandidateSearchResult searchCandidates(
			String merchant,
			String query,
			Long minPrice,
			Long maxPrice,
			String currency,
			String sizesCsv,
			String colorsCsv,
			int limit) {
		String normalizedQuery = normalize(query);
		QueryEmbedding embedding = normalizedQuery == null
				? null
				: productEmbeddingService.embedQuery(normalizedQuery).orElse(null);
		Page<MerchantProduct> page = merchantProductRepository.searchCandidates(
				normalize(merchant),
				normalizedQuery,
				minPrice,
				maxPrice,
				normalize(currency),
				normalize(sizesCsv),
				normalize(colorsCsv),
				true,
				embedding == null ? null : embedding.provider(),
				embedding == null ? null : embedding.model() + "@" + embedding.modelVersion(),
				embedding == null ? null : embedding.vectorLiteral(),
				PageRequest.of(0, limit));
		List<ProductSummary> products = page.getContent().stream()
				.map(this::toSummary)
				.toList();
		List<Long> candidateIds = products.stream().map(ProductSummary::id).toList();
		Map<Long, RetrievalSignal> signals = candidateIds.isEmpty()
				? Map.of()
				: merchantProductRepository.findCandidateRetrievalSignals(
						candidateIds,
						normalizedQuery,
						embedding == null ? null : embedding.provider(),
						embedding == null ? null : embedding.model() + "@" + embedding.modelVersion(),
						embedding == null ? null : embedding.vectorLiteral()).stream()
						.collect(Collectors.toUnmodifiableMap(
								CandidateRetrievalSignalProjection::getCandidateId,
								projection -> new RetrievalSignal(
										projection.getKeywordScore(),
										projection.getSemanticScore())));
		return new CandidateSearchResult(
				new ProductSearchResponse(page.getTotalElements(), page.hasNext(), products),
				signals);
	}

	/**
	 * 빈 검색 조건을 repository가 처리할 수 있는 null로 변환한다.
	 *
	 * @param value 원본 검색 조건
	 * @return 공백을 제거한 값 또는 null
	 */
	private String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/**
	 * 판매처 상품에 최신 snapshot과 옵션을 결합한다.
	 *
	 * @param merchantProduct 판매처 상품
	 * @return API 상품 요약
	 * @throws IllegalStateException 저장된 상품에 offer snapshot이 없는 경우
	 */
	private ProductSummary toSummary(MerchantProduct merchantProduct) {
		OfferSnapshot snapshot = offerSnapshotRepository
				.findFirstByMerchantProductOrderByCollectedAtDescIdDesc(merchantProduct)
				.orElseThrow(() -> new IllegalStateException("판매처 상품에 offer snapshot이 없습니다."));
		List<OptionView> options = productOptionRepository
				.findAllByOfferSnapshotOrderById(snapshot)
				.stream()
				.map(this::toOption)
				.toList();

		return new ProductSummary(
				merchantProduct.getId(),
				merchantProduct.getMerchant(),
				merchantProduct.getExternalId(),
				merchantProduct.getProduct().getName(),
				merchantProduct.getProduct().getBrand(),
				merchantProduct.getProduct().getCategoryPath(),
				merchantProduct.getProductUrl(),
				merchantProduct.getProduct().getImageUrls(),
				toMoney(snapshot.getPriceAmount(), snapshot.getCurrency()),
				new ShippingView(
						toMoney(snapshot.getShippingFeeAmount(), snapshot.getShippingFeeCurrency()),
						snapshot.getShippingSummary()),
				snapshot.getStockStatus(),
				snapshot.getRating(),
				snapshot.getReviewCount(),
				options,
				new SourceView(
						snapshot.getSourceUrl(),
						snapshot.getCollectedAt(),
						snapshot.getCollectorVersion()));
	}

	/**
	 * 옵션 entity를 API 응답으로 변환한다.
	 *
	 * @param option 옵션 entity
	 * @return 옵션 응답
	 */
	private OptionView toOption(ProductOption option) {
		return new OptionView(
				option.getExternalId(),
				option.getLabel(),
				option.getSize(),
				option.getColor(),
				option.getStockStatus(),
				toMoney(option.getPriceAmount(), option.getCurrency()));
	}

	/**
	 * 금액이나 통화가 없는 nullable DB 값을 API 금액으로 변환한다.
	 *
	 * @param amount 금액
	 * @param currency 통화
	 * @return 두 값이 모두 있으면 금액 응답, 아니면 null
	 */
	private MoneyView toMoney(Long amount, String currency) {
		if (amount == null || currency == null) {
			return null;
		}
		return new MoneyView(amount, currency);
	}
}
