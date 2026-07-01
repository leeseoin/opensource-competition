import httpx
from typing import Any, Dict, Optional

class BaseClient:
    """
    모든 외부 API 통신 클라이언트의 부모가 되는 공통 클래스입니다.
    이곳에 타임아웃, 에러 처리, 세션 관리 등 공통 로직을 작성합니다.
    나중에 aiohttp로 변경할 때 이 파일만 수정하면 됩니다.
    """

    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url
        self.timeout = timeout

        # httpx의 비동기 클라이언트 세션을 미리 만들어 둡니다.
        # 이렇게 하면 매번 요청할 때마다 통신 통로를 새로 뚫지 않아도 되어 속도가 훨씬 빠릅니다.
        self.client = httpx.AsyncClient(
            base_url=self.base_url,
            timeout=self.timeout,
            headers={"Content-Type": "application/json"}
        )

    async def _request(self, method: str, endpoint: str, **kwargs) -> Any:
        """
        실제로 HTTP 요청을 보내고 에러를 잡는 내부 공통 메서드입니다.
        """
        try:
            # 외부 API로 요청을 쏘는 부분
            response = await self.client.request(method, endpoint, **kwargs)

            # HTTP 상태 코드가 400~500번대(에러)면 예외를 발생시킵니다.
            response.raise_for_status()

            return response.json()

        except httpx.HTTPStatusError as e:
            # 예: Java Guard에서 403 Forbidden(예산 초과) 등을 보냈을 때의 처리
            print(f"❌ API 요청 실패 (상태 코드: {e.response.status_code}) - {e.request.url}")
            # 필요하다면 여기서 커스텀 에러로 변환하여 던질 수 있습니다.
            raise e
        except httpx.RequestError as e:
            # 예: 인터넷이 끊겼거나 Java Guard 서버가 아예 꺼져있을 때의 처리
            print(f"❌ API 통신 오류 (네트워크 문제) - {e.request.url}")
            raise e

    # =================================================================
    # 아래는 자식 클래스들이 쉽게 가져다 쓸 수 있도록 만든 편의용 메서드들입니다.
    # =================================================================

    async def get(self, endpoint: str, params: Optional[Dict[str, Any]] = None) -> Any:
        return await self._request("GET", endpoint, params=params)

    async def post(self, endpoint: str, data: dict) -> Any:
        return await self._request("POST", endpoint, json=data)

    async def close(self):
        """
        앱이 종료될 때 통신 세션을 깔끔하게 닫아주는 역할을 합니다.
        """
        await self.client.aclose()