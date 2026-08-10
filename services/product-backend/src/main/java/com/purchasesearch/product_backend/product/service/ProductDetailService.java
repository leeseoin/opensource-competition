package com.purchasesearch.product_backend.product.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.purchasesearch.product_backend.evidence.entity.Evidence;
import com.purchasesearch.product_backend.evidence.entity.ProductVerification;
import com.purchasesearch.product_backend.evidence.repository.EvidenceRepository;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.product.dto.ProductComparisonResponse;
import com.purchasesearch.product_backend.product.dto.ProductComparisonResponse.ComparisonField;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse.EvidenceView;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse.FreshnessStatus;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse.FreshnessView;
import com.purchasesearch.product_backend.product.dto.ProductDetailResponse.VerificationView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;

/** ProductDetailService는 상품 상세/근거/최신성과 여러 후보의 비교축을 구성한다. */
@Service
public class ProductDetailService {

	private final ProductQueryService productQueryService;
	private final EvidenceRepository evidenceRepository;
	private final ProductVerificationRepository verificationRepository;
	private final long staleAfterHours;

	/**
	 * @param productQueryService 최신 상품 조회 서비스
	 * @param evidenceRepository 공개 근거 저장소
	 * @param verificationRepository JSON/HTML 검증 저장소
	 * @param staleAfterHours offer 사실의 기본 만료 시간
	 */
	public ProductDetailService(
			ProductQueryService productQueryService,
			EvidenceRepository evidenceRepository,
			ProductVerificationRepository verificationRepository,
			@Value("${purchase-research.freshness.offer-ttl-hours:24}") long staleAfterHours) {
		this.productQueryService = productQueryService;
		this.evidenceRepository = evidenceRepository;
		this.verificationRepository = verificationRepository;
		this.staleAfterHours = Math.max(1, staleAfterHours);
	}

	/** @param productId 판매처 상품 ID @return 최신 사실과 근거 @throws ResponseStatusException 상품이 없을 때 */
	@Transactional(readOnly = true)
	public ProductDetailResponse getProduct(long productId) {
		ProductSummary product = productQueryService.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
		return new ProductDetailResponse(
				product,
				freshness(product),
				evidenceRepository.findAllByMerchantProductIdOrderByCollectedAtDescIdDesc(productId).stream()
						.map(this::toEvidence)
						.toList(),
				verificationRepository.findAllByMerchantProductIdOrderByVerifiedAtDescIdDesc(productId).stream()
						.map(this::toVerification)
						.toList());
	}

	/** @param productId 판매처 상품 ID @return 상품에 연결된 공개 근거만 반환 */
	@Transactional(readOnly = true)
	public List<EvidenceView> getEvidence(long productId) {
		productQueryService.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
		return evidenceRepository.findAllByMerchantProductIdOrderByCollectedAtDescIdDesc(productId).stream()
				.map(this::toEvidence)
				.toList();
	}

	/**
	 * @param productIds 비교할 서로 다른 상품 ID
	 * @return 상품 상세와 주요 비교축
	 * @throws ResponseStatusException 중복 ID나 없는 상품이 포함된 경우
	 */
	@Transactional(readOnly = true)
	public ProductComparisonResponse compare(List<Long> productIds) {
		List<Long> uniqueIds = productIds.stream().distinct().toList();
		if (uniqueIds.size() != productIds.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비교 상품 ID는 중복될 수 없습니다.");
		}
		List<ProductDetailResponse> products = uniqueIds.stream().map(this::getProduct).toList();
		return new ProductComparisonResponse(
				OffsetDateTime.now(),
				products,
				List.of(
						field("price", products, item -> item.product().price()),
						field("stockStatus", products, item -> item.product().stockStatus()),
						field("merchant", products, item -> item.product().merchant()),
						field("categoryPath", products, item -> item.product().categoryPath()),
						field("collectedAt", products, item -> item.product().source().collectedAt()),
						field("freshness", products, item -> item.freshness().status())));
	}

	/** 최신 수집 시각을 설정된 offer TTL과 비교한다. */
	private FreshnessView freshness(ProductSummary product) {
		if (product.source() == null || product.source().collectedAt() == null) {
			return new FreshnessView(FreshnessStatus.MISSING, Long.MAX_VALUE, staleAfterHours);
		}
		long ageHours = Math.max(0, Duration.between(product.source().collectedAt(), OffsetDateTime.now()).toHours());
		return new FreshnessView(
				ageHours <= staleAfterHours ? FreshnessStatus.FRESH : FreshnessStatus.STALE,
				ageHours,
				staleAfterHours);
	}

	/** 한 비교축의 상품별 값을 삽입 순서로 보존하고 동일 여부를 계산한다. */
	private ComparisonField field(
			String key,
			List<ProductDetailResponse> products,
			Function<ProductDetailResponse, Object> extractor) {
		Map<String, Object> values = new LinkedHashMap<>();
		products.forEach(item -> values.put(String.valueOf(item.product().id()), extractor.apply(item)));
		return new ComparisonField(key, Collections.unmodifiableMap(values),
				values.values().stream().distinct().count() <= 1);
	}

	/** Evidence entity를 외부 식별정보 없는 공개 근거 응답으로 변환한다. */
	private EvidenceView toEvidence(Evidence evidence) {
		return new EvidenceView(evidence.getEvidenceType(), evidence.getSourceUrl(),
				evidence.getCollectedAt(), evidence.getCollectorVersion());
	}

	/** ProductVerification entity를 사실 차이와 공개 출처만 포함한 응답으로 변환한다. */
	private VerificationView toVerification(ProductVerification verification) {
		return new VerificationView(
				verification.getStatus(), verification.getComparedFields(), verification.getDifferences(),
				verification.getJsonSourceUrl(), verification.getHtmlSourceUrl(), verification.getVerifiedAt());
	}
}
