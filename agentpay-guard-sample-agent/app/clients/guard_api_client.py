from typing import Dict, Any
from app.clients.base_client import BaseClient

class GuardAPIClient(BaseClient):
    """
    Java Guard (Spring Boot) 서버와의 통신을 전담하는 클라이언트입니다.
    BaseClient를 상속받았으므로 공통 에러 처리나 세션 관리가 이미 적용되어 있습니다.
    """

    def __init__(self, base_url: str = "http://localhost:8080"):
        # 보통 Spring Boot 서버는 기본적으로 8080 포트에서 실행됩니다.
        super().__init__(base_url=base_url)

    async def validate_request(self, agent_id: str, intent: str, estimated_cost: float) -> Dict[str, Any]:
        """
        Java Guard에게 외부 API 사용 예산 및 정책 승인을 요청(기안서 발송)합니다.
        """
        payload = {
            "agentId": agent_id,
            "intent": intent,
            "estimatedCost": estimated_cost
        }

        # 부모 클래스(BaseClient)에 만들어둔 self.post()를 쓰면
        # 알아서 JSON으로 변환하고 에러 검사까지 다 해줍니다!
        print(f"👉 Java Guard({self.base_url})로 예산 승인 요청을 보냅니다...")
        return await self.post("/api/v1/guard/validate", data=payload)