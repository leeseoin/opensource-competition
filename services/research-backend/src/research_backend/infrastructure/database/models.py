"""Collector 결과를 보존하기 위한 첫 PostgreSQL 모델을 정의한다."""

from datetime import datetime
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from research_backend.infrastructure.database.base import Base


class Product(Base):
    """판매처와 무관하게 정리한 상품 기본정보를 저장한다.

    상품명과 선택적인 브랜드를 입력받으며 생성된 UUID를 식별자로 제공한다.
    필수 상품명이 없으면 DB의 NOT NULL 제약조건으로 저장에 실패한다.
    """

    __tablename__ = "products"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(500))
    brand: Mapped[str | None] = mapped_column(String(200), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

    merchant_products: Mapped[list["MerchantProduct"]] = relationship(back_populates="product")


class MerchantProduct(Base):
    """판매처의 상품번호와 공통 상품을 연결한다.

    판매처명, 외부 상품번호, 상품 URL을 입력받는다. 같은 판매처의 같은 상품번호가
    중복되면 고유 제약조건으로 저장에 실패한다.
    """

    __tablename__ = "merchant_products"
    __table_args__ = (UniqueConstraint("merchant", "external_id"),)

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    product_id: Mapped[UUID] = mapped_column(ForeignKey("products.id", ondelete="RESTRICT"), index=True)
    merchant: Mapped[str] = mapped_column(String(50))
    external_id: Mapped[str] = mapped_column(String(200))
    product_url: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

    product: Mapped[Product] = relationship(back_populates="merchant_products")
    offer_snapshots: Mapped[list["OfferSnapshot"]] = relationship(back_populates="merchant_product")
    evidence_items: Mapped[list["Evidence"]] = relationship(back_populates="merchant_product")


class OfferSnapshot(Base):
    """특정 수집 시각의 가격, 통화와 재고 상태를 변경 이력으로 저장한다.

    판매처 상품과 수집 출처 정보를 입력받아 UUID를 제공한다. 연결할 판매처 상품이
    없거나 필수 가격·출처 값이 누락되면 DB 제약조건으로 저장에 실패한다.
    """

    __tablename__ = "offer_snapshots"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    merchant_product_id: Mapped[UUID] = mapped_column(
        ForeignKey("merchant_products.id", ondelete="CASCADE"), index=True
    )
    amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    currency: Mapped[str] = mapped_column(String(3))
    stock_status: Mapped[str] = mapped_column(String(30))
    collected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    collector_version: Mapped[str] = mapped_column(String(100))
    source_url: Mapped[str] = mapped_column(Text)

    merchant_product: Mapped[MerchantProduct] = relationship(back_populates="offer_snapshots")
    options: Mapped[list["ProductOption"]] = relationship(back_populates="offer_snapshot")


class ProductOption(Base):
    """한 가격 스냅샷에서 확인한 사이즈·색상별 옵션 상태를 저장한다.

    옵션 라벨과 재고 상태를 입력받으며 크롤러가 제공한 외부 옵션번호, 사이즈,
    색상과 옵션 가격은 선택값이다. 연결할 스냅샷이 없으면 저장에 실패한다.
    """

    __tablename__ = "product_options"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    offer_snapshot_id: Mapped[UUID] = mapped_column(
        ForeignKey("offer_snapshots.id", ondelete="CASCADE"), index=True
    )
    external_id: Mapped[str | None] = mapped_column(String(200), nullable=True)
    label: Mapped[str] = mapped_column(String(300))
    size: Mapped[str | None] = mapped_column(String(100), nullable=True)
    color: Mapped[str | None] = mapped_column(String(100), nullable=True)
    stock_status: Mapped[str] = mapped_column(String(30))
    amount: Mapped[Decimal | None] = mapped_column(Numeric(18, 2), nullable=True)
    currency: Mapped[str | None] = mapped_column(String(3), nullable=True)

    offer_snapshot: Mapped[OfferSnapshot] = relationship(back_populates="options")


class Evidence(Base):
    """상품 사실의 공개 출처와 수집 시각을 별도 근거로 저장한다.

    판매처 상품, 출처 URL, 수집 시각과 Collector 버전을 입력받아 근거 UUID를
    제공한다. 연결 대상이나 필수 출처 정보가 없으면 DB 제약조건으로 실패한다.
    """

    __tablename__ = "evidence"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    merchant_product_id: Mapped[UUID] = mapped_column(
        ForeignKey("merchant_products.id", ondelete="CASCADE"), index=True
    )
    evidence_type: Mapped[str] = mapped_column(String(50))
    source_url: Mapped[str] = mapped_column(Text)
    collected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    collector_version: Mapped[str] = mapped_column(String(100))

    merchant_product: Mapped[MerchantProduct] = relationship(back_populates="evidence_items")
