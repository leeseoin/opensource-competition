import httpx

from app.clients.anthropic_client import AnthropicClient
from app.clients.guard_api_client import GuardAPIClient
from app.core.config import Settings
from app.core.exceptions import AIProviderError, GuardUnavailableError
from app.models.request import AgentInvokeRequest
from app.models.response import AgentInvokeResponse, GuardDecision
from app.services import llm_router


async def handle_invoke(
    payload: AgentInvokeRequest,
    settings: Settings,
    guard_client: GuardAPIClient,
    ai_client: AnthropicClient,
) -> AgentInvokeResponse:
    """
    사용자의 요청을 분석하고, Guard에 예산/정책 승인을 받은 뒤, 승인된 경우에만 AI 모델을 호출한다.
    """
    agent_id = payload.agent_id or settings.default_agent_id
    routing = llm_router.route(payload.prompt, settings)
    estimated_cost = payload.estimated_cost if payload.estimated_cost is not None else routing.estimated_cost

    try:
        guard_result = await guard_client.validate_request(
            agent_id=agent_id,
            intent=routing.intent,
            estimated_cost=estimated_cost,
        )
    except (httpx.RequestError, httpx.HTTPStatusError) as exc:
        raise GuardUnavailableError(str(exc)) from exc

    decision = GuardDecision(
        decision=guard_result.get("decision", "DENY"),
        reason_code=guard_result.get("reasonCode"),
        reason_message=guard_result.get("reasonMessage"),
    )

    if decision.decision == "DENY":
        return AgentInvokeResponse(status="denied", estimated_cost=estimated_cost, guard_decision=decision)

    if decision.decision == "REQUIRE_APPROVAL":
        return AgentInvokeResponse(status="pending_approval", estimated_cost=estimated_cost, guard_decision=decision)

    try:
        result = await ai_client.generate(prompt=payload.prompt, model=routing.model)
    except Exception as exc:
        raise AIProviderError(str(exc)) from exc

    actual_cost = llm_router.calculate_actual_cost(routing.model, result.input_tokens, result.output_tokens)

    return AgentInvokeResponse(
        status="success",
        model_used=routing.model,
        answer=result.text,
        estimated_cost=estimated_cost,
        actual_cost=actual_cost,
        guard_decision=decision,
    )
