import httpx

from app.core.config import get_settings
from app.core.exceptions import GuardUnavailableError


class GuardAPIClient:
    def __init__(self):
        settings = get_settings()
        self._base_url = settings.guard_api_base_url
        self._timeout = 30

    def _client(self) -> httpx.Client:
        return httpx.Client(base_url=self._base_url, timeout=self._timeout)

    def fetch_intent(self, intent_id: str) -> dict:
        try:
            with self._client() as client:
                resp = client.get(f"/api/intents/{intent_id}")
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPError as e:
            raise GuardUnavailableError(str(e))

    def create_payment_request(self, payload: dict) -> dict:
        try:
            with self._client() as client:
                resp = client.post("/api/payment-requests", json=payload)
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPError as e:
            raise GuardUnavailableError(str(e))

    def evaluate(self, payment_request_id: str) -> dict:
        try:
            with self._client() as client:
                resp = client.post(f"/api/payment-requests/{payment_request_id}/evaluate")
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPError as e:
            raise GuardUnavailableError(str(e))

    def pay(self, payment_request_id: str) -> dict:
        try:
            with self._client() as client:
                resp = client.post(f"/api/payment-requests/{payment_request_id}/pay")
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPError as e:
            raise GuardUnavailableError(str(e))
