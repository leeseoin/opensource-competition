"""전달받은 v1-unified 예제와 Python validator의 일치 여부를 검증한다."""

from __future__ import annotations

import json
import unittest

from purchase_collector.contract import repository_root, validate_product


class UnifiedContractTests(unittest.TestCase):
    """공통 예제 20건을 Python과 Go 비교의 고정 기준으로 사용한다."""

    def test_all_shared_examples_pass_schema(self) -> None:
        """ABC마트 10건과 29CM 10건이 모두 Python validator를 통과하는지 검증한다."""

        example_path = (
            repository_root()
            / "contracts"
            / "collector"
            / "unified"
            / "examples"
            / "unified_구두_top20_20260803_002024.json"
        )
        products = json.loads(example_path.read_text(encoding="utf-8"))

        self.assertEqual(20, len(products))
        self.assertEqual({"abcmart", "29cm"}, {product["site"] for product in products})
        for product in products:
            self.assertEqual([], validate_product(product), product["source_product_id"])

    def test_required_identifier_is_rejected_when_empty(self) -> None:
        """상품 ID가 빈 문자열이면 계약 위반으로 보고되는지 검증한다."""

        example_path = (
            repository_root()
            / "contracts"
            / "collector"
            / "unified"
            / "examples"
            / "unified_구두_top20_20260803_002024.json"
        )
        product = json.loads(example_path.read_text(encoding="utf-8"))[0]
        product["source_product_id"] = ""

        self.assertTrue(validate_product(product))


if __name__ == "__main__":
    unittest.main()
