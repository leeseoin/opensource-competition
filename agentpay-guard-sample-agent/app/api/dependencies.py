from fastapi import Request

from app.clients.anthropic_client import AnthropicClient
from app.clients.guard_api_client import GuardAPIClient
from app.core.config import Settings, get_settings


def get_app_settings() -> Settings:
    return get_settings()


def get_guard_client(request: Request) -> GuardAPIClient:
    return request.app.state.guard_client


def get_ai_client(request: Request) -> AnthropicClient:
    return request.app.state.ai_client
