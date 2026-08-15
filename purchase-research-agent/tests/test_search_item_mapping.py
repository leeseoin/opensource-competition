"""판매처 검색 JSON의 category, 판매 가능 옵션과 재고 매핑을 검증한다."""

import unittest

from app.crawlers.abcmart.json_fetcher import AbcJsonFetcher
from app.crawlers.cm29.crawler import Cm29Crawler


class SearchItemMappingTests(unittest.TestCase):
    """Queue 하드필터가 사용하는 판매처 원본 필드가 과장 없이 변환되는지 검증한다."""

    def test_abcmart_keeps_only_sizes_with_positive_stock(self) -> None:
        """ABC마트가 품절 사이즈를 제외하고 category와 상품 재고를 보존하는지 검증한다."""

        product = AbcJsonFetcher()._parse_item({
            "PRDT_NO": "abc-1",
            "PRDT_NAME": "검정 구두",
            "PRDT_DC_PRICE": "99000",
            "CTGR_NAME_ALL": "신발 > 구두 > 로퍼",
            "PRDT_OPTION": "260,265,270",
            "SIZE_LIST": {"260": "0", "265": "3", "270": "0"},
            "COLOR_ID": "BLACK",
            "SOLD_OUT": "n",
        })

        self.assertEqual(product["options"]["sizes"], ["265"])
        self.assertEqual(product["options"]["available_sizes"], ["265"])
        self.assertEqual(product["category"], "로퍼")
        self.assertTrue(product["in_stock"])

    def test_29cm_preserves_listing_stock_and_category(self) -> None:
        """29CM listing의 displayPrice, 품절 상태와 category path를 보존하는지 검증한다."""

        products = Cm29Crawler()._parse([{
            "itemId": 29,
            "itemInfo": {
                "productName": "재킷",
                "displayPrice": 129000,
                "brandName": "테스트",
                "isSoldOut": True,
                "reviewCount": 7,
            },
            "itemUrl": {"webLink": "https://www.29cm.co.kr/products/29"},
            "itemEvent": {"eventProperties": {
                "largeCategoryName": "의류",
                "middleCategoryName": "아우터",
                "smallCategoryName": "재킷",
            }},
        }])

        self.assertEqual(products[0]["price"], "129,000원")
        self.assertEqual(products[0]["category_path"], "의류 > 아우터 > 재킷")
        self.assertFalse(products[0]["in_stock"])


if __name__ == "__main__":
    unittest.main()
