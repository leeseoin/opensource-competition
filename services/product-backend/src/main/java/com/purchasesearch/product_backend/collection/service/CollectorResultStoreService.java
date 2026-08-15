package com.purchasesearch.product_backend.collection.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.dto.CollectorResult;
import com.purchasesearch.product_backend.collection.dto.CollectorResult.Money;
import com.purchasesearch.product_backend.collection.dto.CollectorResult.Provenance;
import com.purchasesearch.product_backend.collection.entity.CollectionSearchContext;
import com.purchasesearch.product_backend.collection.exception.UnstorableCollectorResultException;
import com.purchasesearch.product_backend.collection.repository.CollectionSearchContextRepository;
import com.purchasesearch.product_backend.evidence.entity.Evidence;
import com.purchasesearch.product_backend.evidence.entity.ProductVerification;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.entity.OfferSnapshot;
import com.purchasesearch.product_backend.product.entity.Product;
import com.purchasesearch.product_backend.product.entity.ProductOption;
import com.purchasesearch.product_backend.product.embedding.ProductSearchDocumentsChanged;
import com.purchasesearch.product_backend.product.embedding.ProductSearchDocumentsChanged.ProductSearchDocument;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.repository.OfferSnapshotRepository;
import com.purchasesearch.product_backend.product.repository.ProductOptionRepository;
import com.purchasesearch.product_backend.product.repository.ProductRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

/**
 * CollectorResultStoreService는 검증된 CollectorResult의 상품, 가격 snapshot, 옵션 및
 * 근거를 하나의 transaction으로 저장한다.
 */
@Service
public class CollectorResultStoreService {

	private final Validator validator;
	private final CollectionSearchContextRepository collectionSearchContextRepository;
	private final ProductRepository productRepository;
	private final MerchantProductRepository merchantProductRepository;
	private final OfferSnapshotRepository offerSnapshotRepository;
	private final ProductOptionRepository productOptionRepository;
	private final EvidenceRepository evidenceRepository;
	private final ProductVerificationRepository productVerificationRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Collector 결과 저장에 필요한 validator와 repository를 연결한다.
	 *
	 * @param validator Bean Validation validator
	 * @param collectionSearchContextRepository 수집 검색어와 필터 repository
	 * @param productRepository 공통 상품 repository
	 * @param merchantProductRepository 판매처 상품 repository
	 * @param offerSnapshotRepository 가격과 재고 snapshot repository
	 * @param productOptionRepository 상품 옵션 repository
	 * @param evidenceRepository 공개 출처 근거 repository
	 * @param productVerificationRepository JSON/HTML 상품 검증 repository
	 * @param eventPublisher commit 뒤 embedding 갱신용 application event publisher
	 */
	public CollectorResultStoreService(
			Validator validator,
			CollectionSearchContextRepository collectionSearchContextRepository,
			ProductRepository productRepository,
			MerchantProductRepository merchantProductRepository,
			OfferSnapshotRepository offerSnapshotRepository,
			ProductOptionRepository productOptionRepository,
			EvidenceRepository evidenceRepository,
			ProductVerificationRepository productVerificationRepository,
			ApplicationEventPublisher eventPublisher) {
		this.validator = validator;
		this.collectionSearchContextRepository = collectionSearchContextRepository;
		this.productRepository = productRepository;
		this.merchantProductRepository = merchantProductRepository;
		this.offerSnapshotRepository = offerSnapshotRepository;
		this.productOptionRepository = productOptionRepository;
		this.evidenceRepository = evidenceRepository;
		this.productVerificationRepository = productVerificationRepository;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * CollectorResult 전체를 검증하고 상품별 snapshot을 저장한다.
	 *
	 * @param result Go Collector 공통 결과
	 * @return 저장된 상품, snapshot, 옵션 및 근거 개수
	 * @throws ConstraintViolationException Contract 제약을 만족하지 못한 경우
	 * @throws UnstorableCollectorResultException 저장할 수 없는 Collector 상태인 경우
	 */
	@Transactional
	public StoreReport store(CollectorResult result) {
		Set<ConstraintViolation<CollectorResult>> violations = validator.validate(result);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
		result.validateStorable();
		storeSearchContext(result);

		int optionCount = 0;
		int evidenceCount = 0;
		int verificationCount = 0;
		List<ProductSearchDocument> searchDocuments = new ArrayList<>();
		for (CollectorResult.Product product : result.products()) {
			MerchantProduct merchantProduct = upsertMerchantProduct(result, product);
			searchDocuments.add(new ProductSearchDocument(
					merchantProduct.getId(), buildSearchDocument(result, product)));
			OfferSnapshot previousSnapshot = offerSnapshotRepository
					.findFirstByMerchantProductOrderByCollectedAtDescIdDesc(merchantProduct)
					.orElse(null);
			OfferSnapshot snapshot = saveOfferSnapshot(result, product, merchantProduct);
			optionCount += saveOptions(product, snapshot, previousSnapshot);
			evidenceCount += saveEvidence(product, merchantProduct, snapshot);
			verificationCount += saveVerification(product, merchantProduct, snapshot);
		}
		eventPublisher.publishEvent(new ProductSearchDocumentsChanged(List.copyOf(searchDocuments)));

		return new StoreReport(
				result.products().size(),
				result.products().size(),
				optionCount,
				evidenceCount,
				verificationCount);
	}

	/** 상품명/브랜드/category/수집 검색어와 판매처만 사용해 embedding 검색 문서를 만든다. */
	private String buildSearchDocument(CollectorResult result, CollectorResult.Product product) {
		return String.join(" / ",
				product.name(),
				product.brand() == null ? "" : product.brand(),
				String.join(" / ", product.categoryPath()),
				result.query() == null ? "" : result.query(),
				result.merchant());
	}

	/**
	 * 상품에 JSON/HTML 비교 결과가 있으면 현재 snapshot에 연결해 저장한다.
	 *
	 * @param product 검증 결과를 포함할 수 있는 Collector 상품
	 * @param merchantProduct 검증 대상 판매처 상품
	 * @param snapshot 비교한 시점의 상품 snapshot
	 * @return 저장했으면 1, 검증 결과가 없으면 0
	 */
	private int saveVerification(
			CollectorResult.Product product,
			MerchantProduct merchantProduct,
			OfferSnapshot snapshot) {
		CollectorResult.Verification verification = product.verification();
		if (verification == null) {
			return 0;
		}
		var differences = verification.differences().stream()
				.map(difference -> {
					Map<String, Object> values = new java.util.LinkedHashMap<>();
					values.put("field", difference.field());
					values.put("jsonValue", difference.jsonValue());
					values.put("htmlValue", difference.htmlValue());
					return values;
				})
				.toList();
		productVerificationRepository.save(ProductVerification.create(
				merchantProduct,
				snapshot,
				verification.status(),
				verification.comparedFields(),
				differences,
				verification.jsonSourceUrl(),
				verification.htmlSourceUrl(),
				verification.verifiedAt()));
		return 1;
	}

	/**
	 * 검색 작업의 원본 검색어와 적용 필터를 requestId 기준으로 한 번만 저장한다.
	 * 같은 requestId가 다른 조건으로 재사용되면 기존 snapshot의 의미가 바뀌지 않도록 거절한다.
	 *
	 * @param result 검증을 통과한 Collector 결과
	 * @throws UnstorableCollectorResultException requestId가 다른 검색 조건으로 재사용된 경우
	 */
	private void storeSearchContext(CollectorResult result) {
		if (!"search".equals(result.operation())) {
			return;
		}
		Map<String, Object> filters = result.filters().toMap();
		collectionSearchContextRepository.findById(result.requestId())
				.ifPresentOrElse(existing -> {
					if (!existing.matches(result.merchant(), result.query(), filters)) {
						throw new UnstorableCollectorResultException(
								"같은 requestId를 다른 검색 조건으로 재사용할 수 없습니다.");
					}
				}, () -> collectionSearchContextRepository.save(CollectionSearchContext.create(
						result.requestId(),
						result.merchant(),
						result.query(),
						filters,
						result.collectedAt(),
						result.collectorVersion())));
	}

	/**
	 * 판매처와 외부 상품번호를 기준으로 기존 상품을 갱신하거나 새 상품을 생성한다.
	 *
	 * @param result Collector 결과 공통 정보
	 * @param product 저장할 상품
	 * @return 저장된 판매처 상품 entity
	 */
	private MerchantProduct upsertMerchantProduct(
			CollectorResult result,
			CollectorResult.Product product) {
		return merchantProductRepository
				.findByMerchantAndExternalId(result.merchant(), product.externalId())
				.map(existing -> {
					existing.getProduct().update(
							product.name(),
							product.brand(),
							product.categoryPath(),
							product.imageUrls());
					existing.updateCollection(product.productUrl(), product.provenance().collectedAt());
					return existing;
				})
				.orElseGet(() -> {
					Product savedProduct = productRepository.save(Product.create(
							product.name(),
							product.brand(),
							product.categoryPath(),
							product.imageUrls()));
					return merchantProductRepository.save(MerchantProduct.create(
							savedProduct,
							result.merchant(),
							product.externalId(),
							product.productUrl(),
							product.provenance().collectedAt()));
				});
	}

	/**
	 * 상품 가격, 배송, 재고 및 평점 snapshot을 추가한다.
	 *
	 * @param result Collector 결과 공통 정보
	 * @param product 저장할 상품
	 * @param merchantProduct 판매처 상품
	 * @return 저장된 offer snapshot
	 */
	private OfferSnapshot saveOfferSnapshot(
			CollectorResult result,
			CollectorResult.Product product,
			MerchantProduct merchantProduct) {
		Money price = product.price();
		Money shippingFee = product.shipping().fee();
		Provenance provenance = product.provenance();
		return offerSnapshotRepository.save(OfferSnapshot.create(
				merchantProduct,
				result.requestId(),
				price == null ? null : price.amount(),
				price == null ? null : price.currency(),
				shippingFee == null ? null : shippingFee.amount(),
				shippingFee == null ? null : shippingFee.currency(),
				product.shipping().summary(),
				product.stockStatus(),
				product.rating(),
				product.reviewCount(),
				provenance.sourceUrl(),
				provenance.collectedAt(),
				provenance.collectorVersion()));
	}

	/**
	 * 하나의 offer snapshot에 포함된 옵션을 추가한다.
	 *
	 * @param product 옵션을 포함한 상품
	 * @param snapshot 옵션이 속할 offer snapshot
	 * @param previousSnapshot 이번 수집 직전의 최신 snapshot 또는 null
	 * @return 저장하거나 이전 근거에서 이월한 옵션 개수
	 */
	private int saveOptions(
			CollectorResult.Product product,
			OfferSnapshot snapshot,
			OfferSnapshot previousSnapshot) {
		if (product.options().isEmpty() && previousSnapshot != null) {
			return carryForwardOptions(previousSnapshot, snapshot);
		}
		for (CollectorResult.Option option : product.options()) {
			Money price = option.price();
			Provenance provenance = option.provenance();
			productOptionRepository.save(ProductOption.create(
					snapshot,
					option.externalId(),
					option.label(),
					option.size(),
					option.color(),
					option.stockStatus(),
					price == null ? null : price.amount(),
					price == null ? null : price.currency(),
					provenance.sourceUrl(),
					provenance.collectedAt(),
					provenance.collectorVersion()));
		}
		return product.options().size();
	}

	/**
	 * 검색 응답이 옵션을 생략했을 때 마지막으로 확인된 옵션과 원래 provenance를 새 offer에 연결한다.
	 * 새 수집 사실로 위장하지 않도록 옵션의 collectedAt과 collectorVersion은 이전 값을 유지한다.
	 */
	private int carryForwardOptions(OfferSnapshot previousSnapshot, OfferSnapshot snapshot) {
		List<ProductOption> previousOptions = productOptionRepository
				.findAllByOfferSnapshotOrderById(previousSnapshot);
		for (ProductOption option : previousOptions) {
			productOptionRepository.save(ProductOption.create(
					snapshot,
					option.getExternalId(),
					option.getLabel(),
					option.getSize(),
					option.getColor(),
					option.getStockStatus(),
					option.getPriceAmount(),
					option.getCurrency(),
					option.getSourceUrl(),
					option.getCollectedAt(),
					option.getCollectorVersion()));
		}
		return previousOptions.size();
	}

	/**
	 * 상품과 배송 정보의 공개 출처를 snapshot에 연결한다.
	 *
	 * @param product 근거를 포함한 상품
	 * @param merchantProduct 판매처 상품
	 * @param snapshot 관련 offer snapshot
	 * @return 저장한 근거 개수
	 */
	private int saveEvidence(
			CollectorResult.Product product,
			MerchantProduct merchantProduct,
			OfferSnapshot snapshot) {
		saveEvidenceItem("product", product.provenance(), merchantProduct, snapshot);
		int savedCount = 1;
		if (!product.shipping().provenance().equals(product.provenance())) {
			saveEvidenceItem("shipping", product.shipping().provenance(), merchantProduct, snapshot);
			savedCount++;
		}
		return savedCount;
	}

	/**
	 * 하나의 provenance를 evidence 행으로 저장한다.
	 *
	 * @param evidenceType 근거 종류
	 * @param provenance 공개 출처 정보
	 * @param merchantProduct 판매처 상품
	 * @param snapshot 관련 offer snapshot
	 */
	private void saveEvidenceItem(
			String evidenceType,
			Provenance provenance,
			MerchantProduct merchantProduct,
			OfferSnapshot snapshot) {
		evidenceRepository.save(Evidence.create(
				merchantProduct,
				snapshot,
				evidenceType,
				provenance.sourceUrl(),
				provenance.collectedAt(),
				provenance.collectorVersion()));
	}

	/**
	 * StoreReport는 한 CollectorResult transaction에서 저장한 행 개수를 요약한다.
	 *
	 * @param productCount 처리한 상품 수
	 * @param snapshotCount 추가한 offer snapshot 수
	 * @param optionCount 추가한 옵션 수
	 * @param evidenceCount 추가한 근거 수
	 * @param verificationCount 추가한 JSON/HTML 검증 결과 수
	 */
	public record StoreReport(
			int productCount,
			int snapshotCount,
			int optionCount,
			int evidenceCount,
			int verificationCount) {
	}
}
