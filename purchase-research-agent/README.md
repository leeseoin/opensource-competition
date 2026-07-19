# Purchase Research Agent

ABC마트 상품 정보(가격·리뷰·옵션)를 카테고리 기반으로 수집하는 FastAPI 크롤링 서버입니다.

## 개요

- ABC마트 카테고리 페이지를 탐색해 상품 목록을 수집합니다.
- 수집된 상품 중 상위 N개에 대해 리뷰·옵션(사이즈/색상)을 API로 추가 수집합니다.
- 결과는 JSON 파일로 저장되며, Markdown 리포트도 자동 생성됩니다.

## 기술 스택

- **FastAPI** + uvicorn
- **Crawl4AI** (Playwright 기반 브라우저 크롤링)
- **httpx** (리뷰·옵션 API 호출)
- Python 3.12

## 실행 방법

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8012
```

## API 엔드포인트

### `GET /api/v1/categories`
지원하는 카테고리 목록 반환

### `POST /api/v1/category`
카테고리 기반 상품 수집

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `category` | 필수 | 카테고리 키 (예: `스니커즈_남성`) |
| `max_items` | 500 | 최대 수집 상품 수 |
| `detail_limit` | 10 | 리뷰·옵션을 수집할 상위 상품 수 (0이면 미수집) |

**응답 예시**
```json
{
  "status": "ok",
  "total_found": 40,
  "returned": 40,
  "elapsed_seconds": 35.2,
  "items": [
    {
      "title": "상품명",
      "brand": "브랜드명",
      "price": "00,000원",
      "link": "https://www.abcmart.co.kr/product?prdtNo=XXXXXXXXXX",
      "review_count": 10,
      "reviews": [
        {
          "content": "리뷰 내용 (최대 200자)",
          "score": 5,
          "date": "2026-00-00",
          "size": "000"
        }
      ],
      "options": {
        "colors": ["COLOR1", "COLOR2"],
        "sizes": ["225", "230", "235", "..."]
      }
    }
  ]
}
```

> `detail_limit` 초과 상품은 `title`, `brand`, `price`, `link`만 포함됩니다.

### `POST /api/v1/search`
키워드 기반 검색 (abcmart 전용, 최대 64건 제한)

## 지원 카테고리

| 카테고리 키 | 설명 |
|-------------|------|
| `신발_남성` / `신발_여성` / `신발_아동` | 신발 전체 |
| `스니커즈_남성` / `스니커즈_여성` | 스니커즈 |
| `스포츠_남성` / `스포츠_여성` | 스포츠화 |
| `러닝화_남성` / `러닝화_여성` | 러닝화 |
| `샌들_남성` / `샌들_여성` | 샌들 |
| `부츠_남성` / `부츠_여성` | 부츠 |
| `구두_남성` / `구두_여성` | 구두 |
| `의류_남성` / `의류_여성` | 의류 |
| `잡화_남성` / `잡화_여성` | 잡화 |

## 수집 구조

```
AbcMartCrawler          → 카테고리 페이지 크롤링 (Crawl4AI)
DetailFetcher           → 리뷰/옵션 API 호출 (httpx, 5초 딜레이)
CrawlerService          → 위 두 모듈 조합
API Endpoint (/category) → 결과 JSON + Markdown 리포트 저장
```

## 딜레이 정책

- 카테고리 페이지 간: `asyncio.sleep(1)` + Crawl4AI 브라우저 로드 (~3~4초) = 실효 4~5초
- 상품 리뷰/옵션 API 간: `asyncio.sleep(5)` (요청당)
- ABC Mart `robots.txt`: `User-agent: * / Allow: /` (전체 허용)

## 출력 파일

- `output/{site}_{category}_top{n}_{timestamp}.json` — 정제 데이터
- `output/raw/{site}_{category}_raw_{timestamp}.json` — 원본 데이터
- `logs/reports/{site}_{category}_{timestamp}.md` — 분석 리포트
- `logs/search_log.md` — 누적 요청 로그

> `output/`, `logs/`는 `.gitignore`에 등록되어 git에 포함되지 않습니다.
