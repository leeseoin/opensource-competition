package com.purchasesearch.product_backend.dashboard.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.ProductCounts;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.ProductCounts.MerchantCount;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.StatusCounts;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.VerificationCounts;
import com.purchasesearch.product_backend.dashboard.dto.DashboardSummaryResponse.Window;
import com.purchasesearch.product_backend.dashboard.exception.InvalidDashboardWindowException;
import com.purchasesearch.product_backend.evidence.repository.ProductVerificationRepository;
import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;

/**
 * DashboardService는 지정한 시간 창 안의 수집/검증 현황을 판매처와 상태별로 집계한다.
 */
@Service
public class DashboardService {

	private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

	private static final List<String> JOB_STATUSES =
			List.of("QUEUED", "RUNNING", "PROCESSING", "COMPLETED", "PARTIAL", "FAILED");
	private static final List<String> TASK_STATUSES =
			List.of("QUEUED", "RUNNING", "SUCCESS", "PARTIAL", "FAILED", "PUBLISH_FAILED");
	private static final List<String> VERIFICATION_STATUSES =
			List.of("PENDING", "MATCHED", "MISMATCH", "MISSING_IN_HTML", "MISSING_IN_JSON", "FAILED");

	private final CollectionJobRepository collectionJobRepository;
	private final CollectionTaskRepository collectionTaskRepository;
	private final MerchantProductRepository merchantProductRepository;
	private final ProductVerificationRepository productVerificationRepository;

	/**
	 * 집계에 필요한 repository를 연결한다.
	 *
	 * @param collectionJobRepository 수집 job 저장소
	 * @param collectionTaskRepository 페이지 작업 저장소
	 * @param merchantProductRepository 판매처 상품 저장소
	 * @param productVerificationRepository JSON/HTML 검증 결과 저장소
	 */
	public DashboardService(
			CollectionJobRepository collectionJobRepository,
			CollectionTaskRepository collectionTaskRepository,
			MerchantProductRepository merchantProductRepository,
			ProductVerificationRepository productVerificationRepository) {
		this.collectionJobRepository = collectionJobRepository;
		this.collectionTaskRepository = collectionTaskRepository;
		this.merchantProductRepository = merchantProductRepository;
		this.productVerificationRepository = productVerificationRepository;
	}

	/**
	 * 지정하거나 기본값(최근 24시간)인 시간 창으로 수집/검증 현황을 집계한다.
	 *
	 * @param since 창 시작 시각이며 없으면 until - 24시간
	 * @param until 창 끝 시각이며 없으면 현재 시각
	 * @return 판매처와 상태별로 집계한 대시보드 요약
	 * @throws InvalidDashboardWindowException since가 until보다 늦거나 같은 경우
	 */
	@Transactional(readOnly = true)
	public DashboardSummaryResponse summarize(OffsetDateTime since, OffsetDateTime until) {
		OffsetDateTime resolvedUntil = until != null ? until : OffsetDateTime.now();
		OffsetDateTime resolvedSince = since != null ? since : resolvedUntil.minus(DEFAULT_WINDOW);
		if (!resolvedSince.isBefore(resolvedUntil)) {
			throw new InvalidDashboardWindowException("since는 until보다 이전이어야 합니다");
		}

		Map<String, Long> jobCounts = new LinkedHashMap<>();
		collectionJobRepository.countByStatusInWindow(resolvedSince, resolvedUntil)
				.forEach(row -> jobCounts.put(row.getStatus(), row.getCount()));
		StatusCounts jobs = fillStatuses(JOB_STATUSES, jobCounts);

		Map<String, Long> taskCounts = new LinkedHashMap<>();
		collectionTaskRepository.countByStatusInWindow(resolvedSince, resolvedUntil)
				.forEach(row -> taskCounts.put(row.getStatus(), row.getCount()));
		StatusCounts tasks = fillStatuses(TASK_STATUSES, taskCounts);

		List<MerchantCount> byMerchant = merchantProductRepository
				.countByMerchantInWindow(resolvedSince, resolvedUntil).stream()
				.map(row -> new MerchantCount(row.getMerchant(), row.getCount()))
				.toList();
		long totalProducts = byMerchant.stream().mapToLong(MerchantCount::count).sum();
		ProductCounts products = new ProductCounts(totalProducts, byMerchant);

		Map<String, Long> verificationCounts = new LinkedHashMap<>();
		productVerificationRepository.countByStatusInWindow(resolvedSince, resolvedUntil)
				.forEach(row -> verificationCounts.put(row.getStatus(), row.getCount()));
		StatusCounts verificationStatusCounts = fillStatuses(VERIFICATION_STATUSES, verificationCounts);
		Double matchRate = verificationStatusCounts.total() == 0
				? null
				: verificationStatusCounts.byStatus().getOrDefault("MATCHED", 0L)
						/ (double) verificationStatusCounts.total();
		VerificationCounts verifications =
				new VerificationCounts(verificationStatusCounts.total(), verificationStatusCounts.byStatus(), matchRate);

		return new DashboardSummaryResponse(
				new Window(resolvedSince, resolvedUntil),
				jobs,
				tasks,
				products,
				verifications,
				OffsetDateTime.now());
	}

	/**
	 * DB에 존재하는 상태만 집계된 결과에 가능한 모든 상태를 0으로라도 채워 넣는다.
	 *
	 * @param knownStatuses 이 도메인이 가질 수 있는 전체 상태 목록
	 * @param counts DB에서 읽은 실제 상태별 개수
	 * @return 모든 상태를 포함한 개수와 합계
	 */
	private StatusCounts fillStatuses(List<String> knownStatuses, Map<String, Long> counts) {
		Map<String, Long> filled = new LinkedHashMap<>();
		for (String status : knownStatuses) {
			filled.put(status, counts.getOrDefault(status, 0L));
		}
		long total = filled.values().stream().mapToLong(Long::longValue).sum();
		return new StatusCounts(total, filled);
	}
}
