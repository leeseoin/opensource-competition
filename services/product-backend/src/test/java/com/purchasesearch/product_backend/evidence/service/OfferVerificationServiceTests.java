package com.purchasesearch.product_backend.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse.VerificationSummary;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskResponse;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionTaskPublisher;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.VerificationStatus;
import com.purchasesearch.product_backend.evidence.entity.OfferVerificationRequest;
import com.purchasesearch.product_backend.evidence.repository.OfferVerificationRequestRepository;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.MoneyView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ShippingView;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.SourceView;
import com.purchasesearch.product_backend.product.entity.MerchantProduct;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;
import com.purchasesearch.product_backend.product.service.ProductQueryService;

/** OfferVerificationServiceTests는 우선 수집 요청과 새 snapshot의 가격/재고 비교를 검증한다. */
class OfferVerificationServiceTests {

	private MerchantProductRepository merchantRepository;
	private ProductQueryService productQueryService;
	private CollectionTaskPublisher publisher;
	private CollectionJobService jobService;
	private OfferVerificationRequestRepository verificationRepository;
	private OfferVerificationService service;

	/** 각 테스트에 저장소와 Queue 경계 mock을 주입한다. */
	@BeforeEach
	void setUp() {
		merchantRepository = mock(MerchantProductRepository.class);
		productQueryService = mock(ProductQueryService.class);
		publisher = mock(CollectionTaskPublisher.class);
		jobService = mock(CollectionJobService.class);
		verificationRepository = mock(OfferVerificationRequestRepository.class);
		service = new OfferVerificationService(
				merchantRepository, productQueryService, publisher, jobService, verificationRepository);
	}

	/** 선택 상품명을 priority 100 검색으로 발행하고 기준 snapshot을 보존하는지 검증한다. */
	@Test
	void requestsPrioritySearchRefresh() {
		MerchantProduct merchantProduct = mock(MerchantProduct.class);
		when(merchantProduct.getId()).thenReturn(11L);
		when(merchantProduct.getMerchant()).thenReturn("abcmart");
		when(merchantRepository.findById(11L)).thenReturn(Optional.of(merchantProduct));
		when(productQueryService.findById(11L)).thenReturn(Optional.of(product(11, 69_000, "available", OffsetDateTime.now().minusDays(1))));
		when(publisher.publish(any())).thenReturn(
				new CollectionTaskResponse("task-1", "job-1", "QUEUED", "abcmart", "search", OffsetDateTime.now()));
		when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.request(11L);

		assertThat(response.status()).isEqualTo(VerificationStatus.QUEUED);
		assertThat(response.baseline().priceAmount()).isEqualTo(69_000);
		ArgumentCaptor<CollectionTaskRequest> request = ArgumentCaptor.forClass(CollectionTaskRequest.class);
		verify(publisher).publish(request.capture());
		assertThat(request.getValue().query()).isEqualTo("페니 로퍼");
		assertThat(request.getValue().priority()).isEqualTo(100);
	}

	/** 완료 job에서 더 최신 가격이 생기면 CHANGED와 price 차이를 반환하는지 검증한다. */
	@Test
	void reportsChangedPriceFromNewSnapshot() {
		UUID verificationId = UUID.randomUUID();
		OfferVerificationRequest request = mock(OfferVerificationRequest.class);
		MerchantProduct merchantProduct = mock(MerchantProduct.class);
		OffsetDateTime baselineAt = OffsetDateTime.now().minusHours(2);
		when(request.getVerificationId()).thenReturn(verificationId);
		when(request.getMerchantProduct()).thenReturn(merchantProduct);
		when(merchantProduct.getId()).thenReturn(11L);
		when(request.getJobId()).thenReturn("job-1");
		when(request.getBaselinePriceAmount()).thenReturn(69_000L);
		when(request.getBaselineCurrency()).thenReturn("KRW");
		when(request.getBaselineStockStatus()).thenReturn("available");
		when(request.getBaselineCollectedAt()).thenReturn(baselineAt);
		when(verificationRepository.findById(verificationId)).thenReturn(Optional.of(request));
		when(jobService.get("job-1")).thenReturn(job("COMPLETED"));
		when(productQueryService.findById(11L))
				.thenReturn(Optional.of(product(11, 72_000, "available", baselineAt.plusHours(1))));

		var response = service.get(verificationId);

		assertThat(response.status()).isEqualTo(VerificationStatus.CHANGED);
		assertThat(response.changes()).containsExactly("price");
		assertThat(response.latest().priceAmount()).isEqualTo(72_000);
	}

	/** 테스트용 최신 상품 snapshot을 생성한다. */
	private ProductSummary product(long id, long price, String stockStatus, OffsetDateTime collectedAt) {
		return new ProductSummary(
				id, "abcmart", "external", "페니 로퍼", "호킨스", List.of("신발", "구두"),
				"https://example.com/product", List.of(), new MoneyView(price, "KRW"),
				new ShippingView(null, null), stockStatus, null, null, List.of(),
				new SourceView("https://example.com/source", collectedAt, "test"));
	}

	/** 테스트용 수집 job 상태 응답을 생성한다. */
	private CollectionJobResponse job(String status) {
		return new CollectionJobResponse(
				"job-1", status, "abcmart", "search", "페니 로퍼", 1, 1, 1,
				0, 0, 1, 0, 0, 1,
				new VerificationSummary(0, 0, 0, 0, 0, 0, 0),
				OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now(), List.of());
	}
}
