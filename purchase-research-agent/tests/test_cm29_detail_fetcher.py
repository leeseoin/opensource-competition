"""29CM 상세 JSON-LD/옵션 파싱과 리뷰 개인정보 제거/사진 신호/수집 상한을 검증한다."""

from __future__ import annotations

import unittest

from app.crawlers.cm29.detail_fetcher import (
    _option_dimensions,
    _parse_ld,
    _parse_options,
    _parse_reviews,
)


class ParseLdTests(unittest.TestCase):
    def test_separates_product_and_breadcrumb_schemas(self) -> None:
        html = """
        <script type="application/ld+json">{"@type": "Product", "name": "운동화"}</script>
        <script type="application/ld+json">{"@type": "BreadcrumbList", "itemListElement": []}</script>
        """

        product, breadcrumb = _parse_ld(html)

        self.assertEqual(product["name"], "운동화")
        self.assertEqual(breadcrumb["itemListElement"], [])

    def test_returns_empty_dicts_when_no_ld_json_present(self) -> None:
        product, breadcrumb = _parse_ld("<html><body>no ld+json here</body></html>")

        self.assertEqual(product, {})
        self.assertEqual(breadcrumb, {})

    def test_ignores_malformed_json_block_without_raising(self) -> None:
        html = '<script type="application/ld+json">{not valid json</script>'

        product, breadcrumb = _parse_ld(html)

        self.assertEqual(product, {})
        self.assertEqual(breadcrumb, {})


class ParseOptionsTests(unittest.TestCase):
    def test_parses_unescaped_option_pairs(self) -> None:
        html = '"optionItemName":"SIZE","optionItemValue":"230mm"'

        options = _parse_options(html)

        self.assertEqual(options, [{"name": "SIZE", "value": "230mm", "stock_status": "unknown"}])

    def test_parses_escaped_streaming_option_pairs(self) -> None:
        html = r'\"optionItemName\":\"COLOR\",\"optionItemValue\":\"BLACK\"'

        options = _parse_options(html)

        self.assertEqual(options, [{"name": "COLOR", "value": "BLACK", "stock_status": "unknown"}])

    def test_deduplicates_repeated_option_pairs(self) -> None:
        html = (
            '"optionItemName":"SIZE","optionItemValue":"230mm"'
            '"optionItemName":"SIZE","optionItemValue":"230mm"'
            '"optionItemName":"SIZE","optionItemValue":"235mm"'
        )

        options = _parse_options(html)

        self.assertEqual(options, [
            {"name": "SIZE", "value": "230mm", "stock_status": "unknown"},
            {"name": "SIZE", "value": "235mm", "stock_status": "unknown"},
        ])

    def test_returns_empty_list_when_no_options_present(self) -> None:
        self.assertEqual(_parse_options("<html></html>"), [])

    def test_preserves_available_and_sold_out_option_status(self) -> None:
        """29CM streaming option 객체의 공개 판매 상태를 각 옵션에 연결하는지 검증한다."""

        html = (
            r'{\"isSoldOut\":false,\"isVisible\":true,'
            r'\"optionItemName\":\"SIZE\",\"optionItemValue\":\"230\"}'
            r'{\"isSoldOut\":true,\"isVisible\":true,'
            r'\"optionItemName\":\"SIZE\",\"optionItemValue\":\"235\"}'
        )

        options = _parse_options(html)

        self.assertEqual([option["stock_status"] for option in options], ["available", "out_of_stock"])

    def test_extracts_shoe_apparel_and_color_dimensions(self) -> None:
        """신발 숫자/의류 문자 사이즈와 결합 색상을 범용 목록으로 추출하는지 검증한다."""

        dimensions = _option_dimensions([
            {"name": "SIZE", "value": "BLACK (3CM) / KR 230 / IT36"},
            {"name": "SIZE", "value": "M"},
            {"name": "COLOR", "value": "NAVY"},
        ])

        self.assertEqual(dimensions["sizes"], ["230", "M"])
        self.assertEqual(dimensions["colors"], ["BLACK", "NAVY"])


class ParseReviewsTests(unittest.TestCase):
    """29CM 리뷰가 개인 식별정보 없이 사진/사이즈 신호만 보존하는지 검증한다."""

    def _raw_review(self, **overrides: object) -> dict:
        base = {
            "itemReviewNo": 42,
            "contents": "정말 편해요" * 100,
            "point": 5,
            "insertTimestamp": "2026-08-01T10:00:00",
            "optionValue": ["[SIZE] 230mm"],
            "userSize": [230],
            "helpfulCount": 2,
            "uploadFiles": [{"url": "/reviews/a.jpg", "isDeleted": "F"}],
            "surveyList": [{"surveyType": "SIZE", "optionValue": 2}],
            "partnerComment": None,
            "isBlind": False,
            # 원본 응답에 있을 수 있는 개인 식별 필드 — 결과에 남으면 안 된다.
            "writerNickname": "구두러버",
            "writerId": "member-9",
        }
        base.update(overrides)
        return base

    def test_excludes_personal_identifiers_beyond_review_sequence(self) -> None:
        reviews = _parse_reviews([self._raw_review()])

        self.assertEqual(
            set(reviews[0]),
            {
                "review_source_id", "content", "score", "date", "size", "user_size",
                "helpful_count", "images", "partner_comment", "size_survey", "is_blind",
            },
        )

    def test_caps_content_length_at_five_hundred_characters(self) -> None:
        reviews = _parse_reviews([self._raw_review()])

        self.assertLessEqual(len(reviews[0]["content"]), 500)

    def test_converts_relative_image_paths_and_keeps_photo_signal(self) -> None:
        reviews = _parse_reviews([self._raw_review()])

        self.assertEqual(reviews[0]["images"], ["https://img.29cm.co.kr/reviews/a.jpg"])

    def test_excludes_deleted_images_from_photo_signal(self) -> None:
        reviews = _parse_reviews([self._raw_review(uploadFiles=[
            {"url": "/reviews/a.jpg", "isDeleted": "F"},
            {"url": "/reviews/deleted.jpg", "isDeleted": "T"},
        ])])

        self.assertEqual(reviews[0]["images"], ["https://img.29cm.co.kr/reviews/a.jpg"])

    def test_photo_signal_is_empty_list_when_no_uploaded_files(self) -> None:
        reviews = _parse_reviews([self._raw_review(uploadFiles=[])])

        self.assertEqual(reviews[0]["images"], [])

    def test_keeps_absolute_image_urls_unchanged(self) -> None:
        reviews = _parse_reviews([self._raw_review(uploadFiles=[
            {"url": "https://cdn.example.com/a.jpg", "isDeleted": "F"},
        ])])

        self.assertEqual(reviews[0]["images"], ["https://cdn.example.com/a.jpg"])

    def test_strips_type_tag_prefix_from_option_value(self) -> None:
        reviews = _parse_reviews([self._raw_review(optionValue=["[SIZE] 230mm", "[COLOR] 블랙"])])

        self.assertEqual(reviews[0]["size"], "230mm, 블랙")

    def test_maps_size_survey_code_to_label(self) -> None:
        small = _parse_reviews([self._raw_review(surveyList=[{"surveyType": "SIZE", "optionValue": 1}])])
        fits = _parse_reviews([self._raw_review(surveyList=[{"surveyType": "SIZE", "optionValue": 2}])])
        big = _parse_reviews([self._raw_review(surveyList=[{"surveyType": "SIZE", "optionValue": 3}])])

        self.assertEqual(small[0]["size_survey"], "작음")
        self.assertEqual(fits[0]["size_survey"], "맞음")
        self.assertEqual(big[0]["size_survey"], "큼")

    def test_size_survey_is_none_when_no_size_survey_present(self) -> None:
        reviews = _parse_reviews([self._raw_review(surveyList=[])])

        self.assertIsNone(reviews[0]["size_survey"])

    def test_review_source_id_defaults_to_empty_string_when_missing(self) -> None:
        raw = self._raw_review()
        del raw["itemReviewNo"]

        reviews = _parse_reviews([raw])

        self.assertEqual(reviews[0]["review_source_id"], "")


if __name__ == "__main__":
    unittest.main()
