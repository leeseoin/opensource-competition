from typing import Optional

from fastapi import Header, HTTPException, status

from app.core.config import get_settings


async def verify_api_key(x_api_key: Optional[str] = Header(default=None, alias="X-API-Key")) -> None:
    """
    간단한 API Key 검증. AGENT_API_KEY가 설정되지 않은 경우(PoC 기본값) 검증을 건너뛴다.
    """
    settings = get_settings()

    if not settings.agent_api_key:
        return

    if x_api_key != settings.agent_api_key:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="유효하지 않은 API Key입니다.")
