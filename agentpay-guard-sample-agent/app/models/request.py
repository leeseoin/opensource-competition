from typing import Optional

from pydantic import BaseModel, Field


class AgentInvokeRequest(BaseModel):
    prompt: str = Field(..., min_length=1, description="에이전트에게 전달할 사용자 프롬프트")
    agent_id: Optional[str] = Field(default=None, description="Guard에 등록된 agent 식별자. 미지정 시 기본값 사용")
    estimated_cost: Optional[float] = Field(
        default=None, gt=0, description="예상 비용(USD). 미지정 시 프롬프트 기반으로 자동 추정"
    )
