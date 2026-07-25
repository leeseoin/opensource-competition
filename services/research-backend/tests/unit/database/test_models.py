"""첫 DB 모델과 migration 대상 metadata를 검증한다."""

from sqlalchemy import UniqueConstraint

from research_backend.infrastructure.database import Base


def test_initial_collection_tables_are_registered() -> None:
    """첫 migration에 필요한 다섯 테이블이 metadata에 등록되어야 한다."""

    assert set(Base.metadata.tables) == {
        "evidence",
        "merchant_products",
        "offer_snapshots",
        "product_options",
        "products",
    }


def test_merchant_external_id_is_unique_per_merchant() -> None:
    """같은 판매처의 외부 상품번호 중복을 막는 제약조건이 있어야 한다."""

    table = Base.metadata.tables["merchant_products"]
    unique_column_sets = {
        tuple(column.name for column in constraint.columns)
        for constraint in table.constraints
        if isinstance(constraint, UniqueConstraint)
    }

    assert ("merchant", "external_id") in unique_column_sets
