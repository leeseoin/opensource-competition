"""29CM crawl_category가 대표 검색어 결과를 대분류/중분류명으로 정확히 걸러내는지 검증한다."""

from __future__ import annotations

import json
import unittest
from unittest.mock import patch

import httpx

from app.crawlers.cm29.crawler import (
    CATEGORIES,
    LARGE_CATEGORY_CODES,
    SHOE_CATEGORY_CODES,
    Cm29Crawler,
    _matches_category,
)


class MatchesCategoryTests(unittest.TestCase):
    """category_path 단계 비교가 부분 문자열이 아니라 정확한 단계 일치인지 검증한다."""

    def test_matches_when_large_and_middle_both_present(self) -> None:
        product = {"category_path": "여성슈즈 > 샌들 > 스트랩샌들"}

        self.assertTrue(_matches_category(product, "여성슈즈", "샌들"))

    def test_does_not_match_different_gender(self) -> None:
        product = {"category_path": "남성슈즈 > 샌들 > 스트랩샌들"}

        self.assertFalse(_matches_category(product, "여성슈즈", "샌들"))

    def test_does_not_match_different_middle_category(self) -> None:
        product = {"category_path": "여성슈즈 > 부츠 > 롱부츠"}

        self.assertFalse(_matches_category(product, "여성슈즈", "샌들"))

    def test_does_not_match_partial_substring(self) -> None:
        """"샌들"이 다른 단계 이름의 부분 문자열로만 있을 때 오탐하지 않는지 검증한다."""

        product = {"category_path": "여성슈즈 > 슬링백샌들여름 > 기타"}

        self.assertFalse(_matches_category(product, "여성슈즈", "샌들"))

    def test_missing_category_path_does_not_match(self) -> None:
        self.assertFalse(_matches_category({}, "여성슈즈", "샌들"))


def _listing_item(item_id: str, large: str, middle: str, small: str) -> dict:
    """listing API 응답 한 건을 흉내 낸다."""

    return {
        "itemId": item_id,
        "itemInfo": {
            "productName": f"상품{item_id}",
            "brandName": "테스트브랜드",
            "sellPrice": 10000,
        },
        "itemUrl": {"webLink": f"https://www.29cm.co.kr/products/{item_id}"},
        "itemEvent": {
            "eventProperties": {
                "largeCategoryName": large,
                "middleCategoryName": middle,
                "smallCategoryName": small,
            }
        },
    }


def _listing_body(items: list[dict], has_next: bool) -> bytes:
    payload = {
        "meta": {"result": "SUCCESS"},
        "data": {
            "list": items,
            "pagination": {"hasNext": has_next, "totalCount": len(items)},
        },
    }
    return json.dumps(payload).encode("utf-8")


def _detail_html(item_id: str) -> str:
    payload = {
        "@context": "https://schema.org",
        "@type": "Product",
        "sku": item_id,
        "name": f"상품{item_id}",
        "brand": {"@type": "Brand", "name": "테스트브랜드"},
        "image": [],
        "offers": {"price": 10000, "url": f"https://www.29cm.co.kr/products/{item_id}"},
    }
    return (
        '<html><head><script type="application/ld+json">'
        f"{json.dumps(payload, ensure_ascii=False)}"
        "</script></head><body></body></html>"
    )


class CrawlCategoryTests(unittest.IsolatedAsyncioTestCase):
    """crawl_category가 대표 검색어 검색 결과를 사후 필터링하는지 검증한다."""

    async def test_returns_error_without_any_request_for_unknown_category(self) -> None:
        calls: list[str] = []

        def handler(request: httpx.Request) -> httpx.Response:
            calls.append(str(request.url))
            return httpx.Response(500)

        real_async_client = httpx.AsyncClient

        def fake_async_client(*args, **kwargs) -> httpx.AsyncClient:
            return real_async_client(headers=kwargs.get("headers"), transport=httpx.MockTransport(handler))

        with patch("app.crawlers.cm29.crawler.httpx.AsyncClient", side_effect=fake_async_client):
            products, errors = await Cm29Crawler().crawl_category("존재하지않는카테고리", 5)

        self.assertEqual(products, [])
        self.assertIn("알 수 없는 카테고리", errors[0])
        self.assertEqual(calls, [], "알 수 없는 카테고리는 네트워크 요청 없이 즉시 실패해야 한다")

    async def test_filters_out_products_from_other_categories_on_same_page(self) -> None:
        page1 = [
            _listing_item("1", "여성슈즈", "샌들", "스트랩샌들"),
            _listing_item("2", "여성슈즈", "부츠", "롱부츠"),
            _listing_item("3", "남성슈즈", "샌들", "슬라이드"),
            _listing_item("4", "여성슈즈", "샌들", "슬링백샌들"),
        ]

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "POST":
                return httpx.Response(200, content=_listing_body(page1, has_next=False))
            item_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, text=_detail_html(item_id))

        real_async_client = httpx.AsyncClient

        def fake_async_client(*args, **kwargs) -> httpx.AsyncClient:
            return real_async_client(headers=kwargs.get("headers"), transport=httpx.MockTransport(handler))

        with patch("app.crawlers.cm29.crawler.httpx.AsyncClient", side_effect=fake_async_client):
            products, errors = await Cm29Crawler().crawl_category("샌들_여성", 10)

        self.assertEqual(
            sorted(p["source_product_id"] for p in products), ["1", "4"],
            "여성슈즈>샌들에 속한 상품만 남아야 한다",
        )

    async def test_stops_paginating_once_no_more_products(self) -> None:
        page1 = [_listing_item("1", "여성슈즈", "샌들", "스트랩샌들")]
        pages = iter([page1, []])

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "POST":
                items = next(pages, [])
                return httpx.Response(200, content=_listing_body(items, has_next=bool(items)))
            item_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, text=_detail_html(item_id))

        real_async_client = httpx.AsyncClient

        def fake_async_client(*args, **kwargs) -> httpx.AsyncClient:
            return real_async_client(headers=kwargs.get("headers"), transport=httpx.MockTransport(handler))

        with patch("app.crawlers.cm29.crawler.httpx.AsyncClient", side_effect=fake_async_client):
            products, errors = await Cm29Crawler().crawl_category("샌들_여성", 100)

        self.assertEqual(len(products), 1, "빈 페이지를 만나면 더 요청하지 않고 멈춰야 한다")

    async def test_deduplicates_repeated_products_across_pages(self) -> None:
        page1 = [_listing_item("1", "여성슈즈", "샌들", "스트랩샌들")]
        page2 = [
            _listing_item("1", "여성슈즈", "샌들", "스트랩샌들"),  # 페이지 넘김 중 재등장
            _listing_item("2", "여성슈즈", "샌들", "슬링백샌들"),
        ]
        pages = iter([page1, page2, []])

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "POST":
                items = next(pages, [])
                return httpx.Response(200, content=_listing_body(items, has_next=bool(items)))
            item_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, text=_detail_html(item_id))

        real_async_client = httpx.AsyncClient

        def fake_async_client(*args, **kwargs) -> httpx.AsyncClient:
            return real_async_client(headers=kwargs.get("headers"), transport=httpx.MockTransport(handler))

        with patch("app.crawlers.cm29.crawler.httpx.AsyncClient", side_effect=fake_async_client):
            products, errors = await Cm29Crawler().crawl_category("샌들_여성", 100)

        self.assertEqual(sorted(p["source_product_id"] for p in products), ["1", "2"])

    async def test_stops_once_max_items_reached(self) -> None:
        page1 = [
            _listing_item("1", "여성슈즈", "샌들", "스트랩샌들"),
            _listing_item("2", "여성슈즈", "샌들", "슬링백샌들"),
            _listing_item("3", "여성슈즈", "샌들", "플립플랍"),
        ]
        pages = iter([page1])

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "POST":
                items = next(pages, [])
                return httpx.Response(200, content=_listing_body(items, has_next=bool(items)))
            item_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, text=_detail_html(item_id))

        real_async_client = httpx.AsyncClient

        def fake_async_client(*args, **kwargs) -> httpx.AsyncClient:
            return real_async_client(headers=kwargs.get("headers"), transport=httpx.MockTransport(handler))

        with patch("app.crawlers.cm29.crawler.httpx.AsyncClient", side_effect=fake_async_client):
            products, errors = await Cm29Crawler().crawl_category("샌들_여성", 2)

        self.assertEqual(len(products), 2)


class CategoriesDictTests(unittest.TestCase):
    """CATEGORIES의 각 항목이 (검색어, 대분류명, 중분류명) 3-tuple 형태인지 검증한다."""

    def test_every_entry_has_three_non_empty_strings(self) -> None:
        for name, value in CATEGORIES.items():
            self.assertEqual(len(value), 3, name)
            for part in value:
                self.assertTrue(part, f"{name}: {value}")


class CategoryCodeReferenceTests(unittest.TestCase):
    """LARGE_CATEGORY_CODES/SHOE_CATEGORY_CODES가 CATEGORIES의 이름과 어긋나지 않는지 검증한다."""

    def test_every_categories_large_name_has_a_code(self) -> None:
        for name, (_, large_name, _) in CATEGORIES.items():
            self.assertIn(large_name, LARGE_CATEGORY_CODES, f"{name}: {large_name}")

    def test_every_categories_large_middle_pair_has_a_shoe_code(self) -> None:
        for name, (_, large_name, middle_name) in CATEGORIES.items():
            key = f"{large_name}>{middle_name}"
            self.assertIn(key, SHOE_CATEGORY_CODES, f"{name}: {key}")

    def test_shoe_category_codes_are_numeric_strings(self) -> None:
        for key, code in SHOE_CATEGORY_CODES.items():
            self.assertTrue(code.isdigit(), f"{key}: {code}")

    def test_large_category_codes_are_numeric_strings(self) -> None:
        for key, code in LARGE_CATEGORY_CODES.items():
            self.assertTrue(code.isdigit(), f"{key}: {code}")

    def test_shoe_large_codes_match_large_category_codes(self) -> None:
        """SHOE_CATEGORY_CODES의 "여성슈즈"/"남성슈즈" 코드가 LARGE_CATEGORY_CODES와 같은지 검증한다."""

        self.assertEqual(SHOE_CATEGORY_CODES["여성슈즈"], LARGE_CATEGORY_CODES["여성슈즈"])
        self.assertEqual(SHOE_CATEGORY_CODES["남성슈즈"], LARGE_CATEGORY_CODES["남성슈즈"])


if __name__ == "__main__":
    unittest.main()
