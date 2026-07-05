from typing import Optional

from pydantic import BaseModel


class GuardDecision(BaseModel):
    decision: str  # ALLOW | REQUIRE_APPROVAL | DENY
    reason_code: Optional[str] = None
    reason_message: Optional[str] = None


class AgentInvokeResponse(BaseModel):
    status: str  # success | denied | pending_approval
    model_used: Optional[str] = None
    answer: Optional[str] = None
    estimated_cost: float
    actual_cost: Optional[float] = None  # 실제 Claude 호출이 일어난 경우(ALLOW)에만 채워진다
    guard_decision: GuardDecision
