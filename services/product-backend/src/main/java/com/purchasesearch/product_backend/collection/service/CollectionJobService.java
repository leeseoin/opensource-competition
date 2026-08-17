package com.purchasesearch.product_backend.collection.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.purchasesearch.product_backend.collection.dto.BulkCollectionTaskRequest;
import com.purchasesearch.product_backend.collection.dto.CollectionJobListResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionJobListResponse.CollectionJobSummary;
import com.purchasesearch.product_backend.collection.dto.CollectionJobRequestSnapshot;
import com.purchasesearch.product_backend.collection.dto.CollectionJobResponse;
import com.purchasesearch.product_backend.collection.dto.CollectionResultEnvelope;
import com.purchasesearch.product_backend.collection.dto.CollectionTaskMessage;
import com.purchasesearch.product_backend.collection.entity.CollectionJob;
import com.purchasesearch.product_backend.collection.entity.CollectionTask;
import com.purchasesearch.product_backend.collection.exception.CollectionJobNotFoundException;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionResultMessageException;
import com.purchasesearch.product_backend.collection.exception.InvalidCollectionTaskException;
import com.purchasesearch.product_backend.collection.repository.CollectionJobRepository;
import com.purchasesearch.product_backend.collection.repository.CollectionTaskRepository;

/**
 * CollectionJobService는 Queue 발행 전 작업 등록부터 결과 수신 후 job 집계까지 관리한다.
 */
@Service
public class CollectionJobService {

	private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "PARTIAL");
	private static final Set<String> ACTIVE_TASK_STATUSES = Set.of("QUEUED", "RUNNING");

	private final CollectionJobRepository collectionJobRepository;
	private final CollectionTaskRepository collectionTaskRepository;

	/**
	 * 작업 상태 저장과 조회에 필요한 repository를 연결한다.
	 *
	 * @param collectionJobRepository 상위 job 저장소
	 * @param collectionTaskRepository 페이지 작업 저장소
	 */
	public CollectionJobService(
			CollectionJobRepository collectionJobRepository,
			CollectionTaskRepository collectionTaskRepository) {
		this.collectionJobRepository = collectionJobRepository;
		this.collectionTaskRepository = collectionTaskRepository;
	}

	/**
	 * 같은 판매처/검색 조건(idempotencyKey)의 작업이 아직 QUEUED 또는 RUNNING으로 진행 중인지 확인한다.
	 * 이미 종료된(SUCCESS/PARTIAL/FAILED/PUBLISH_FAILED) 과거 작업은 새 요청을 막지 않는다.
	 *
	 * @param idempotencyKey 판매처/검색 조건에서 계산한 멱등성 key
	 * @return 진행 중인 동일 조건 작업이 있으면 true
	 */
	@Transactional(readOnly = true)
	public boolean isDuplicateInFlight(String idempotencyKey) {
		return collectionTaskRepository.existsByIdempotencyKeyAndStatusIn(idempotencyKey, ACTIVE_TASK_STATUSES);
	}

	/**
	 * RabbitMQ 발행 전에 한 job과 모든 페이지 작업을 QUEUED 상태로 저장한다.
	 *
	 * @param messages 같은 jobId를 공유하는 페이지 작업 목록
	 * @throws IllegalArgumentException 작업 목록이 비어 있거나 서로 다른 jobId가 섞인 경우
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void register(List<CollectionTaskMessage> messages) {
		if (messages.isEmpty()) {
			throw new IllegalArgumentException("등록할 수집 작업이 없습니다.");
		}
		CollectionTaskMessage first = messages.getFirst();
		boolean sameJob = messages.stream().allMatch(message -> first.jobId().equals(message.jobId()));
		if (!sameJob) {
			throw new IllegalArgumentException("서로 다른 jobId의 작업을 함께 등록할 수 없습니다.");
		}
		int startPage = messages.stream().mapToInt(message -> message.payload().page()).min().orElseThrow();
		int endPage = messages.stream().mapToInt(message -> message.payload().page()).max().orElseThrow();
		CollectionJob job = collectionJobRepository.save(
				CollectionJob.queued(first, messages.size(), startPage, endPage));
		collectionTaskRepository.saveAll(messages.stream()
				.map(message -> CollectionTask.queued(job, message))
				.toList());
	}

	/**
	 * RabbitMQ에 보내지 못한 작업을 PUBLISH_FAILED로 기록하고 job 상태를 다시 계산한다.
	 *
	 * @param taskIds 실패한 작업 식별자 목록
	 * @param message 발행 실패 설명
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPublishFailed(List<String> taskIds, String message) {
		OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);
		String jobId = null;
		for (String taskId : taskIds) {
			CollectionTask task = collectionTaskRepository.findById(taskId).orElse(null);
			if (task == null) {
				continue;
			}
			task.failPublishing("RABBITMQ_PUBLISH_FAILED", message, failedAt);
			jobId = task.getJob().getJobId();
		}
		if (jobId != null) {
			refreshJobStatus(jobId);
		}
	}

	/**
	 * 성공 또는 부분 성공 Worker 결과를 추적 중인 페이지 작업에 반영한다.
	 * 등록 전의 과거 결과는 상품 저장 호환성을 위해 상태 갱신만 건너뛴다.
	 *
	 * @param envelope 검증된 Queue 결과 봉투
	 * @param productCount 저장 서비스가 처리한 상품 수
	 * @return 추적 중인 작업을 갱신했으면 true
	 */
	@Transactional
	public boolean recordCompleted(CollectionResultEnvelope envelope, int productCount) {
		CollectionTask task = collectionTaskRepository.findById(envelope.taskId()).orElse(null);
		if (task == null) {
			return false;
		}
		validateJobIdentity(task, envelope);
		task.complete(envelope, productCount);
		refreshJobStatus(task.getJob().getJobId());
		return true;
	}

	/**
	 * Worker가 소비를 시작한 작업을 RUNNING으로 기록하고 상위 job 진행 상태를 갱신한다.
	 * 중복 또는 최종 결과 뒤에 늦게 도착한 시작 이벤트는 기존 상태를 유지한다.
	 *
	 * @param envelope 검증된 running 상태 봉투
	 * @return 추적 중인 작업을 처음 RUNNING으로 전환했으면 true
	 */
	@Transactional
	public boolean recordRunning(CollectionResultEnvelope envelope) {
		CollectionTask task = collectionTaskRepository.findById(envelope.taskId()).orElse(null);
		if (task == null) {
			return false;
		}
		validateJobIdentity(task, envelope);
		boolean changed = task.start(envelope);
		if (changed) {
			refreshJobStatus(task.getJob().getJobId());
		}
		return changed;
	}

	/**
	 * 정상적인 failed Worker 결과를 추적 중인 페이지 작업에 반영한다.
	 *
	 * @param envelope 오류 정보가 포함된 Queue 결과 봉투
	 * @return 추적 중인 작업을 갱신했으면 true
	 */
	@Transactional
	public boolean recordFailed(CollectionResultEnvelope envelope) {
		CollectionTask task = collectionTaskRepository.findById(envelope.taskId()).orElse(null);
		if (task == null) {
			return false;
		}
		validateJobIdentity(task, envelope);
		task.fail(envelope);
		refreshJobStatus(task.getJob().getJobId());
		return true;
	}

	/**
	 * job 식별자로 전체 진행률과 페이지별 결과를 조회한다.
	 *
	 * @param jobId 조회할 수집 job 식별자
	 * @return 상품 수와 검증 집계를 포함한 현재 상태
	 * @throws CollectionJobNotFoundException job이 존재하지 않는 경우
	 */
	@Transactional(readOnly = true)
	public CollectionJobResponse get(String jobId) {
		CollectionJob job = collectionJobRepository.findById(jobId)
				.orElseThrow(() -> new CollectionJobNotFoundException(jobId));
		List<CollectionTask> tasks = collectionTaskRepository.findAllByJobJobIdOrderByPageAsc(jobId);
		return CollectionJobResponse.from(job, tasks);
	}

	/**
	 * 판매처/상태로 거르거나 전체 job을 최신 요청순으로 페이지네이션해 조회한다.
	 *
	 * @param merchant 선택 판매처
	 * @param status 선택 job 상태
	 * @param page 0부터 시작하는 페이지 번호
	 * @param size 페이지당 최대 job 수
	 * @return 요청 이력 화면이 쓸 성공률/상품 수 포함 job 목록
	 */
	@Transactional(readOnly = true)
	public CollectionJobListResponse list(String merchant, String status, int page, int size) {
		Page<CollectionJobRepository.JobSummaryProjection> result = collectionJobRepository.search(
				normalize(merchant), normalize(status), PageRequest.of(page, size));
		List<CollectionJobSummary> items = result.getContent().stream().map(this::toSummary).toList();
		return new CollectionJobListResponse(result.getTotalElements(), result.hasNext(), items);
	}

	/**
	 * 실패하거나 일부만 성공한 job을 저장된 원본 조건 그대로 다시 발행할 수 있게 요청으로 되돌린다.
	 *
	 * @param jobId 재실행할 job 식별자
	 * @return 등록 당시와 같은 merchant/query/페이지범위/limit/locale/currency/filters를 담은 재발행 요청
	 * @throws CollectionJobNotFoundException job이 존재하지 않는 경우
	 * @throws InvalidCollectionTaskException job이 FAILED 또는 PARTIAL 상태가 아닌 경우
	 */
	@Transactional(readOnly = true)
	public BulkCollectionTaskRequest toRetryRequest(String jobId) {
		CollectionJob job = collectionJobRepository.findById(jobId)
				.orElseThrow(() -> new CollectionJobNotFoundException(jobId));
		if (!RETRYABLE_STATUSES.contains(job.getStatus())) {
			throw new InvalidCollectionTaskException(
					"FAILED 또는 PARTIAL 상태의 job만 재실행할 수 있습니다. 현재 상태: " + job.getStatus());
		}
		CollectionJobRequestSnapshot snapshot = job.getRequestSnapshot() == null
				? CollectionJobRequestSnapshot.empty()
				: job.getRequestSnapshot();
		return new BulkCollectionTaskRequest(
				job.getMerchant(),
				job.getSearchQuery(),
				job.getStartPage(),
				job.getEndPage() - job.getStartPage() + 1,
				snapshot.limit(),
				snapshot.locale(),
				snapshot.currency(),
				snapshot.priority(),
				snapshot.maxAttempts(),
				snapshot.toRequestFilters());
	}

	/**
	 * job 요약 projection을 API 응답과 페이지/상품 단위 두 가지 성공률로 변환한다.
	 *
	 * @param projection repository가 계산한 job과 페이지 작업 집계
	 * @return taskSuccessRate(페이지 단위)와 verificationMatchRate(상품 단위)를 포함한 job 요약
	 */
	private CollectionJobSummary toSummary(CollectionJobRepository.JobSummaryProjection projection) {
		Double taskSuccessRate = projection.getTaskCount() == 0
				? null
				: projection.getSucceededTaskCount() / (double) projection.getTaskCount();
		Double verificationMatchRate = projection.getVerificationTotal() == 0
				? null
				: projection.getVerificationMatched() / (double) projection.getVerificationTotal();
		return new CollectionJobSummary(
				projection.getJobId(),
				projection.getMerchant(),
				projection.getSearchQuery(),
				projection.getStatus(),
				projection.getTaskCount(),
				projection.getSucceededTaskCount(),
				projection.getFailedTaskCount(),
				taskSuccessRate,
				projection.getProductCount(),
				projection.getVerificationTotal(),
				projection.getVerificationMatched(),
				verificationMatchRate,
				projection.getRequestedAt(),
				projection.getCompletedAt());
	}

	/**
	 * 빈 필터 조건을 repository가 처리할 수 있는 null로 변환한다.
	 *
	 * @param value 원본 필터 값
	 * @return 공백을 제거한 값 또는 null
	 */
	private String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/**
	 * 하위 작업의 종료 여부와 결과 조합으로 상위 job 상태를 계산한다.
	 *
	 * @param jobId 다시 계산할 job 식별자
	 */
	private void refreshJobStatus(String jobId) {
		CollectionJob job = collectionJobRepository.findById(jobId)
				.orElseThrow(() -> new CollectionJobNotFoundException(jobId));
		List<CollectionTask> tasks = collectionTaskRepository.findAllByJobJobIdOrderByPageAsc(jobId);
		long terminalCount = tasks.stream().filter(CollectionTask::isTerminal).count();
		if (terminalCount < job.getTotalTasks()) {
			boolean hasRunningTask = tasks.stream().anyMatch(task -> "RUNNING".equals(task.getStatus()));
			String activeStatus = hasRunningTask ? "RUNNING" : terminalCount == 0 ? "QUEUED" : "PROCESSING";
			job.updateStatus(activeStatus, null);
			return;
		}

		long successCount = tasks.stream().filter(task -> "SUCCESS".equals(task.getStatus())).count();
		long failedCount = tasks.stream()
				.filter(task -> "FAILED".equals(task.getStatus()) || "PUBLISH_FAILED".equals(task.getStatus()))
				.count();
		String status;
		if (successCount == job.getTotalTasks()) {
			status = "COMPLETED";
		} else if (failedCount == job.getTotalTasks()) {
			status = "FAILED";
		} else {
			status = "PARTIAL";
		}
		OffsetDateTime completedAt = tasks.stream()
				.map(CollectionTask::getCompletedAt)
				.filter(java.util.Objects::nonNull)
				.max(OffsetDateTime::compareTo)
				.orElse(OffsetDateTime.now(ZoneOffset.UTC));
		job.updateStatus(status, completedAt);
	}

	/**
	 * Queue 결과의 jobId가 등록 당시 task의 상위 job과 같은지 검사한다.
	 *
	 * @param task 추적 중인 페이지 작업
	 * @param envelope Python/Go Worker 결과 봉투
	 * @throws InvalidCollectionResultMessageException 다른 job의 결과가 섞인 경우
	 */
	private void validateJobIdentity(CollectionTask task, CollectionResultEnvelope envelope) {
		if (!task.getJob().getJobId().equals(envelope.jobId())) {
			throw new InvalidCollectionResultMessageException(
					"CollectionResult의 jobId가 등록된 작업과 일치하지 않습니다.");
		}
	}
}
