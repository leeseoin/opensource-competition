"""Python 원본 상품을 현재 CollectorResult로 바꾸는 동작을 검증한다."""

import unittest

from app.services.collector_result_adapter import (
    build_collector_result,
    build_collector_result_batches,
    parse_won,
)


class CollectorResultAdapterTests(unittest.TestCase):
    """가격, 옵션, 리뷰와 출처 변환의 저장 계약을 검증한다."""

    def test_parses_won_string(self) -> None:
        """원화 표시 문자열이 정수 금액으로 변환되는지 검증한다."""

        self.assertEqual(parse_won("19,000원"), 19000)
        self.assertIsNone(parse_won(""))

    def test_builds_current_collector_result(self) -> None:
        """정우님 ABC마트 상품이 Spring Boot 저장 계약의 핵심 필드를 채우는지 검증한다."""

        result = build_collector_result(
            [{
                "source_product_id": "abc-1",
                "title": "테스트 구두",
                "brand": "테스트 브랜드",
                "price": "19,000원",
                "image_url": "https://example.com/image.jpg",
                "link": "https://example.com/products/abc-1",
                "site": "abcmart",
                "review_count": 1,
                "options": {
                    "sizes": ["270", "270"],
                    "available_sizes": ["270"],
                    "colors": ["BLACK"],
                },
                "category_path": "신발 > 구두",
                "in_stock": True,
                "reviews": [{
                    "review_source_id": "review-1",
                    "content": "편합니다",
                    "score": 5,
                    "date": "2026-08-04",
                    "size": "270",
                }],
            }],
            request_id="python-test-001",
            merchant="abcmart",
            query="구두",
            collected_at="2026-08-04T03:00:00+00:00",
        )

        self.assertEqual(result["status"], "success")
        self.assertNotIn("verificationSummary", result)
        self.assertNotIn("verification", result["products"][0])
        self.assertEqual(result["products"][0]["price"]["amount"], 19000)
        self.assertEqual(len(result["products"][0]["options"]), 2)
        self.assertEqual(result["products"][0]["options"][0]["stockStatus"], "available")
        self.assertEqual(result["products"][0]["options"][0]["color"], "BLACK")
        self.assertEqual(result["products"][0]["categoryPath"], ["신발", "구두"])
        self.assertEqual(result["products"][0]["stockStatus"], "available")
        self.assertEqual(result["products"][0]["reviews"][0]["createdAt"], "2026-08-04T00:00:00+09:00")
        self.assertEqual(result["products"][0]["provenance"]["collectorVersion"], "python-collector-v1")

    def test_preserves_29cm_option_pair_and_stock_status(self) -> None:
        """29CM 상세 옵션의 색상/사이즈 조합과 판매 상태를 공통 계약에 보존하는지 검증한다."""

        result = build_collector_result(
            [{
                "source_product_id": "29-available-1",
                "title": "테스트 슬링백",
                "price": "89,000원",
                "link": "https://product.29cm.co.kr/catalog/29-available-1",
                "site": "29cm",
                "options": [{
                    "name": "SIZE",
                    "value": "BLACK (3CM) / KR 230 / IT36",
                    "stock_status": "available",
                }],
            }],
            request_id="python-29cm-options-001",
            merchant="29cm",
            query="슬링백",
            collected_at="2026-08-15T03:00:00+00:00",
        )

        option = result["products"][0]["options"][0]
        self.assertEqual(option["size"], "230")
        self.assertEqual(option["color"], "BLACK")
        self.assertEqual(option["stockStatus"], "available")

    def test_marks_crawler_errors_as_partial_warnings(self) -> None:
        """일부 크롤링 오류가 저장 가능한 partial 상태와 경고로 바뀌는지 검증한다."""

        result = build_collector_result(
            [{
                "source_product_id": "29-1",
                "title": "테스트 상품",
                "price": "10,000원",
                "link": "https://example.com/products/29-1",
            }],
            request_id="python-test-002",
            merchant="29cm",
            query="티셔츠",
            collected_at="2026-08-04T03:00:00+00:00",
            crawler_errors=["다음 페이지 요청 실패"],
        )

        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["warnings"][0]["code"], "PYTHON_CRAWLER_WARNING")

    def test_converts_full_page_verification_for_storage(self) -> None:
        """JSON/HTML 전수 비교 결과가 Spring Boot 저장 계약으로 변환되는지 검증한다."""

        result = build_collector_result(
            [{
                "source_product_id": "abc-verified-1",
                "title": "검증 상품",
                "price": "19,000원",
                "link": "https://abcmart.a-rt.com/product?prdtNo=1000000001",
                "verification": {
                    "status": "MISMATCH",
                    "compared_fields": ["title", "price"],
                    "differences": [{
                        "field": "price",
                        "json_value": "19,000원",
                        "html_value": "20,000원",
                    }],
                    "json_source_url": "https://abcmart.a-rt.com/json?page=1",
                    "html_source_url": "https://abcmart.a-rt.com/search?page=1",
                    "verified_at": "2026-08-04T03:00:01+00:00",
                },
            }],
            request_id="python-verification-001",
            merchant="abcmart",
            query="구두",
            collected_at="2026-08-04T03:00:00+00:00",
        )

        verification = result["products"][0]["verification"]
        self.assertEqual(result["verificationSummary"], {
            "total": 1,
            "matched": 0,
            "mismatched": 1,
            "failed": 0,
            "missingInHtml": 0,
            "missingInJson": 0,
            "pending": 0,
        })
        self.assertEqual(verification["status"], "MISMATCH")
        self.assertEqual(verification["comparedFields"], ["title", "price"])
        self.assertEqual(verification["differences"][0]["htmlValue"], "20,000원")

    def test_splits_large_result_into_fifty_product_batches(self) -> None:
        """125개 상품이 Spring Boot 상한에 맞춰 50/50/25개로 나뉘는지 검증한다."""

        products = [
            {
                "source_product_id": f"item-{index}",
                "title": f"상품 {index}",
                "price": "10,000원",
                "link": f"https://example.com/products/{index}",
                "verification": {
                    "status": "MATCHED" if index < 60 else "MISMATCH",
                    "compared_fields": ["title"],
                    "differences": [],
                    "json_source_url": "https://example.com/api",
                    "html_source_url": f"https://example.com/products/{index}",
                    "verified_at": "2026-08-04T03:00:01+00:00",
                },
            }
            for index in range(125)
        ]
        batches = build_collector_result_batches(
            products,
            request_id_prefix="python-large-001",
            merchant="abcmart",
            query="구두",
            collected_at="2026-08-04T03:00:00+00:00",
        )

        self.assertEqual([len(batch["products"]) for batch in batches], [50, 50, 25])
        self.assertTrue(all(batch["totalCount"] == 125 for batch in batches))
        self.assertEqual(batches[0]["requestId"], "python-large-001-b001")
        self.assertEqual(batches[2]["requestId"], "python-large-001-b003")
        self.assertEqual(batches[0]["verificationSummary"]["matched"], 50)
        self.assertEqual(batches[1]["verificationSummary"]["matched"], 10)
        self.assertEqual(batches[1]["verificationSummary"]["mismatched"], 40)
        self.assertEqual(batches[2]["verificationSummary"]["mismatched"], 25)

    def test_rejects_verification_status_outside_shared_contract(self) -> None:
        """공통 계약에 없는 검증 상태가 Product Backend 전송 전에 차단되는지 검증한다."""

        with self.assertRaisesRegex(ValueError, "CollectorResult 계약 위반"):
            build_collector_result(
                [{
                    "source_product_id": "invalid-status-1",
                    "title": "잘못된 검증 상태 상품",
                    "price": "10,000원",
                    "link": "https://example.com/products/invalid-status-1",
                    "verification": {
                        "status": "UNKNOWN_STATUS",
                        "compared_fields": ["title"],
                        "differences": [],
                        "json_source_url": "https://example.com/api",
                        "html_source_url": "https://example.com/products/invalid-status-1",
                        "verified_at": "2026-08-04T03:00:01+00:00",
                    },
                }],
                request_id="python-invalid-status-001",
                merchant="abcmart",
                query="구두",
                collected_at="2026-08-04T03:00:00+00:00",
            )


if __name__ == "__main__":
    unittest.main()
