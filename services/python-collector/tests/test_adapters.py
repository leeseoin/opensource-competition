"""저장된 판매처 JSON fixture로 Python Adapter의 변환을 검증한다."""

from __future__ import annotations

import json
import unittest

import httpx

from purchase_collector.contract import repository_root, validate_product
from purchase_collector.merchants.abcmart import AbcMartAdapter
from purchase_collector.merchants.twentyninecm import TwentyNineCmAdapter


class MerchantAdapterTests(unittest.IsolatedAsyncioTestCase):
    """네트워크 없이 ABC마트와 29CM의 pagination/필드 변환을 검증한다."""

    async def test_abcmart_fixture_is_converted(self) -> None:
        """ABC마트 검색 fixture가 가격/옵션/재고를 포함한 비교 상품으로 바뀌는지 검증한다."""

        fixture = (repository_root() / "services" / "collector" / "testdata" / "abcmart" / "search-products.json").read_bytes()

        async def handler(request: httpx.Request) -> httpx.Response:
            """ABC마트 fixture를 정상 HTTP 응답처럼 반환한다."""

            self.assertEqual("1", request.url.params["page"])
            self.assertEqual("구두", request.url.params["searchWord"])
            return httpx.Response(200, content=fixture, headers={"content-type": "application/json"})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await AbcMartAdapter().fetch_page(client, "구두", 1, 30)

        self.assertEqual(1650, result.total_count)
        self.assertTrue(result.has_next)
        self.assertEqual("69,000원", result.products[0]["price"])
        self.assertEqual(["250", "255", "260", "265", "270", "275", "280"], result.products[0]["options"]["sizes"])
        self.assertEqual([], validate_product(result.products[0]))

    async def test_29cm_fixture_is_converted(self) -> None:
        """29CM 검색 fixture가 평점/카테고리를 포함한 비교 상품으로 바뀌는지 검증한다."""

        fixture = (repository_root() / "services" / "collector" / "testdata" / "twentyninecm" / "search-items.json").read_bytes()

        async def handler(request: httpx.Request) -> httpx.Response:
            """29CM fixture를 정상 HTTP 응답처럼 반환한다."""

            request_body = json.loads(request.content)
            self.assertEqual(1, request_body["pageRequest"]["page"])
            self.assertEqual("구두", request_body["keyword"])
            return httpx.Response(200, content=fixture, headers={"content-type": "application/json"})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await TwentyNineCmAdapter().fetch_page(client, "구두", 1, 30)

        self.assertEqual(5452, result.total_count)
        self.assertTrue(result.has_next)
        self.assertEqual("100,960원", result.products[0]["price"])
        self.assertEqual("여성슈즈 > 플랫슈즈 > 플랫", result.products[0]["category_path"])
        self.assertEqual([], validate_product(result.products[0]))


if __name__ == "__main__":
    unittest.main()
