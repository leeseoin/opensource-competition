"""Go Collector v1 검색 요청과 응답을 검증하는 Pydantic 모델을 정의한다."""

from datetime import datetime
from typing import Annotated, Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, StringConstraints, field_validator

Identifier = Annotated[str, StringConstraints(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$")]
MerchantName = Annotated[str, StringConstraints(min_length=1, max_length=64, pattern=r"^[a-z0-9][a-z0-9-]*$")]
CurrencyCode = Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")]
StockStatus = Literal["available", "low_stock", "out_of_stock", "unknown"]
CollectorStatus = Literal["success", "partial", "blocked", "unsupported", "temporarily_unavailable"]


class CollectorModel(BaseModel):
    """Collector 계약 모델의 공통 alias와 추가 필드 거부 규칙을 제공한다.

    JSON 객체를 입력받아 검증된 Python 객체를 출력한다. 계약에 없는 필드나 잘못된
    타입이 들어오면 Pydantic ValidationError를 발생시킨다.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class SearchFilters(CollectorModel):
    """가격, 카테고리, 옵션과 재고 검색 조건을 표현한다."""

    price_min: int | None = Field(default=None, alias="priceMin", ge=0)
    price_max: int | None = Field(default=None, alias="priceMax", ge=0)
    categories: list[str] = Field(default_factory=list, max_length=50)
    sizes: list[str] = Field(default_factory=list, max_length=50)
    colors: list[str] = Field(default_factory=list, max_length=50)
    in_stock_only: bool = Field(default=False, alias="inStockOnly")
    attributes: dict[str, str | int | float | bool | list[str]] = Field(default_factory=dict)

    @field_validator("price_max")
    @classmethod
    def validate_price_range(cls, price_max: int | None, info) -> int | None:
        """최대 가격이 최소 가격보다 작으면 요청 검증을 실패시킨다."""

        price_min = info.data.get("price_min")
        if price_min is not None and price_max is not None and price_min > price_max:
            raise ValueError("priceMin은 priceMax보다 클 수 없습니다")
        return price_max


class SearchRequest(CollectorModel):
    """Python Backend가 Go Collector에 전송할 검색 요청을 표현한다."""

    request_id: Identifier = Field(alias="requestId")
    merchant: MerchantName
    query: str = Field(min_length=1, max_length=200)
    requested_at: AwareDatetime = Field(alias="requestedAt")
    limit: int = Field(default=10, ge=1, le=50)
    locale: str = Field(default="ko-KR", pattern=r"^[a-z]{2}-[A-Z]{2}$")
    currency: CurrencyCode = "KRW"
    filters: SearchFilters = Field(default_factory=SearchFilters)

    @field_validator("requested_at")
    @classmethod
    def require_timezone(cls, value: datetime) -> datetime:
        """요청 시각에 timezone이 없으면 Collector 호출 전에 검증을 실패시킨다."""

        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("requestedAt에는 timezone이 필요합니다")
        return value


class Provenance(CollectorModel):
    """판매처 사실의 공개 출처, 수집 시각과 Collector 버전을 표현한다."""

    source_url: str = Field(alias="sourceUrl", min_length=1, max_length=2048)
    collected_at: AwareDatetime = Field(alias="collectedAt")
    collector_version: str = Field(alias="collectorVersion", min_length=1, max_length=100)


class Money(CollectorModel):
    """0 이상의 정수 금액과 3자리 통화 코드를 표현한다."""

    amount: int = Field(ge=0)
    currency: CurrencyCode


class Shipping(CollectorModel):
    """검색 결과에서 확인한 배송비와 배송 설명을 표현한다."""

    fee: Money | None
    summary: str | None = Field(max_length=2000)
    provenance: Provenance


class ProductOption(CollectorModel):
    """판매처 상품의 사이즈·색상 옵션과 재고·가격을 표현한다."""

    external_id: str | None = Field(alias="externalId", max_length=200)
    label: str = Field(min_length=1, max_length=300)
    size: str | None = Field(max_length=2000)
    color: str | None = Field(max_length=2000)
    stock_status: StockStatus = Field(alias="stockStatus")
    price: Money | None
    provenance: Provenance


class MeasurementValue(CollectorModel):
    """상품 실측 숫자, 단위와 공개 출처를 표현한다."""

    value: float
    unit: str = Field(min_length=1, max_length=20)
    provenance: Provenance


class Review(CollectorModel):
    """작성자 식별정보를 제외한 공개 리뷰의 최소 필드를 표현한다."""

    external_id: str | None = Field(alias="externalId", max_length=200)
    rating: float | None = Field(default=None, ge=0, le=5)
    text: str | None = Field(default=None, max_length=10000)
    has_image: bool = Field(alias="hasImage")
    purchased_option: str | None = Field(alias="purchasedOption", max_length=2000)
    created_at: AwareDatetime | None = Field(alias="createdAt")
    provenance: Provenance


class Product(CollectorModel):
    """판매처별 원본을 공통 필드로 번역한 상품을 표현한다."""

    external_id: str = Field(alias="externalId", min_length=1, max_length=200)
    name: str = Field(min_length=1, max_length=500)
    brand: str | None = Field(default=None, max_length=2000)
    category_path: list[str] = Field(alias="categoryPath", max_length=20)
    product_url: str = Field(alias="productUrl", min_length=1, max_length=2048)
    image_urls: list[str] = Field(alias="imageUrls", max_length=20)
    price: Money | None
    shipping: Shipping
    stock_status: StockStatus = Field(alias="stockStatus")
    rating: float | None = Field(default=None, ge=0, le=5)
    review_count: int | None = Field(alias="reviewCount", default=None, ge=0)
    options: list[ProductOption] = Field(max_length=500)
    measurements: dict[str, MeasurementValue]
    reviews: list[Review] = Field(max_length=1000)
    provenance: Provenance


class CollectorIssue(CollectorModel):
    """Collector가 반환한 경고 또는 오류와 재시도 가능 여부를 표현한다."""

    code: str = Field(min_length=1, max_length=100, pattern=r"^[A-Z][A-Z0-9_]*$")
    message: str = Field(min_length=1, max_length=1000)
    retryable: bool
    source_url: str | None = Field(alias="sourceUrl", default=None, max_length=2048)


class CollectorResult(CollectorModel):
    """검색 작업의 상태와 검증된 상품 배열을 표현한다."""

    request_id: Identifier = Field(alias="requestId")
    operation: Literal["search", "product", "reviews"]
    status: CollectorStatus
    merchant: MerchantName
    total_count: int | None = Field(alias="totalCount", default=None, ge=0)
    has_next: bool | None = Field(alias="hasNext", default=None)
    collected_at: AwareDatetime = Field(alias="collectedAt")
    collector_version: str = Field(alias="collectorVersion", min_length=1, max_length=100)
    products: list[Product] = Field(max_length=50)
    warnings: list[CollectorIssue]
    errors: list[CollectorIssue]
