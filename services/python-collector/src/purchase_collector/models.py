"""판매처 Adapter와 대량 수집 실행기가 공유하는 자료형을 정의한다."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any


@dataclass(slots=True)
class PageResult:
    """한 판매처 페이지에서 변환한 상품과 다음 페이지 정보를 보관한다.

    Args:
        products: 공통 비교 계약으로 변환하기 전 또는 변환한 상품 목록이다.
        has_next: 판매처 응답이 알려준 다음 페이지 존재 여부다.
        total_count: 판매처가 제공한 검색 결과 전체 수다.
    """

    products: list[dict[str, Any]]
    has_next: bool
    total_count: int | None


@dataclass(slots=True)
class CollectionConfig:
    """한 판매처 수집 실행의 안전 상한과 저장 위치를 정의한다.

    최대 상품 수는 10,000으로 제한하며 요청 예산과 시간 상한은 별도로 적용한다.
    """

    merchant: str
    queries: list[str]
    output_dir: Path
    max_items: int = 100
    page_size: int = 50
    request_budget: int = 10
    min_interval_seconds: float = 1.0
    timeout_seconds: float = 15.0
    max_retries: int = 1
    max_wall_seconds: float = 3_600.0
    resume: bool = False

    def validate(self) -> None:
        """설정이 프로젝트의 수집 안전 상한 안에 있는지 검사한다.

        Raises:
            ValueError: 판매처/검색어/수량/시간 설정이 허용 범위를 벗어난 경우다.
        """

        if self.merchant not in {"abcmart", "29cm"}:
            raise ValueError("merchant는 abcmart 또는 29cm여야 합니다")
        if not self.queries or any(not value.strip() for value in self.queries):
            raise ValueError("비어 있지 않은 query가 하나 이상 필요합니다")
        if not 1 <= self.max_items <= 10_000:
            raise ValueError("max_items는 1 이상 10000 이하여야 합니다")
        if not 1 <= self.page_size <= 100:
            raise ValueError("page_size는 1 이상 100 이하여야 합니다")
        if self.request_budget < 1:
            raise ValueError("request_budget은 1 이상이어야 합니다")
        if self.min_interval_seconds < 0:
            raise ValueError("min_interval_seconds는 0 이상이어야 합니다")
        if self.timeout_seconds <= 0 or self.max_wall_seconds <= 0:
            raise ValueError("timeout과 wall time은 0보다 커야 합니다")
        if not 0 <= self.max_retries <= 3:
            raise ValueError("max_retries는 0 이상 3 이하여야 합니다")


@dataclass(slots=True)
class CollectionStats:
    """재현 가능한 수집 결과와 성능 지표를 기록한다."""

    merchant: str
    target_count: int
    unique_count: int = 0
    received_count: int = 0
    duplicate_count: int = 0
    contract_pass_count: int = 0
    contract_fail_count: int = 0
    missing_field_count: int = 0
    request_count: int = 0
    error_count: int = 0
    http_429_count: int = 0
    wall_seconds: float = 0.0
    cpu_seconds: float = 0.0
    peak_memory_kib: int = 0
    stop_reason: str = ""
    checkpoint_resumed: bool = False
    errors: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        """JSON 요약 파일에 저장할 사전으로 변환한다.

        Returns:
            dataclass의 모든 수집 및 성능 지표를 담은 사전이다.
        """

        return asdict(self)


class MerchantRequestError(RuntimeError):
    """판매처 요청 실패의 HTTP 상태와 재시도 가능 여부를 전달한다."""

    def __init__(self, message: str, *, status_code: int | None = None, retryable: bool = False):
        """수집 실행기가 안전 중단과 제한된 재시도를 판단할 오류를 만든다."""

        super().__init__(message)
        self.status_code = status_code
        self.retryable = retryable
