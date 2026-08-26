"""저장 fixture parser benchmark가 같은 측정 경계를 지키는지 검증한다."""

from __future__ import annotations

import unittest

from purchase_collector.benchmark import run_benchmark


class ParserBenchmarkTests(unittest.TestCase):
    """실제 네트워크 없이 양쪽 판매처 benchmark 결과 형식을 검증한다."""

    def test_both_merchants_decode_normalize_and_validate(self) -> None:
        """ABC마트와 29CM fixture가 decode/정규화/Contract 검증되는지 확인한다."""

        for merchant in ("abcmart", "29cm"):
            with self.subTest(merchant=merchant):
                result = run_benchmark(merchant, iterations=2, warmup=1)
                self.assertEqual("python", result["language"])
                self.assertEqual(merchant, result["merchant"])
                self.assertGreater(result["products_per_iteration"], 0)
                self.assertEqual(
                    result["products_per_iteration"] * 2,
                    result["processed_products"],
                )


if __name__ == "__main__":
    unittest.main()
