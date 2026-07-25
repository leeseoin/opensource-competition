"""첫 상품 수집 저장 테이블을 생성한다.

Revision ID: 20260721_0001
Revises:
Create Date: 2026-07-21
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "20260721_0001"
down_revision: str | Sequence[str] | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """상품, 판매처 상품, 가격 스냅샷, 옵션과 근거 테이블을 생성한다."""

    op.create_table(
        "products",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=500), nullable=False),
        sa.Column("brand", sa.String(length=200), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_products")),
    )
    op.create_table(
        "merchant_products",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("product_id", sa.Uuid(), nullable=False),
        sa.Column("merchant", sa.String(length=50), nullable=False),
        sa.Column("external_id", sa.String(length=200), nullable=False),
        sa.Column("product_url", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["product_id"], ["products.id"], name=op.f("fk_merchant_products_product_id_products"), ondelete="RESTRICT"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_merchant_products")),
        sa.UniqueConstraint("merchant", "external_id", name=op.f("uq_merchant_products_merchant")),
    )
    op.create_index(op.f("ix_merchant_products_product_id"), "merchant_products", ["product_id"])
    op.create_table(
        "offer_snapshots",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("merchant_product_id", sa.Uuid(), nullable=False),
        sa.Column("amount", sa.Numeric(precision=18, scale=2), nullable=False),
        sa.Column("currency", sa.String(length=3), nullable=False),
        sa.Column("stock_status", sa.String(length=30), nullable=False),
        sa.Column("collected_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("collector_version", sa.String(length=100), nullable=False),
        sa.Column("source_url", sa.Text(), nullable=False),
        sa.ForeignKeyConstraint(
            ["merchant_product_id"],
            ["merchant_products.id"],
            name=op.f("fk_offer_snapshots_merchant_product_id_merchant_products"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_offer_snapshots")),
    )
    op.create_index(op.f("ix_offer_snapshots_collected_at"), "offer_snapshots", ["collected_at"])
    op.create_index(
        op.f("ix_offer_snapshots_merchant_product_id"), "offer_snapshots", ["merchant_product_id"]
    )
    op.create_table(
        "evidence",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("merchant_product_id", sa.Uuid(), nullable=False),
        sa.Column("evidence_type", sa.String(length=50), nullable=False),
        sa.Column("source_url", sa.Text(), nullable=False),
        sa.Column("collected_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("collector_version", sa.String(length=100), nullable=False),
        sa.ForeignKeyConstraint(
            ["merchant_product_id"],
            ["merchant_products.id"],
            name=op.f("fk_evidence_merchant_product_id_merchant_products"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_evidence")),
    )
    op.create_index(op.f("ix_evidence_collected_at"), "evidence", ["collected_at"])
    op.create_index(op.f("ix_evidence_merchant_product_id"), "evidence", ["merchant_product_id"])
    op.create_table(
        "product_options",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("offer_snapshot_id", sa.Uuid(), nullable=False),
        sa.Column("external_id", sa.String(length=200), nullable=True),
        sa.Column("label", sa.String(length=300), nullable=False),
        sa.Column("size", sa.String(length=100), nullable=True),
        sa.Column("color", sa.String(length=100), nullable=True),
        sa.Column("stock_status", sa.String(length=30), nullable=False),
        sa.Column("amount", sa.Numeric(precision=18, scale=2), nullable=True),
        sa.Column("currency", sa.String(length=3), nullable=True),
        sa.ForeignKeyConstraint(
            ["offer_snapshot_id"],
            ["offer_snapshots.id"],
            name=op.f("fk_product_options_offer_snapshot_id_offer_snapshots"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_product_options")),
    )
    op.create_index(
        op.f("ix_product_options_offer_snapshot_id"), "product_options", ["offer_snapshot_id"]
    )


def downgrade() -> None:
    """첫 상품 수집 저장 테이블과 index를 의존성 역순으로 제거한다."""

    op.drop_index(op.f("ix_product_options_offer_snapshot_id"), table_name="product_options")
    op.drop_table("product_options")
    op.drop_index(op.f("ix_evidence_merchant_product_id"), table_name="evidence")
    op.drop_index(op.f("ix_evidence_collected_at"), table_name="evidence")
    op.drop_table("evidence")
    op.drop_index(op.f("ix_offer_snapshots_merchant_product_id"), table_name="offer_snapshots")
    op.drop_index(op.f("ix_offer_snapshots_collected_at"), table_name="offer_snapshots")
    op.drop_table("offer_snapshots")
    op.drop_index(op.f("ix_merchant_products_product_id"), table_name="merchant_products")
    op.drop_table("merchant_products")
    op.drop_table("products")
