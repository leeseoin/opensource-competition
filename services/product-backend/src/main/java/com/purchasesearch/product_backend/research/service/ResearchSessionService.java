package com.purchasesearch.product_backend.research.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.product.dto.ProductCandidateResponse;
import com.purchasesearch.product_backend.product.service.ProductCandidateService;
import com.purchasesearch.product_backend.research.dto.ConfirmResearchSessionRequest;
import com.purchasesearch.product_backend.research.dto.ResearchSessionRequest;
import com.purchasesearch.product_backend.research.dto.ResearchSessionResponse;
import com.purchasesearch.product_backend.research.entity.ResearchSession;
import com.purchasesearch.product_backend.research.entity.ResearchSessionStatus;
import com.purchasesearch.product_backend.research.exception.ResearchSessionException;
import com.purchasesearch.product_backend.research.repository.ResearchSessionRepository;

/** ResearchSessionService는 AI 조건 초안 저장과 사용자 확인 후 상품 검색을 관리한다. */
@Service
public class ResearchSessionService {

	private final ResearchSessionRepository repository;
	private final ProductCandidateService productCandidateService;
	private final PurchaseConditionResolver conditionResolver;

	/**
	 * 조사 세션 저장소와 상품 후보 검색을 연결한다.
	 *
	 * @param repository 조사 세션 저장소
	 * @param productCandidateService 상품 후보 검색 서비스
	 * @param conditionResolver 사용자 표현을 표준 조건으로 변환하는 서비스
	 */
	public ResearchSessionService(
			ResearchSessionRepository repository,
			ProductCandidateService productCandidateService,
			PurchaseConditionResolver conditionResolver) {
		this.repository = repository;
		this.productCandidateService = productCandidateService;
		this.conditionResolver = conditionResolver;
	}

	/**
	 * AI가 구조화한 조건을 DRAFT 상태로 저장한다.
	 *
	 * @param request AI 실행 결과
	 * @return 저장된 조사 세션
	 */
	@Transactional
	public ResearchSessionResponse create(ResearchSessionRequest request) {
		ResearchSession session = ResearchSession.draft(
				request.question(), request.runtime(), request.pluginId(),
				conditionResolver.resolveDraft(request.conditions()));
		return ResearchSessionResponse.from(repository.save(session), null);
	}

	/**
	 * 사용자 확인 조건을 저장하고 검색 가능한 상태로 전환한다.
	 *
	 * @param sessionId 조사 세션 식별자
	 * @param request 사용자가 확정한 조건
	 * @return 확정 상태의 조사 세션
	 * @throws ResearchSessionException 조사 세션이 없거나 AI 확인 질문이 남은 경우
	 */
	@Transactional
	public ResearchSessionResponse confirm(UUID sessionId, ConfirmResearchSessionRequest request) {
		ResearchSession session = repository.findById(sessionId)
				.orElseThrow(() -> new ResearchSessionException("조사 세션을 찾을 수 없습니다."));
		if (!request.conditions().missingConditions().isEmpty()) {
			throw new ResearchSessionException("확인하지 않은 구매 조건이 남아 있습니다.");
		}
		session.confirm(conditionResolver.resolveConfirmed(request.conditions()));
		return ResearchSessionResponse.from(session, null);
	}

	/**
	 * CONFIRMED 조사 세션의 조건으로 상품 후보를 검색한다.
	 *
	 * @param sessionId 조사 세션 식별자
	 * @return 확정 조건과 상품 후보 최대 3개
	 * @throws ResearchSessionException 세션이 없거나 아직 확인되지 않은 경우
	 */
	@Transactional(readOnly = true)
	public ResearchSessionResponse search(UUID sessionId) {
		ResearchSession session = repository.findById(sessionId)
				.orElseThrow(() -> new ResearchSessionException("조사 세션을 찾을 수 없습니다."));
		if (session.getStatus() != ResearchSessionStatus.CONFIRMED) {
			throw new ResearchSessionException("사용자가 구매 조건을 확인하지 않았습니다.");
		}
		ProductCandidateResponse result = productCandidateService.findCandidates(
				session.getQuestion(), session.getConditions());
		return ResearchSessionResponse.from(session, result);
	}
}
