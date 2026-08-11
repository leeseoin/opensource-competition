"""ABC마트 상세/리뷰/옵션 파싱과 수집 상한/개인정보 제거/사진 신호를 검증한다."""

from __future__ import annotations

import unittest
from unittest.mock import patch

import httpx

from app.crawlers.abcmart.detail_fetcher import (
    _extract_detail,
    _fetch_option,
    _fetch_review,
    _parse_review,
    _parse_scores,
    _prdtno,
)


class PrdtnoTests(unittest.TestCase):
    def test_extracts_product_number_from_link(self) -> None:
        self.assertEqual(_prdtno("https://abcmart.a-rt.com/product/detail?prdtNo=123456"), "123456")

    def test_returns_none_when_link_has_no_product_number(self) -> None:
        self.assertIsNone(_prdtno("https://abcmart.a-rt.com/product/detail"))


class ParseScoresTests(unittest.TestCase):
    def test_separates_overall_from_detail_scores(self) -> None:
        overall, details = _parse_scores([
            {"prdtRvwCode": 10000, "evltScore": 4.5},
            {"prdtRvwCode": 10006, "evltScore": 4},
            {"prdtRvwCode": 99999, "evltScore": 3},
        ])

        self.assertEqual(overall, 4.5)
        self.assertEqual(details, {"착화감": 4})

    def test_returns_none_and_empty_dict_when_no_scores(self) -> None:
        overall, details = _parse_scores([])

        self.assertIsNone(overall)
        self.assertEqual(details, {})

    def test_ignores_entries_missing_code_or_score(self) -> None:
        overall, details = _parse_scores([{"prdtRvwCode": 10000}, {"evltScore": 5}])

        self.assertIsNone(overall)
        self.assertEqual(details, {})


class ParseReviewTests(unittest.TestCase):
    """개인 식별정보를 제외하고 사진 신호와 수집 상한(200자)만 보존하는지 검증한다."""

    _RAW_REVIEW_WITH_PII = {
        "prdtRvwSeq": 555,
        "rvwContText": "편해요" * 100,
        "productReviewEvlts": [{"prdtRvwCode": 10000, "evltScore": 5}],
        "writeDtm": "2026-08-01T10:00:00",
        "prdtOptnNm": "265",
        "prdtColorCodeName": "블랙",
        "helpfulCnt": 3,
        "bestYn": "Y",
        "productReviewImages": [{"imageUrl": "https://img.example.com/a.jpg"}],
        # 판매처 원본 응답에 있을 수 있는 개인 식별 필드 — 결과에 남으면 안 된다.
        "writerName": "홍길동",
        "writerId": "user123",
        "phoneNumber": "010-1234-5678",
        "email": "user@example.com",
    }

    def test_excludes_personal_identifiers_beyond_review_sequence(self) -> None:
        review = _parse_review(self._RAW_REVIEW_WITH_PII)

        self.assertEqual(
            set(review),
            {
                "review_source_id", "content", "score", "detail_scores", "date",
                "size", "color", "helpful_count", "is_best", "images",
            },
        )
        self.assertEqual(review["review_source_id"], "555")

    def test_caps_content_length_at_two_hundred_characters(self) -> None:
        review = _parse_review(self._RAW_REVIEW_WITH_PII)

        self.assertLessEqual(len(review["content"]), 200)

    def test_keeps_photo_signal_when_review_has_images(self) -> None:
        review = _parse_review(self._RAW_REVIEW_WITH_PII)

        self.assertEqual(review["images"], ["https://img.example.com/a.jpg"])

    def test_photo_signal_is_empty_list_when_no_images(self) -> None:
        review = _parse_review({"prdtRvwSeq": 1, "rvwContText": "굿", "productReviewEvlts": []})

        self.assertEqual(review["images"], [])

    def test_falls_back_to_prdt_img_url_when_image_url_missing(self) -> None:
        review = _parse_review({
            "prdtRvwSeq": 2,
            "rvwContText": "",
            "productReviewEvlts": [],
            "productReviewImages": [{"prdtImgUrl": "https://img.example.com/b.jpg"}],
        })

        self.assertEqual(review["images"], ["https://img.example.com/b.jpg"])

    def test_size_falls_back_to_optn_name_when_prdt_optn_name_missing(self) -> None:
        review = _parse_review({
            "prdtRvwSeq": 3, "rvwContText": "", "productReviewEvlts": [], "optnName": "M",
        })

        self.assertEqual(review["size"], "M")

    def test_review_source_id_is_none_when_sequence_missing(self) -> None:
        review = _parse_review({"rvwContText": "", "productReviewEvlts": []})

        self.assertIsNone(review["review_source_id"])


class ExtractDetailTests(unittest.TestCase):
    def test_parses_deduplicated_images_category_and_in_stock(self) -> None:
        html = (
            '<script>{"category": "신발 > 구두 > 로퍼"}</script>'
            '<img src="https://image.a-rt.com/art/product/2026/08/abc.jpg?shrink=580:580">'
            '<img src="https://image.a-rt.com/art/product/2026/08/abc.jpg?shrink=580:580">'
            '<button class="btn-prod-size">260</button>'
            '<button class="btn-prod-size sold-out">265</button>'
        )

        detail = _extract_detail(html)

        self.assertEqual(detail["images"], ["https://image.a-rt.com/art/product/2026/08/abc.jpg"])
        self.assertEqual(detail["category"], "로퍼")
        self.assertEqual(detail["category_path"], "신발 > 구두 > 로퍼")
        self.assertTrue(detail["in_stock"], "일부 사이즈가 남아 있으면 재고 있음으로 판단해야 한다")

    def test_marks_out_of_stock_when_every_size_is_sold_out(self) -> None:
        html = '<button class="btn-prod-size sold-out">260</button>'

        detail = _extract_detail(html)

        self.assertFalse(detail["in_stock"])

    def test_in_stock_false_when_no_size_buttons_present(self) -> None:
        detail = _extract_detail("<html></html>")

        self.assertFalse(detail["in_stock"])
        self.assertEqual(detail["images"], [])
        self.assertEqual(detail["category"], "")


def _review_response(*, total_count: int, content: list[dict]) -> httpx.Response:
    return httpx.Response(200, json={"totalCount": total_count, "content": content})


def _raw_review(review_id: int) -> dict:
    return {"prdtRvwSeq": review_id, "rvwContText": f"review-{review_id}", "productReviewEvlts": []}


class FetchReviewTests(unittest.IsolatedAsyncioTestCase):
    """리뷰 API 응답을 상한(수집 개수)까지만 요청하는지, 실패 시 안전한지 검증한다."""

    @patch("app.crawlers.abcmart.detail_fetcher._REVIEW_PAGE_SIZE", 2)
    async def test_stops_once_review_limit_reached_without_exhausting_remaining_pages(self) -> None:
        pages = [
            _review_response(total_count=6, content=[_raw_review(1), _raw_review(2)]),
            _review_response(total_count=6, content=[_raw_review(3), _raw_review(4)]),
            _review_response(total_count=6, content=[_raw_review(5), _raw_review(6)]),
        ]
        request_count = {"value": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            response = pages[request_count["value"]]
            request_count["value"] += 1
            return response

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await _fetch_review(client, "prdt-1", review_limit=3)

        self.assertEqual(len(result["reviews"]), 3)
        self.assertEqual(request_count["value"], 2, "상한(3개)에 도달하면 남은 page를 요청하면 안 된다")

    @patch("app.crawlers.abcmart.detail_fetcher._REVIEW_PAGE_SIZE", 2)
    async def test_paginates_to_last_page_when_no_review_limit_set(self) -> None:
        pages = [
            _review_response(total_count=3, content=[_raw_review(1), _raw_review(2)]),
            _review_response(total_count=3, content=[_raw_review(3)]),
        ]
        request_count = {"value": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            response = pages[request_count["value"]]
            request_count["value"] += 1
            return response

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await _fetch_review(client, "prdt-1", review_limit=0)

        self.assertEqual(len(result["reviews"]), 3)
        self.assertEqual(result["review_count"], 3)
        self.assertEqual(request_count["value"], 2)

    async def test_returns_safe_error_without_url_or_body_on_failure(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(500, text="<html>internal secret trace</html>")

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await _fetch_review(client, "prdt-1", review_limit=0)

        self.assertIn("_err", result)
        self.assertNotIn("internal secret trace", result["_err"], "응답 body가 오류 메시지에 새면 안 된다")


class FetchOptionTests(unittest.IsolatedAsyncioTestCase):
    async def test_extracts_colors_and_sizes_from_option_response(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(
                200,
                json={
                    "resultColorList": [{"codeDtlName": "블랙"}, {"codeDtlName": "화이트"}],
                    "resultList": [{"optnName": "260"}, {"optnName": "265"}],
                },
            )

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await _fetch_option(client, "prdt-1")

        self.assertEqual(result["colors"], ["블랙", "화이트"])
        self.assertEqual(result["sizes"], ["260", "265"])

    async def test_returns_safe_error_without_body_on_failure(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(403, text="blocked by waf internal detail")

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await _fetch_option(client, "prdt-1")

        self.assertIn("_err", result)
        self.assertNotIn("blocked by waf internal detail", result["_err"])


if __name__ == "__main__":
    unittest.main()
