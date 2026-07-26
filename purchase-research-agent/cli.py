"""§3.3 사용자 요청 -> 추천 흐름의 CLI 진입점 (1차 구현).

사용자 요청(자연어)
  -> parse_query() [규칙 기반 Query Analysis Agent 임시 구현]
  -> Spring GET /api/v1/products/search
  -> 10건 미만이면 Spring POST /api/v1/products/crawl-trigger (크롤링+적재)로 보강 후 재조회
  -> recommend() [규칙 기반 Recommendation Agent 임시 구현]
  -> 콘솔 출력

실행: python cli.py
"""

import os

import httpx
from dotenv import load_dotenv

from app.services.query_parser import parse_query
from app.services.recommender import recommend

load_dotenv()

BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8081")
BACKEND_API_KEY = os.getenv("BACKEND_API_KEY", "")
THRESHOLD = 10


def _headers() -> dict:
    return {"X-API-Key": BACKEND_API_KEY} if BACKEND_API_KEY else {}


def _search(client: httpx.Client, keyword: str | None, max_price: int | None) -> dict:
    params = {}
    if keyword:
        params["keyword"] = keyword
    if max_price is not None:
        params["maxPrice"] = max_price
    r = client.get(f"{BACKEND_BASE_URL}/api/v1/products/search", params=params, headers=_headers())
    r.raise_for_status()
    return r.json()


def _trigger_crawl(client: httpx.Client, keyword: str) -> dict:
    r = client.post(
        f"{BACKEND_BASE_URL}/api/v1/products/crawl-trigger",
        params={"keyword": keyword, "site": "abcmart", "maxItems": 30},
        headers=_headers(),
        timeout=120,
    )
    r.raise_for_status()
    return r.json()


def main() -> None:
    print("=== purchase-research-agent CLI (§3.3, 규칙 기반 임시 구현) ===")
    text = input("어떤 상품을 찾으세요? (예: 30만원 이하 러닝화 추천해줘)\n> ").strip()
    if not text:
        print("입력이 없습니다.")
        return

    conditions = parse_query(text)
    print(f"[분석 결과] keyword={conditions['keyword']!r}, max_price={conditions['max_price']}")

    with httpx.Client() as client:
        result = _search(client, conditions["keyword"], conditions["max_price"])
        print(f"[DB 조회] {result['totalCount']}건")

        if result["totalCount"] < THRESHOLD:
            if not conditions["keyword"]:
                print("[임계값 미달] 크롤링에 쓸 키워드가 없어서 트리거를 건너뜁니다.")
            else:
                print(f"[임계값 미달] {THRESHOLD}건 미만이라 크롤링을 트리거합니다 (몇십 초 걸릴 수 있습니다)...")
                trigger_result = _trigger_crawl(client, conditions["keyword"])
                print(
                    f"[크롤링+적재 완료] 성공 {trigger_result['successCount']}건 / "
                    f"실패 {trigger_result['failCount']}건"
                )
                result = _search(client, conditions["keyword"], conditions["max_price"])
                print(f"[재조회] {result['totalCount']}건")

    top = recommend(result["products"])
    if not top:
        print("추천할 상품이 없습니다.")
        return

    print(f"\n=== 추천 상품 (상위 {len(top)}개) ===")
    for i, p in enumerate(top, 1):
        discount = f" ({p['discountPercent']}% 할인)" if p.get("discountPercent") else ""
        price = f"{p['price']:,}원" if p.get("price") is not None else "가격 정보 없음"
        print(f"{i}. {p['title']} | {p.get('brand') or '-'} | {price}{discount}")
        print(f"   {p['link']}")


if __name__ == "__main__":
    main()
