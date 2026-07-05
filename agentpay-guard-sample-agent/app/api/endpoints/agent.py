from fastapi import APIRouter, Depends

from app.api.dependencies import get_ai_client, get_app_settings, get_guard_client
from app.core.security import verify_api_key
from app.models.request import AgentInvokeRequest
from app.models.response import AgentInvokeResponse
from app.services import agent_logic

router = APIRouter()


@router.post(
    "/invoke",
    response_model=AgentInvokeResponse,
    summary="에이전트에게 프롬프트를 전달하고 Guard 승인 후 AI 응답을 받는다",
    dependencies=[Depends(verify_api_key)],
)
async def invoke_agent(
    payload: AgentInvokeRequest,
    settings=Depends(get_app_settings),
    guard_client=Depends(get_guard_client),
    ai_client=Depends(get_ai_client),
) -> AgentInvokeResponse:
    return await agent_logic.handle_invoke(payload, settings, guard_client, ai_client)
