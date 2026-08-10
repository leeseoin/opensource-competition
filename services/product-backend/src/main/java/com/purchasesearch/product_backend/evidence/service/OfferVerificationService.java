package com.purchasesearch.product_backend.evidence.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionTaskPublisher;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.SnapshotView;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.VerificationStatus;
import com.purchasesearch.product_backend.evidence.entity.OfferVerificationRequest;
import com.purchasesearch.product_backend.evidence.repository.OfferVerificationRequestRepository;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.service.ProductQueryService;

/** OfferVerificationService는 선택 상품의 기준 snapshot과 우선 검색 수집 결과를 비교한다. */
@Service
public class OfferVerificationService {

	private static final String STRATEGY = "PRIORITY_SEARCH_REFRESH";
	private static final int VERIFICATION_LIMIT = 5;

	private final MerchantProductRepository merchantProductRepository;
	private final ProductQueryService productQueryService;
	private final CollectionTaskPublisher collectionTaskPublisher;
	private final CollectionJobService collectionJobService;
	private final OfferVerificationRequestRepository verificationRepository;

	/**
	 * @param merchantProductRepository 판매처 상품 조회 저장소
	 * @param productQueryService 최신 snapshot 조회 서비스
	 * @param collectionTaskPublisher 우선 수집 Queue 발행 서비스
	 * @param collectionJobService 수집 job 상태 서비스
	 * @param verificationRepository 기준 snapshot 저장소
	 */
	public OfferVerificationService(
			MerchantProductRepository merchantProductRepository,
			ProductQueryService productQueryService,
			CollectionTaskPublisher collectionTaskPublisher,
			CollectionJobService collectionJobService,
			OfferVerificationRequestRepository verificationRepository) {
		this.merchantProductRepository = merchantProductRepository;
		this.productQueryService = productQueryService;
		this.collectionTaskPublisher = collectionTaskPublisher;
		this.collectionJobService = collectionJobService;
		this.verificationRepository = verificationRepository;
	}

	/**
	 * 현재 크롤러 검색 계약으로 선택 상품명을 높은 우선순위로 다시 수집한다.
	 *
	 * @param productId 검증할 판매처 상품 ID
	 * @return QUEUED 재검증 요청
	 */
	public OfferVerificationResponse request(long productId) {
		MerchantProduct merchantProduct = merchantProductRepository.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
		ProductSummary baseline = productQueryService.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품 snapshot을 찾을 수 없습니다."));
		OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
		CollectionTaskResponse task = collectionTaskPublisher.publish(new CollectionTaskRequest(
				merchantProduct.getMerchant(),
				baseline.name(),
				1,
				VERIFICATION_LIMIT,
				"ko-KR",
				baseline.price() == null ? "KRW" : baseline.price().currency(),
				100,
				2,
				null));
		OfferVerificationRequest request = verificationRepository.save(
				OfferVerificationRequest.create(merchantProduct, baseline, task.jobId(), requestedAt));
		return response(request, VerificationStatus.QUEUED, null, List.of());
	}

	/**
	 * @param verificationId 재검증 요청 ID
	 * @return Queue 진행 상태 또는 최신 snapshot 비교 결과
	 */
	@Transactional(readOnly = true)
	public OfferVerificationResponse get(UUID verificationId) {
		OfferVerificationRequest request = verificationRepository.findById(verificationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재검증 요청을 찾을 수 없습니다."));
		CollectionJobResponse job = collectionJobService.get(request.getJobId());
		VerificationStatus activeStatus = activeStatus(job.status());
		if (activeStatus != null) {
			return response(request, activeStatus, null, List.of());
		}
		if ("FAILED".equals(job.status())) {
			return response(request, VerificationStatus.FAILED, null, List.of());
		}
		ProductSummary latest = productQueryService.findById(request.getMerchantProduct().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품 snapshot을 찾을 수 없습니다."));
		if (latest.source() == null || latest.source().collectedAt() == null
				|| !latest.source().collectedAt().isAfter(request.getBaselineCollectedAt())) {
			return response(request, VerificationStatus.NOT_FOUND, null, List.of());
		}
		List<String> changes = changes(request, latest);
		return response(request, changes.isEmpty() ? VerificationStatus.VERIFIED : VerificationStatus.CHANGED,
				snapshot(latest), changes);
	}

	/** 진행 중 job 상태를 재검증 상태로 변환하고 최종 상태면 null을 반환한다. */
	private VerificationStatus activeStatus(String jobStatus) {
		return switch (jobStatus) {
			case "QUEUED" -> VerificationStatus.QUEUED;
			case "RUNNING", "PROCESSING" -> VerificationStatus.RUNNING;
			default -> null;
		};
	}

	/** 기준과 최신 가격/통화/재고 차이를 사람이 읽을 수 있는 key로 반환한다. */
	private List<String> changes(OfferVerificationRequest request, ProductSummary latest) {
		List<String> changes = new ArrayList<>();
		Long latestPrice = latest.price() == null ? null : latest.price().amount();
		String latestCurrency = latest.price() == null ? null : latest.price().currency();
		if (!Objects.equals(request.getBaselinePriceAmount(), latestPrice)
				|| !Objects.equals(request.getBaselineCurrency(), latestCurrency)) {
			changes.add("price");
		}
		if (!Objects.equals(request.getBaselineStockStatus(), latest.stockStatus())) {
			changes.add("stockStatus");
		}
		return List.copyOf(changes);
	}

	/** 저장된 기준과 선택적 최신 snapshot을 공통 응답으로 변환한다. */
	private OfferVerificationResponse response(
			OfferVerificationRequest request,
			VerificationStatus status,
			SnapshotView latest,
			List<String> changes) {
		return new OfferVerificationResponse(
				request.getVerificationId(), request.getMerchantProduct().getId(), request.getJobId(), status,
				STRATEGY,
				new SnapshotView(request.getBaselinePriceAmount(), request.getBaselineCurrency(),
						request.getBaselineStockStatus(), request.getBaselineCollectedAt()),
				latest,
				changes);
	}

	/** ProductSummary의 가격/재고/수집 시각만 재검증 snapshot으로 축약한다. */
	private SnapshotView snapshot(ProductSummary product) {
		return new SnapshotView(
				product.price() == null ? null : product.price().amount(),
				product.price() == null ? null : product.price().currency(),
				product.stockStatus(),
				product.source().collectedAt());
	}
}
