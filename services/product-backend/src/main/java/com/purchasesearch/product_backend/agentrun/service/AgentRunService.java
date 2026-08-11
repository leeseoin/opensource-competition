package com.purchasesearch.product_backend.agentrun.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.agentrun.dto.AgentRunResponse;
import com.purchasesearch.product_backend.agentrun.dto.AgentRunResponse.CollectionJobView;
import com.purchasesearch.product_backend.agentrun.dto.AgentRunResponse.EventView;
import com.purchasesearch.product_backend.agentrun.dto.StartAgentRunRequest;
import com.purchasesearch.product_backend.agentrun.entity.AgentRun;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunCollectionJob;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunEvent;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunStatus;
import com.purchasesearch.product_backend.agentrun.exception.AgentRunException;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunCollectionJobRepository;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunEventRepository;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunRepository;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionRefreshService;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse;
import com.purchasesearch.product_backend.evidence.dto.OfferVerificationResponse.VerificationStatus;
import com.purchasesearch.product_backend.evidence.service.OfferVerificationService;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;
import com.purchasesearch.product_backend.research.dto.ResearchSessionResponse;
import com.purchasesearch.product_backend.research.entity.ResearchSession;
import com.purchasesearch.product_backend.research.entity.ResearchSessionStatus;
import com.purchasesearch.product_backend.research.repository.ResearchSessionRepository;
import com.purchasesearch.product_backend.research.service.ResearchSessionService;

/** AgentRunService는 DB 우선 검색부터 조건부 수집, 재검색과 선택 상품 재검증을 조율한다. */
@Service
public class AgentRunService {

	private static final Set<String> SUPPORTED_MERCHANTS = Set.of("abcmart", "29cm");
	private static final List<String> DEFAULT_MERCHANTS = List.of("abcmart");
	private static final Set<String> ACTIVE_JOB_STATUSES = Set.of("QUEUED", "RUNNING", "PROCESSING");

	private final AgentRunRepository runRepository;
	private final AgentRunEventRepository eventRepository;
	private final AgentRunCollectionJobRepository runJobRepository;
	private final ResearchSessionRepository researchSessionRepository;
	private final ResearchSessionService researchSessionService;
	private final CollectionRefreshService collectionRefreshService;
	private final CollectionJobService collectionJobService;
	private final OfferVerificationService offerVerificationService;

	/** 구매 조사 실행에 필요한 상태 저장소와 기존 검색/수집/재검증 서비스를 연결한다. */
	public AgentRunService(
			AgentRunRepository runRepository,
			AgentRunEventRepository eventRepository,
			AgentRunCollectionJobRepository runJobRepository,
			ResearchSessionRepository researchSessionRepository,
			ResearchSessionService researchSessionService,
			CollectionRefreshService collectionRefreshService,
			CollectionJobService collectionJobService,
			OfferVerificationService offerVerificationService) {
		this.runRepository = runRepository;
		this.eventRepository = eventRepository;
		this.runJobRepository = runJobRepository;
		this.researchSessionRepository = researchSessionRepository;
		this.researchSessionService = researchSessionService;
		this.collectionRefreshService = collectionRefreshService;
		this.collectionJobService = collectionJobService;
		this.offerVerificationService = offerVerificationService;
	}

	/**
	 * 확정 세션을 한 번만 실행으로 만들고 먼저 PostgreSQL 후보를 검색한다.
	 *
	 * @param request 조사 세션과 선택적 판매처 범위
	 * @return 생성했거나 이미 존재하는 실행의 현재 상태
	 */
	@Transactional
	public AgentRunResponse start(StartAgentRunRequest request) {
		ResearchSession session = researchSessionRepository.findByIdForUpdate(request.sessionId())
				.orElseThrow(() -> notFound("조사 세션을 찾을 수 없습니다."));
		AgentRun existing = runRepository.findByResearchSessionId(request.sessionId()).orElse(null);
		if (existing != null) {
			return response(existing);
		}
		if (session.getStatus() != ResearchSessionStatus.CONFIRMED) {
			throw conflict("AGENT_RUN_SESSION_NOT_CONFIRMED", "사용자가 구매 조건을 확인한 뒤 실행할 수 있습니다.");
		}
		List<String> merchants = resolveMerchants(session.getConditions(), request.merchants());

		AgentRun run = runRepository.saveAndFlush(AgentRun.start(session));
		append(run, "RUN_STARTED", "확정한 구매 조건으로 상품 조사를 시작했습니다.");
		ResearchSessionResponse research = researchSessionService.search(session.getId());
		append(run, "SEARCH_COMPLETED", "PostgreSQL에서 확인된 조건과 일치하는 후보를 검색했습니다.");
		if (hasCandidates(research)) {
			run.transitionTo(AgentRunStatus.READY);
			append(run, "RESEARCH_READY", "비교할 상품 후보와 근거를 준비했습니다.");
			return response(run, research, null);
		}

		try {
			requestCollection(run, session.getConditions(), merchants);
		} catch (CollectionTaskPublishException exception) {
			run.fail("COLLECTION_REQUEST_FAILED", "상품 수집 작업을 요청하지 못했습니다.");
			append(run, "COLLECTION_FAILED", "상품 수집 작업을 시작하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
		}
		return response(run, null, null);
	}

	/** @param runId 실행 ID @return 저장된 현재 상태와 최신 연결 작업 상태 */
	@Transactional(readOnly = true)
	public AgentRunResponse get(UUID runId) {
		return response(find(runId));
	}

	/**
	 * 수집 또는 재검증 job을 한 단계 확인하고 종료됐다면 다음 결정적 상태로 전환한다.
	 *
	 * @param runId 실행 ID
	 * @return 전진 후 현재 상태
	 */
	@Transactional
	public AgentRunResponse advance(UUID runId) {
		AgentRun run = runRepository.findByIdForUpdate(runId)
				.orElseThrow(() -> notFound("구매 조사 실행을 찾을 수 없습니다."));
		if (run.getStatus() == AgentRunStatus.COLLECTING) {
			return advanceCollection(run);
		}
		if (run.getStatus() == AgentRunStatus.VERIFYING) {
			return advanceVerification(run);
		}
		return response(run);
	}

	/**
	 * READY 후보 중 사용자가 선택한 판매처 상품의 최신 가격과 재고를 재검증한다.
	 *
	 * @param runId 실행 ID
	 * @param productId 선택한 판매처 상품 ID
	 * @return VERIFYING 상태
	 */
	@Transactional
	public AgentRunResponse verify(UUID runId, long productId) {
		AgentRun run = runRepository.findByIdForUpdate(runId)
				.orElseThrow(() -> notFound("구매 조사 실행을 찾을 수 없습니다."));
		if (run.getStatus() == AgentRunStatus.VERIFYING && run.getVerificationId() != null) {
			return response(run);
		}
		if (run.getStatus() != AgentRunStatus.READY) {
			throw conflict("AGENT_RUN_NOT_READY", "후보가 준비된 실행에서만 상품을 재검증할 수 있습니다.");
		}
		ResearchSessionResponse research = researchSessionService.search(run.getResearchSession().getId());
		if (!containsProduct(research, productId)) {
			throw conflict("AGENT_RUN_PRODUCT_NOT_CANDIDATE", "현재 구매 조사 후보에 포함된 상품만 재검증할 수 있습니다.");
		}
		OfferVerificationResponse verification = offerVerificationService.request(productId);
		run.beginVerification(verification.verificationId());
		append(run, "VERIFICATION_REQUESTED", "선택 상품의 최신 가격과 재고 확인을 요청했습니다.");
		return response(run, research, verification);
	}

	/** 수집 job이 모두 끝나면 후보를 한 번 재검색하고 결과와 실패를 구분한다. */
	private AgentRunResponse advanceCollection(AgentRun run) {
		List<AgentRunCollectionJob> links = runJobRepository.findAllByRunRunIdOrderByCreatedAtAsc(run.getRunId());
		List<CollectionJobResponse> jobs = links.stream().map(link -> collectionJobService.get(link.getJobId())).toList();
		if (jobs.stream().anyMatch(job -> ACTIVE_JOB_STATUSES.contains(job.status()))) {
			return response(run);
		}
		if (!jobs.isEmpty() && jobs.stream().allMatch(job -> "FAILED".equals(job.status()))) {
			run.fail("COLLECTION_FAILED", "필요한 상품 수집 작업이 모두 실패했습니다.");
			append(run, "COLLECTION_FAILED", "상품 수집에 실패했습니다. 작업 상태에서 원인을 확인할 수 있습니다.");
			return response(run);
		}

		run.transitionTo(AgentRunStatus.SEARCHING);
		append(run, "COLLECTION_COMPLETED", "상품 수집이 끝나 확인된 조건으로 후보를 다시 검색합니다.");
		ResearchSessionResponse research = researchSessionService.search(run.getResearchSession().getId());
		append(run, "SEARCH_COMPLETED", "새로 수집된 정보를 포함해 상품 후보를 다시 검색했습니다.");
		if (hasCandidates(research)) {
			run.transitionTo(AgentRunStatus.READY);
			append(run, "RESEARCH_READY", "비교할 상품 후보와 근거를 준비했습니다.");
		} else {
			run.transitionTo(AgentRunStatus.NO_RESULTS);
			append(run, "NO_RESULTS", "수집 후에도 확정한 조건을 만족하는 상품을 찾지 못했습니다.");
		}
		return response(run, research, null);
	}

	/** 재검증 job의 최종 비교 결과를 실행 종료 상태에 연결한다. */
	private AgentRunResponse advanceVerification(AgentRun run) {
		OfferVerificationResponse verification = offerVerificationService.get(run.getVerificationId());
		if (verification.status() == VerificationStatus.QUEUED
				|| verification.status() == VerificationStatus.RUNNING) {
			return response(run, researchSessionService.search(run.getResearchSession().getId()), verification);
		}
		if (verification.status() == VerificationStatus.FAILED) {
			run.fail("VERIFICATION_FAILED", "선택 상품의 구매 직전 재검증에 실패했습니다.");
			append(run, "VERIFICATION_FAILED", "선택 상품의 최신 가격과 재고를 확인하지 못했습니다.");
		} else {
			run.transitionTo(AgentRunStatus.COMPLETED);
			String type = verification.status() == VerificationStatus.CHANGED
					? "VERIFICATION_CHANGED" : "VERIFICATION_COMPLETED";
			append(run, type, "선택 상품의 구매 직전 가격과 재고 확인을 마쳤습니다.");
		}
		return response(run, researchSessionService.search(run.getResearchSession().getId()), verification);
	}

	/** 결과가 없을 때 freshness 정책을 재사용하고 실제 요청된 job만 실행에 연결한다. */
	private void requestCollection(AgentRun run, PurchaseCondition conditions, List<String> merchants) {
		String query = conditions.productType().effectiveValue();
		boolean requested = false;
		for (String merchant : merchants) {
			CollectionRefreshResponse refresh = collectionRefreshService.request(
					new CollectionRefreshRequest(merchant, query, false));
			if (refresh.collectionRequested()) {
				runJobRepository.save(AgentRunCollectionJob.create(
						run, refresh.jobId(), merchant, refresh.dataStatus().name()));
				requested = true;
			}
		}
		if (requested) {
			run.transitionTo(AgentRunStatus.COLLECTING);
			append(run, "COLLECTION_REQUESTED", "DB에 후보가 없어 필요한 판매처 상품 수집을 요청했습니다.");
		} else {
			run.transitionTo(AgentRunStatus.NO_RESULTS);
			append(run, "COLLECTION_SKIPPED", "최신 DB 범위에서 조건을 만족하는 상품을 찾지 못했습니다.");
		}
	}

	/** 판매처 조건을 우선하고 없으면 요청 범위 또는 안전한 기본 판매처를 반환한다. */
	private List<String> resolveMerchants(PurchaseCondition conditions, List<String> requested) {
		List<String> values = conditions.merchant() == null || conditions.merchant().isBlank()
				? requested.isEmpty() ? DEFAULT_MERCHANTS : requested
				: List.of(conditions.merchant());
		LinkedHashSet<String> unique = new LinkedHashSet<>(values);
		if (!SUPPORTED_MERCHANTS.containsAll(unique)) {
			throw conflict("AGENT_RUN_UNSUPPORTED_MERCHANT", "현재 Agent Run이 지원하지 않는 판매처가 포함됐습니다.");
		}
		return List.copyOf(unique);
	}

	/** 사건 순서를 계산해 현재 상태와 함께 저장한다. */
	private void append(AgentRun run, String type, String message) {
		int sequence = Math.toIntExact(eventRepository.countByRunRunId(run.getRunId()) + 1);
		eventRepository.save(AgentRunEvent.create(run, sequence, type, message));
	}

	/** 후보 목록이 비어 있지 않은지 검사한다. */
	private boolean hasCandidates(ResearchSessionResponse research) {
		return research.result() != null && !research.result().candidates().isEmpty();
	}

	/** 선택 상품이 현재 후보 또는 후보 상품군의 판매처 상품에 포함되는지 검사한다. */
	private boolean containsProduct(ResearchSessionResponse research, long productId) {
		if (research.result() == null) {
			return false;
		}
		boolean direct = research.result().candidates().stream().anyMatch(candidate -> candidate.id() == productId);
		if (direct) {
			return true;
		}
		return research.result().groups().stream()
				.flatMap(group -> group.listings().stream())
				.anyMatch(listing -> listing.product().id() == productId);
	}

	/** 실행과 연결된 현재 job, 사건, 후보와 재검증을 조합한다. */
	private AgentRunResponse response(AgentRun run) {
		ResearchSessionResponse research = switch (run.getStatus()) {
			case READY, VERIFYING, COMPLETED -> researchSessionService.search(run.getResearchSession().getId());
			default -> null;
		};
		OfferVerificationResponse verification = run.getVerificationId() == null
				? null : offerVerificationService.get(run.getVerificationId());
		return response(run, research, verification);
	}

	/** 미리 조회한 후보와 재검증을 이용해 중복 호출 없이 응답을 조합한다. */
	private AgentRunResponse response(
			AgentRun run, ResearchSessionResponse research, OfferVerificationResponse verification) {
		List<CollectionJobView> jobs = new ArrayList<>();
		for (AgentRunCollectionJob link : runJobRepository.findAllByRunRunIdOrderByCreatedAtAsc(run.getRunId())) {
			jobs.add(CollectionJobView.from(
					link.getMerchant(), link.getDataStatus(), collectionJobService.get(link.getJobId())));
		}
		List<EventView> events = eventRepository.findAllByRunRunIdOrderBySequenceNoAsc(run.getRunId()).stream()
				.map(EventView::from).toList();
		return AgentRunResponse.from(run, research, jobs, verification, events, nextAction(run.getStatus()));
	}

	/** 상태별로 Web과 MCP가 수행할 다음 행동을 안정적인 값으로 반환한다. */
	private String nextAction(AgentRunStatus status) {
		return switch (status) {
			case COLLECTING, VERIFYING -> "POLL_ADVANCE";
			case READY -> "SELECT_AND_VERIFY";
			case SEARCHING -> "POLL_ADVANCE";
			case COMPLETED, NO_RESULTS, FAILED -> "NONE";
		};
	}

	/** 실행 ID로 현재 상태를 찾는다. */
	private AgentRun find(UUID runId) {
		return runRepository.findById(runId)
				.orElseThrow(() -> notFound("구매 조사 실행을 찾을 수 없습니다."));
	}

	/** 찾을 수 없음 오류를 일관된 코드로 생성한다. */
	private AgentRunException notFound(String message) {
		return new AgentRunException(HttpStatus.NOT_FOUND, "AGENT_RUN_NOT_FOUND", message);
	}

	/** 허용되지 않은 입력 또는 상태 전이 오류를 생성한다. */
	private AgentRunException conflict(String code, String message) {
		return new AgentRunException(HttpStatus.CONFLICT, code, message);
	}
}
