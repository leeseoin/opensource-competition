"""
로컬 테스트용 임시 Mock Guard 서버.

agentpay-guard-api-server(Spring Boot)에 /api/v1/guard/validate가 아직 구현되어
있지 않아서, sample-agent의 ALLOW -> Anthropic 호출 경로까지 로컬에서 테스트하기
위한 스텁이다. 실제 Spring Guard 서버가 /api/v1/guard/validate를 구현하면 이 파일은
지워도 된다.

실행:
    python dev/mock_guard_server.py
"""

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Mock Guard Server (dev only)")


class ValidateRequest(BaseModel):
    agentId: str
    intent: str
    estimatedCost: float


@app.post("/api/v1/guard/validate")
async def validate(payload: ValidateRequest) -> dict:
    cost = payload.estimatedCost

    if cost >= 0.01:
        return {
            "decision": "DENY",
            "reasonCode": "BUDGET_EXCEEDED",
            "reasonMessage": f"예상 비용 {cost}가 허용 한도를 초과했습니다.",
        }

    if cost >= 0.001:
        return {
            "decision": "REQUIRE_APPROVAL",
            "reasonCode": "APPROVAL_THRESHOLD",
            "reasonMessage": f"예상 비용 {cost}는 승인이 필요합니다.",
        }

    return {
        "decision": "ALLOW",
        "reasonCode": "WITHIN_BUDGET",
        "reasonMessage": "예산 범위 내 요청입니다.",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8080)
