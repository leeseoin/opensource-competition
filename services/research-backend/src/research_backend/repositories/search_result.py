"""검증된 Collector 검색 결과를 SQLAlchemy 모델로 저장한다."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from research_backend.application.dto import CollectionSaveSummary
from research_backend.clients.collector.models import CollectorResult, Product as CollectedProduct
from research_backend.infrastructure.database.models import (
    Evidence,
    MerchantProduct,
    OfferSnapshot,
    Product,
    ProductOption,
)


class SqlAlchemySearchResultRepository:
    """판매처 상품은 upsert하고 가격·재고는 새 스냅샷으로 저장한다.

    열린 SQLAlchemy Session과 검증된 CollectorResult를 입력받아 저장 개수를 반환한다.
    commit과 rollback은 호출자가 소유하며 제약조건 또는 연결 실패는 그대로 전달한다.
    """

    def save(self, session: Session, result: CollectorResult) -> CollectionSaveSummary:
        """성공·부분 성공 검색 결과의 상품, 스냅샷, 옵션과 근거를 저장한다."""

        merchant_product_count = 0
        offer_snapshot_count = 0
        option_count = 0
        evidence_count = 0

        for collected_product in result.products:
            merchant_product = self._upsert_merchant_product(
                session=session,
                merchant=result.merchant,
                collected_product=collected_product,
            )
            merchant_product_count += 1

            session.add(
                Evidence(
                    merchant_product=merchant_product,
                    evidence_type="product",
                    source_url=collected_product.provenance.source_url,
                    collected_at=collected_product.provenance.collected_at,
                    collector_version=collected_product.provenance.collector_version,
                )
            )
            evidence_count += 1

            if collected_product.price is None:
                continue

            snapshot = OfferSnapshot(
                merchant_product=merchant_product,
                amount=collected_product.price.amount,
                currency=collected_product.price.currency,
                stock_status=collected_product.stock_status,
                collected_at=collected_product.provenance.collected_at,
                collector_version=collected_product.provenance.collector_version,
                source_url=collected_product.provenance.source_url,
            )
            session.add(snapshot)
            offer_snapshot_count += 1

            for collected_option in collected_product.options:
                option_price = collected_option.price
                session.add(
                    ProductOption(
                        offer_snapshot=snapshot,
                        external_id=collected_option.external_id,
                        label=collected_option.label,
                        size=collected_option.size,
                        color=collected_option.color,
                        stock_status=collected_option.stock_status,
                        amount=option_price.amount if option_price else None,
                        currency=option_price.currency if option_price else None,
                    )
                )
                option_count += 1

        session.flush()
        return CollectionSaveSummary(
            merchant_products=merchant_product_count,
            offer_snapshots=offer_snapshot_count,
            options=option_count,
            evidence=evidence_count,
        )

    @staticmethod
    def _upsert_merchant_product(
        session: Session,
        merchant: str,
        collected_product: CollectedProduct,
    ) -> MerchantProduct:
        """판매처와 externalId로 기존 행을 갱신하거나 새 상품 연결을 생성한다."""

        merchant_product = session.scalar(
            select(MerchantProduct).where(
                MerchantProduct.merchant == merchant,
                MerchantProduct.external_id == collected_product.external_id,
            )
        )
        collected_at = collected_product.provenance.collected_at

        if merchant_product is None:
            product = Product(
                name=collected_product.name,
                brand=collected_product.brand,
                created_at=collected_at,
            )
            merchant_product = MerchantProduct(
                product=product,
                merchant=merchant,
                external_id=collected_product.external_id,
                product_url=collected_product.product_url,
                created_at=collected_at,
                updated_at=collected_at,
            )
            session.add(merchant_product)
            return merchant_product

        merchant_product.product.name = collected_product.name
        merchant_product.product.brand = collected_product.brand
        merchant_product.product_url = collected_product.product_url
        merchant_product.updated_at = collected_at
        return merchant_product
