from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    guard_api_base_url: str = "http://localhost:8080"

    anthropic_api_key: str = ""
    anthropic_model_default: str = "claude-haiku-4-5-20251001"
    anthropic_model_premium: str = "claude-sonnet-5"

    agent_server_host: str = "0.0.0.0"
    agent_server_port: int = 8001

    default_agent_id: str = "agent-sample-001"

    # 비워두면 인증 없이 열어둔다 (PoC 기본값). 값을 채우면 X-API-Key 헤더 검증이 활성화된다.
    agent_api_key: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
