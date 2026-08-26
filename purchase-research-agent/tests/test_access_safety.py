"""판매처 HTTP 접근 제한을 우회하지 않는 상태 분류와 오류 비노출을 검증한다."""

import unittest

import httpx

from app.crawlers.abcmart.detail_fetcher import _BROWSER_CFG
from app.crawlers.access_safety import MerchantAccessError, ensure_success, safe_exception_message


def _response(status: int) -> httpx.Response:
    """지정 상태와 공개 판매처 요청이 연결된 테스트 HTTP 응답을 만든다."""

    return httpx.Response(status, request=httpx.Request("GET", "https://merchant.example/items"))


class AccessSafetyTests(unittest.TestCase):
    """TLS, redirect, 접근 차단과 재시도 가능 상태의 안전 계약을 검증한다."""

    def test_accepts_only_success_status(self) -> None:
        """2xx 응답만 예외 없이 통과하는지 검증한다."""

        ensure_success(_response(200), "merchant")
        ensure_success(_response(204), "merchant")

    def test_blocks_redirect_without_following_it(self) -> None:
        """3xx 응답을 외부 Location 추적 없이 non-retryable 오류로 분류하는지 검증한다."""

        with self.assertRaises(MerchantAccessError) as raised:
            ensure_success(_response(302), "merchant")

        self.assertEqual(raised.exception.code, "MERCHANT_REDIRECT_BLOCKED")
        self.assertFalse(raised.exception.retryable)

    def test_does_not_retry_access_control(self) -> None:
        """401/403 접근 통제를 우회나 재시도 대상으로 분류하지 않는지 검증한다."""

        for status in (401, 403):
            with self.subTest(status=status), self.assertRaises(MerchantAccessError) as raised:
                ensure_success(_response(status), "merchant")
            self.assertEqual(raised.exception.code, "MERCHANT_ACCESS_BLOCKED")
            self.assertFalse(raised.exception.retryable)

    def test_retries_only_rate_limit_and_server_failure(self) -> None:
        """429와 5xx만 일시적인 HTTP 실패로 표시하는지 검증한다."""

        expected = {429: "MERCHANT_RATE_LIMITED", 503: "MERCHANT_TEMPORARY_FAILURE"}
        for status, code in expected.items():
            with self.subTest(status=status), self.assertRaises(MerchantAccessError) as raised:
                ensure_success(_response(status), "merchant")
            self.assertEqual(raised.exception.code, code)
            self.assertTrue(raised.exception.retryable)

    def test_hides_request_url_and_exception_text(self) -> None:
        """네트워크 오류의 URL/query와 원문을 Queue 경고에 포함하지 않는지 검증한다."""

        request = httpx.Request("GET", "https://merchant.example/items?token=sensitive")
        error = httpx.ConnectError("credential-like-detail", request=request)

        message = safe_exception_message(error, "merchant", "검색")

        self.assertIn("connection", message)
        self.assertNotIn("sensitive", message)
        self.assertNotIn("credential-like-detail", message)

    def test_browser_tls_errors_are_not_ignored(self) -> None:
        """ABC마트 상세 browser가 인증서 오류를 무시하지 않도록 설정됐는지 검증한다."""

        self.assertFalse(_BROWSER_CFG.ignore_https_errors)


if __name__ == "__main__":
    unittest.main()
