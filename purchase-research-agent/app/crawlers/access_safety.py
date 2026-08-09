"""판매처 HTTP 응답과 예외를 우회 없이 안전한 수집 오류로 분류한다."""

from __future__ import annotations

from dataclasses import dataclass

import httpx


@dataclass(frozen=True)
class MerchantAccessError(Exception):
    """HTTP 상태에 대응하는 안전한 오류 코드와 재시도 가능 여부를 보관한다."""

    code: str
    message: str
    retryable: bool

    def __str__(self) -> str:
        """URL이나 응답 body 없이 오류 코드와 안전한 설명만 반환한다."""

        return f"{self.code}: {self.message}"


def ensure_success(response: httpx.Response, merchant: str) -> None:
    """판매처 응답을 상태별로 분류하고 2xx만 정상 처리한다.

    Args:
        response: redirect를 자동 추적하지 않고 받은 HTTP 응답이다.
        merchant: 로그와 오류에 사용할 판매처 식별자다.

    Raises:
        MerchantAccessError: redirect, 접근 차단, rate limit 또는 HTTP 실패인 경우다.
    """

    status = response.status_code
    if 200 <= status < 300:
        return
    if 300 <= status < 400:
        raise MerchantAccessError(
            "MERCHANT_REDIRECT_BLOCKED",
            f"{merchant} 응답 redirect를 자동 추적하지 않았습니다(HTTP {status})",
            False,
        )
    if status in {401, 403}:
        raise MerchantAccessError(
            "MERCHANT_ACCESS_BLOCKED",
            f"{merchant} 접근이 거부됐습니다(HTTP {status})",
            False,
        )
    if status == 429:
        raise MerchantAccessError(
            "MERCHANT_RATE_LIMITED",
            f"{merchant} 요청 제한 응답을 받았습니다(HTTP 429)",
            True,
        )
    if 500 <= status < 600:
        raise MerchantAccessError(
            "MERCHANT_TEMPORARY_FAILURE",
            f"{merchant} 5xx 일시 오류를 반환했습니다(HTTP {status})",
            True,
        )
    raise MerchantAccessError(
        "MERCHANT_HTTP_FAILURE",
        f"{merchant} 요청이 실패했습니다(HTTP {status})",
        False,
    )


def safe_exception_message(exc: Exception, merchant: str, operation: str) -> str:
    """외부 URL, 응답 body와 traceback을 제외한 수집 실패 설명을 만든다.

    Args:
        exc: 판매처 요청 또는 parsing 중 발생한 예외다.
        merchant: 판매처 식별자다.
        operation: 실패한 검색, 상세, 리뷰 같은 작업 이름이다.

    Returns:
        Queue retry 분류에 필요한 제한된 코드와 예외 종류만 포함한 문자열이다.
    """

    if isinstance(exc, MerchantAccessError):
        return str(exc)
    if isinstance(exc, httpx.TimeoutException):
        return f"MERCHANT_TIMEOUT: {merchant} {operation} timeout"
    if isinstance(exc, httpx.RequestError):
        return f"MERCHANT_CONNECTION_FAILURE: {merchant} {operation} connection 실패"
    return f"MERCHANT_RESPONSE_INVALID: {merchant} {operation} 실패({type(exc).__name__})"
