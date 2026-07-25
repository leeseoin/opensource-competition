"""상품 수집·저장 use case가 반환하는 안정적인 결과 DTO를 정의한다."""

from dataclasses import dataclass


@dataclass(frozen=True)
class CollectionSaveSummary:
    """한 Collector 응답에서 DB에 반영된 행 개수를 전달한다.

    생성·갱신된 판매처 상품 수, 추가된 가격 스냅샷·옵션·근거 수를 입력받아
    호출자에게 불변 결과로 반환한다. 음수 값 검증은 이 DTO의 책임이 아니다.
    """

    merchant_products: int
    offer_snapshots: int
    options: int
    evidence: int


@dataclass(frozen=True)
class CollectSearchOutcome:
    """Collector 요청 상태와 DB 저장 개수를 CLI/API에 전달한다."""

    request_id: str
    merchant: str
    status: str
    products_received: int
    total_count: int | None
    has_next: bool | None
    saved: CollectionSaveSummary
