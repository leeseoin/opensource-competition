"""29CM 검색 JSON과 상세 HTML JSON-LD의 전수 비교 규칙을 검증한다."""

import json
import unittest

import httpx

from app.crawlers.cm29.crawler import Cm29Crawler
from app.crawlers.cm29.verification import (
    _canonical_detail_url,
    compare_product,
    parse_product_json_ld,
    verify_products,
)


class CanonicalDetailUrlTests(unittest.TestCase):
    """검색 API가 돌려주는 구 상세 URL을 리다이렉트 없는 현재 URL로 바꾸는지 검증한다."""

    def test_rewrites_legacy_catalog_url_to_current_products_url(self) -> None:
        """product.29cm.co.kr/catalog/{id}를 www.29cm.co.kr/products/{id}로 바꾸는지 검증한다."""

        rewritten = _canonical_detail_url("https://product.29cm.co.kr/catalog/2468262")

        self.assertEqual(rewritten, "https://www.29cm.co.kr/products/2468262")

    def test_leaves_already_current_url_unchanged(self) -> None:
        """이미 현재 형식인 URL은 그대로 두는지 검증한다."""

        url = "https://www.29cm.co.kr/products/2468262"

        self.assertEqual(_canonical_detail_url(url), url)


class VerifyProductsRedirectAvoidanceTests(unittest.IsolatedAsyncioTestCase):
    """검증 요청이 구 URL의 307을 겪지 않고 현재 URL로 바로 나가는지 검증한다."""

    async def test_requests_current_url_instead_of_legacy_redirecting_url(self) -> None:
        """구 link로도 요청은 www.29cm.co.kr/products/{id}로만 나가는지 검증한다."""

        requested_urls: list[str] = []

        def handler(request: httpx.Request) -> httpx.Response:
            requested_urls.append(str(request.url))
            return httpx.Response(200, text=_html(_json_ld()))

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            products, warnings = await verify_products(
                client,
                [_listing_product()],
                json_source_url="https://display-bff-api.29cm.co.kr/api/v1/listing/items",
                ts_file="20260812",
            )

        self.assertEqual(requested_urls, ["https://www.29cm.co.kr/products/2468262"])
        self.assertEqual(products[0]["verification"]["status"], "MATCHED")
        self.assertEqual(warnings, [])


class Cm29VerificationTests(unittest.TestCase):
    """29CM Product JSON-LD 파싱과 필드 차이 판정을 검증한다."""

    def test_parses_product_json_ld_and_matches_listing(self) -> None:
        """상세 HTML의 SEO 상품 정보가 검색 JSON 상품과 일치하는지 검증한다."""

        html = _html(_json_ld())
        html_product = parse_product_json_ld(html, "https://www.29cm.co.kr/products/2468262")
        verification = compare_product(
            _listing_product(),
            html_product,
            json_source_url="https://display-bff-api.29cm.co.kr/api/v1/listing/items",
            html_source_url="https://www.29cm.co.kr/products/2468262",
        )

        self.assertEqual(html_product["source_product_id"], "2468262")
        self.assertEqual(html_product["price"], "119,280원")
        self.assertEqual(verification["status"], "MATCHED")
        self.assertEqual(verification["differences"], [])

    def test_records_detail_price_mismatch(self) -> None:
        """검색 JSON과 상세 HTML 가격이 다르면 가격 차이를 기록하는지 검증한다."""

        payload = _json_ld()
        payload["offers"]["price"] = 120000
        html_product = parse_product_json_ld(
            _html(payload),
            "https://www.29cm.co.kr/products/2468262",
        )
        verification = compare_product(
            _listing_product(),
            html_product,
            json_source_url="https://display-bff-api.29cm.co.kr/api/v1/listing/items",
            html_source_url="https://www.29cm.co.kr/products/2468262",
        )

        self.assertEqual(verification["status"], "MISMATCH")
        self.assertIn("price", [item["field"] for item in verification["differences"]])

    def test_treats_omitted_no_discount_fields_as_match(self) -> None:
        """상세 HTML이 무할인 정상가를 생략해도 검색 JSON과 일치하는지 검증한다."""

        listing = _listing_product()
        listing.update({
            "price": "168,000원",
            "price_original": "168,000원",
            "discount_percent": 0,
        })
        payload = _json_ld()
        payload["offers"]["price"] = 168000
        payload["offers"].pop("priceSpecification")
        html_product = parse_product_json_ld(
            _html(payload),
            "https://www.29cm.co.kr/products/2468262",
        )

        verification = compare_product(
            listing,
            html_product,
            json_source_url="https://display-bff-api.29cm.co.kr/api/v1/listing/items",
            html_source_url="https://www.29cm.co.kr/products/2468262",
        )

        self.assertEqual(verification["status"], "MATCHED")

    def test_listing_discount_uses_stored_sell_price(self) -> None:
        """쿠폰 표시가가 있어도 저장 판매가와 정상가로 할인율을 계산하는지 검증한다."""

        products = Cm29Crawler()._parse([{
            "itemId": 2468262,
            "itemUrl": {"webLink": "https://product.29cm.co.kr/catalog/2468262"},
            "itemInfo": {
                "productName": "[29EDITION] BELLA SLINGBACK / BLACK",
                "brandName": "기호",
                "originalPrice": 168000,
                "sellPrice": 119280,
                "displayPrice": 87200,
                "saleRate": 48.0,
            },
        }])

        self.assertEqual(products[0]["price"], "119,280원")
        self.assertEqual(products[0]["discount_percent"], 29)


def _listing_product() -> dict:
    """테스트에서 사용할 29CM 검색 JSON 변환 상품을 만든다."""

    return {
        "source_product_id": "2468262",
        "title": "[29EDITION] BELLA SLINGBACK / BLACK",
        "brand": "기호",
        "price": "119,280원",
        "price_original": "168,000원",
        "discount_percent": 29,
        "image_url": "https://img.29cm.co.kr/item/product.jpg",
        "link": "https://product.29cm.co.kr/catalog/2468262",
    }


def _json_ld() -> dict:
    """테스트에서 HTML에 넣을 29CM Product JSON-LD를 만든다."""

    return {
        "@context": "https://schema.org",
        "@type": "Product",
        "sku": "2468262",
        "name": "[29EDITION] BELLA SLINGBACK / BLACK",
        "brand": {"@type": "Brand", "name": "기호"},
        "image": [{"@type": "ImageObject", "contentUrl": "https://img.29cm.co.kr/item/product.jpg"}],
        "offers": {
            "@type": "Offer",
            "price": 119280,
            "url": "https://www.29cm.co.kr/products/2468262",
            "priceSpecification": {
                "@type": "UnitPriceSpecification",
                "price": 168000,
            },
        },
    }


def _html(payload: dict) -> str:
    """Product JSON-LD를 포함한 최소 HTML fixture를 만든다."""

    return (
        '<html><head><script type="application/ld+json">'
        f"{json.dumps(payload, ensure_ascii=False)}"
        "</script></head><body></body></html>"
    )


if __name__ == "__main__":
    unittest.main()
