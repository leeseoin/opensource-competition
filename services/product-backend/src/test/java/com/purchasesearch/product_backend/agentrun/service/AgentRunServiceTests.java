package com.purchasesearch.product_backend.agentrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.purchasesearch.product_backend.agentrun.dto.StartAgentRunRequest;
import com.purchasesearch.product_backend.agentrun.entity.AgentRun;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunCollectionJob;
import com.purchasesearch.product_backend.agentrun.entity.AgentRunStatus;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunCollectionJobRepository;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunEventRepository;
import com.purchasesearch.product_backend.agentrun.repository.AgentRunRepository;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse.VerificationSummary;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionRefreshResponse.DataStatus;
import com.purchasesearch.product_backend.collection.exception.CollectionTaskPublishException;
import com.purchasesearch.product_backend.collection.service.CollectionJobService;
import com.purchasesearch.product_backend.collection.service.CollectionRefreshService;
import com.purchasesearch.product_backend.evidence.service.OfferVerificationService;
import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.dto.ProductSearchResponse.ProductSummary;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.ConditionPriority;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PriceCondition;
import com.purchasesearch.product_backend.research.dto.PurchaseCondition.PrioritizedText;
import com.purchasesearch.product_backend.research.dto.ResearchSessionResponse;
import com.purchasesearch.product_backend.research.entity.ResearchSession;
import com.purchasesearch.product_backend.research.entity.ResearchSessionStatus;
import com.purchasesearch.product_backend.research.repository.ResearchSessionRepository;
import com.purchasesearch.product_backend.research.service.ResearchSessionService;

/** AgentRunServiceTests는 DB 우선 검색, 조건부 수집과 idempotent 상태 전이를 검증한다. */
class AgentRunServiceTests {

	private AgentRunRepository runRepository;
	private AgentRunEventRepository eventRepository;
	private AgentRunCollectionJobRepository runJobRepository;
	private ResearchSessionRepository sessionRepository;
	private ResearchSessionService sessionService;
	private CollectionRefreshService refreshService;
	private CollectionJobService jobService;
	private OfferVerificationService verificationService;
	private AgentRunService service;
	private ResearchSession session;
	private UUID sessionId;
	private UUID runId;
	private final List<AgentRunCollectionJob> links = new ArrayList<>();

	/** 각 테스트에 격리된 저장소와 외부 경계 mock을 구성한다. */
	@BeforeEach
	void setUp() {
		runRepository = mock(AgentRunRepository.class);
		eventRepository = mock(AgentRunEventRepository.class);
		runJobRepository = mock(AgentRunCollectionJobRepository.class);
		sessionRepository = mock(ResearchSessionRepository.class);
		sessionService = mock(ResearchSessionService.class);
		refreshService = mock(CollectionRefreshService.class);
		jobService = mock(CollectionJobService.class);
		verificationService = mock(OfferVerificationService.class);
		service = new AgentRunService(
				runRepository, eventRepository, runJobRepository, sessionRepository, sessionService,
				refreshService, jobService, verificationService);

		sessionId = UUID.randomUUID();
		runId = UUID.randomUUID();
		session = mock(ResearchSession.class);
		when(session.getId()).thenReturn(sessionId);
		when(session.getStatus()).thenReturn(ResearchSessionStatus.CONFIRMED);
		when(session.getConditions()).thenReturn(conditions());
		when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
		when(runRepository.findByResearchSessionId(sessionId)).thenReturn(Optional.empty());
		when(runRepository.saveAndFlush(any(AgentRun.class))).thenAnswer(invocation -> {
			AgentRun run = invocation.getArgument(0);
			ReflectionTestUtils.setField(run, "runId", runId);
			return run;
		});
		when(eventRepository.countByRunRunId(runId)).thenReturn(0L, 1L, 2L, 3L, 4L);
		when(eventRepository.findAllByRunRunIdOrderBySequenceNoAsc(runId)).thenReturn(List.of());
		when(runJobRepository.save(any(AgentRunCollectionJob.class))).thenAnswer(invocation -> {
			AgentRunCollectionJob link = invocation.getArgument(0);
			links.add(link);
			return link;
		});
		when(runJobRepository.findAllByRunRunIdOrderByCreatedAtAsc(runId)).thenAnswer(invocation -> List.copyOf(links));
	}

	/** DB 후보가 있으면 수집을 요청하지 않고 즉시 READY가 되는지 검증한다. */
	@Test
	void becomesReadyWithoutCollectionWhenDatabaseHasCandidates() {
		when(sessionService.search(sessionId)).thenReturn(research(List.of(mock(ProductSummary.class))));

		var response = service.start(new StartAgentRunRequest(sessionId, List.of()));

		assertThat(response.status()).isEqualTo(AgentRunStatus.READY);
		assertThat(response.nextAction()).isEqualTo("SELECT_AND_VERIFY");
		verify(refreshService, never()).request(any());
	}

	/** DB 후보가 없고 데이터도 없으면 한 번만 수집을 요청하고 COLLECTING으로 전환하는지 검증한다. */
	@Test
	void requestsCollectionForMissingCandidates() {
		when(sessionService.search(sessionId)).thenReturn(research(List.of()));
		when(refreshService.request(any())).thenReturn(
				new CollectionRefreshResponse(DataStatus.MISSING, true, "job-1", "task-1", "QUEUED", null));
		when(jobService.get("job-1")).thenReturn(job("QUEUED"));

		var response = service.start(new StartAgentRunRequest(sessionId, List.of()));

		assertThat(response.status()).isEqualTo(AgentRunStatus.COLLECTING);
		assertThat(response.collectionJobs()).singleElement().satisfies(job -> {
			assertThat(job.jobId()).isEqualTo("job-1");
			assertThat(job.dataStatus()).isEqualTo("MISSING");
		});
		verify(refreshService).request(any());
	}

	/** Queue 요청 실패를 실행 손실 없이 FAILED 상태와 안전한 오류로 보존하는지 검증한다. */
	@Test
	void preservesCollectionRequestFailureAsTerminalRun() {
		when(sessionService.search(sessionId)).thenReturn(research(List.of()));
		when(refreshService.request(any())).thenThrow(new CollectionTaskPublishException("broker secret detail"));

		var response = service.start(new StartAgentRunRequest(sessionId, List.of()));

		assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
		assertThat(response.error().code()).isEqualTo("COLLECTION_REQUEST_FAILED");
		assertThat(response.error().message()).doesNotContain("secret");
	}

	/** 여러 판매처 중 하나의 Queue 요청이 실패해도 접수된 작업으로 조사를 계속하는지 검증한다. */
	@Test
	void continuesCollectionWhenOneMerchantPublishFails() {
		when(sessionService.search(sessionId)).thenReturn(research(List.of()));
		when(refreshService.request(any()))
				.thenReturn(new CollectionRefreshResponse(
						DataStatus.MISSING, true, "job-1", "task-1", "QUEUED", null))
				.thenThrow(new CollectionTaskPublishException("second merchant failed"));
		when(jobService.get("job-1")).thenReturn(job("QUEUED"));

		var response = service.start(new StartAgentRunRequest(sessionId, List.of("abcmart", "29cm")));

		assertThat(response.status()).isEqualTo(AgentRunStatus.COLLECTING);
		assertThat(response.error()).isNull();
		assertThat(response.collectionJobs()).singleElement()
				.extracting(job -> job.jobId())
				.isEqualTo("job-1");
	}

	/** 완료된 수집 뒤 재검색에도 후보가 없으면 추가 수집 없이 NO_RESULTS로 끝나는지 검증한다. */
	@Test
	void endsWithNoResultsAfterSinglePostCollectionSearch() {
		AgentRun run = AgentRun.start(session);
		ReflectionTestUtils.setField(run, "runId", runId);
		run.transitionTo(AgentRunStatus.COLLECTING);
		AgentRunCollectionJob link = AgentRunCollectionJob.create(run, "job-1", "abcmart", "MISSING");
		links.add(link);
		when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
		when(jobService.get("job-1")).thenReturn(job("COMPLETED"));
		when(sessionService.search(sessionId)).thenReturn(research(List.of()));

		var response = service.advance(runId);

		assertThat(response.status()).isEqualTo(AgentRunStatus.NO_RESULTS);
		assertThat(response.nextAction()).isEqualTo("NONE");
		verify(refreshService, never()).request(any());
	}

	/** 테스트용 확정 구매 조건을 생성한다. */
	private PurchaseCondition conditions() {
		return new PurchaseCondition(
				new PrioritizedText("구두", ConditionPriority.required), List.of(),
				new PriceCondition(null, 100_000L, "KRW", ConditionPriority.required),
				List.of(), List.of(), List.of(), null, List.of(), List.of(), 0.95, true);
	}

	/** 후보 목록만 달리하는 조사 세션 응답을 생성한다. */
	private ResearchSessionResponse research(List<ProductSummary> candidates) {
		ProductCandidateResponse result = new ProductCandidateResponse(
				"구두 찾아줘", "구두", candidates.size(), false, candidates, List.of(), List.of());
		return new ResearchSessionResponse(
				sessionId, "구두 찾아줘", "codex", "purchase-research-agent",
				ResearchSessionStatus.CONFIRMED, conditions(), OffsetDateTime.now(), result);
	}

	/** 테스트용 수집 job 상태를 생성한다. */
	private CollectionJobResponse job(String status) {
		return new CollectionJobResponse(
				"job-1", status, "abcmart", "search", "구두", 1, 1, 1,
				0, 0, "COMPLETED".equals(status) ? 1 : 0, 0, 0, 0,
				new VerificationSummary(0, 0, 0, 0, 0, 0, 0),
				OffsetDateTime.now(), "COMPLETED".equals(status) ? OffsetDateTime.now() : null, List.of());
	}
}
