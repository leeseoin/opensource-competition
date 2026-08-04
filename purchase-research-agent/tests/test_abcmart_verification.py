"""ABC마트 JSON과 HTML 상품의 전수 비교 규칙을 검증한다."""

import unittest

from app.crawlers.abcmart.verification import reconcile_page, verification_summary


class AbcMartVerificationTests(unittest.TestCase):
    """일치, 필드 차이와 HTML 누락 상태를 상품별로 검증한다."""

    def test_marks_every_json_product_with_verification_status(self) -> None:
        """JSON 상품 전체가 MATCHED 또는 MISSING_IN_HTML 상태를 받는지 검증한다."""

        json_products = [
            _product("1", "상품 1", "19,000원"),
            _product("2", "상품 2", "29,000원"),
        ]
        html_products = [_product("1", "상품 1", "19,000 원")]

        verified, warnings = reconcile_page(
            json_products,
            html_products,
            json_source_url="https://example.com/api?page=1",
            html_source_url="https://example.com/search?page=1",
        )

        self.assertEqual(verification_summary(verified), {"MATCHED": 1, "MISSING_IN_HTML": 1})
        self.assertEqual(len(warnings), 1)

    def test_records_price_mismatch(self) -> None:
        """JSON과 HTML 가격이 다르면 필드 차이가 MISMATCH로 기록되는지 검증한다."""

        verified, warnings = reconcile_page(
            [_product("1", "상품 1", "19,000원")],
            [_product("1", "상품 1", "49,000원")],
            json_source_url="https://example.com/api?page=1",
            html_source_url="https://example.com/search?page=1",
        )

        verification = verified[0]["verification"]
        self.assertEqual(verification["status"], "MISMATCH")
        self.assertEqual(verification["differences"][0]["field"], "price")
        self.assertEqual(len(warnings), 1)

    def test_ignores_json_only_fields_and_html_no_discount_omission(self) -> None:
        """HTML에 없는 JSON 전용 필드와 무할인 생략 표현을 불일치로 처리하지 않는지 검증한다."""

        json_product = _product("1", "상품 1", "49,000원")
        json_product.update({
            "price_original": "49,000원",
            "discount_percent": 0,
            "color": "BLACK",
            "style_code": "STYLE-1",
        })
        html_product = _product("1", "상품 1", "49,000원")
        html_product.update({
            "price_original": "",
            "discount_percent": None,
            "color": "",
            "style_code": "",
        })

        verified, warnings = reconcile_page(
            [json_product],
            [html_product],
            json_source_url="https://example.com/api?page=1",
            html_source_url="https://example.com/search?page=1",
        )

        verification = verified[0]["verification"]
        self.assertEqual(verification["status"], "MATCHED")
        self.assertNotIn("color", verification["compared_fields"])
        self.assertNotIn("style_code", verification["compared_fields"])
        self.assertEqual(warnings, [])


def _product(product_id: str, title: str, price: str) -> dict:
    """테스트에서 사용할 비교 필드가 채워진 상품을 만든다."""

    return {
        "source_product_id": product_id,
        "title": title,
        "brand": "브랜드",
        "price": price,
        "price_original": "",
        "discount_percent": None,
        "image_url": "https://example.com/image.jpg?size=500",
        "color": "BLACK",
        "style_code": "STYLE",
        "link": f"https://example.com/product?prdtNo={product_id}",
    }


if __name__ == "__main__":
    unittest.main()
