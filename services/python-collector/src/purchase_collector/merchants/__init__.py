"""지원 판매처 Adapter 생성 함수를 제공한다."""

from .abcmart import AbcMartAdapter
from .base import MerchantAdapter
from .twentyninecm import TwentyNineCmAdapter


def create_adapter(merchant: str) -> MerchantAdapter:
    """판매처 이름에 맞는 JSON Adapter를 생성한다.

    Args:
        merchant: `abcmart` 또는 `29cm` 판매처 식별자다.

    Returns:
        페이지 요청과 변환을 수행할 Adapter다.

    Raises:
        ValueError: 지원하지 않는 판매처인 경우다.
    """

    if merchant == "abcmart":
        return AbcMartAdapter()
    if merchant == "29cm":
        return TwentyNineCmAdapter()
    raise ValueError(f"지원하지 않는 판매처입니다: {merchant}")


__all__ = ["MerchantAdapter", "create_adapter"]
