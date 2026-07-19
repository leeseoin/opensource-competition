import json
import time
import traceback
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter

from app.services.crawler_service import SUPPORTED_SITES, CrawlerService

router = APIRouter()


def _analyze(products: list[dict]) -> dict:
    if not products:
        return {"total": 0, "price_missing": 0, "title_missing": 0, "duplicates": 0, "price_range": None}

    prices = []
    seen_links: set[str] = set()
    price_missing = 0
    title_missing = 0
    duplicates = 0

    for p in products:
        price_str = p.get("price", "")
        title = p.get("title", "")
        link = p.get("link", "")

        if not price_str:
            price_missing += 1

        if not title:
            title_missing += 1

        if link:
            if link in seen_links:
                duplicates += 1
            else:
                seen_links.add(link)

        nums = "".join(c for c in price_str if c.isdigit())
        if nums:
            prices.append(int(nums))

    return {
        "total": len(products),
        "price_missing": price_missing,
        "title_missing": title_missing,
        "duplicates": duplicates,
        "price_range": {
            "min": f"{min(prices):,}원",
            "max": f"{max(prices):,}원",
            "avg": f"{int(sum(prices)/len(prices)):,}원",
        } if prices else None,
    }


def _build_report(
    keyword: str, site: str, ts: str,
    elapsed: float, products: list[dict], errors: list[str], analysis: dict,
    max_items: int, output_file: str, raw_file: str,
) -> str:
    site_name = {"musinsa": "무신사", "abcmart": "ABC마트"}.get(site, site)
    pr = analysis.get("price_range")
    price_range_str = (
        f"최저 {pr['min']} / 최고 {pr['max']} / 평균 {pr['avg']}" if pr else "N/A"
    )
    error_section = "\n".join(f"- {e}" for e in errors) if errors else "- 없음"

    suggestions: list[str] = []
    found = analysis["total"]
    if found < max_items:
        suggestions.append(
            f"요청 {max_items}개 중 {found}개만 수집됨 → 페이지네이션 파라미터 재확인 또는 스크롤 횟수 증가 필요"
        )
    if analysis["duplicates"] > 0:
        suggestions.append(f"중복 항목 {analysis['duplicates']}개 → 상품 ID 기반 중복 제거 로직 강화 필요")
    if analysis["price_missing"] > 0:
        suggestions.append(f"가격 누락 {analysis['price_missing']}개 → CSS 선택자 재확인 필요")
    if analysis["title_missing"] > 0:
        suggestions.append(f"제목 누락 {analysis['title_missing']}개 → img alt 또는 대체 텍스트 탐색 필요")
    if errors:
        suggestions.append(f"에러 {len(errors)}건 발생 → 에러 로그 섹션 참조")
    if elapsed > 30:
        suggestions.append(f"응답 시간 {elapsed}s 초과 → 비동기 병렬 페이지 크롤링 고려")
    if not suggestions:
        suggestions.append("현재 크롤링 품질 양호 — 이상 없음")

    suggestion_str = "\n".join(f"- {s}" for s in suggestions)

    return f"""# 크롤링 분석 리포트

## 기본 정보
| 항목 | 값 |
|------|-----|
| 사이트 | {site_name} ({site}) |
| 키워드 | {keyword} |
| 요청 시각 | {ts} |
| 최대 요청 수 | {max_items}개 |

## 수집 결과
| 항목 | 값 |
|------|-----|
| 총 소요 시간 | {elapsed}s |
| 수집 상품 수 | {found}개 |
| 가격 범위 | {price_range_str} |
| 가격 누락 | {analysis['price_missing']}개 |
| 제목 누락 | {analysis['title_missing']}개 |
| 중복 항목 | {analysis['duplicates']}개 |

## 저장 파일
- 정제 데이터: `{output_file}`
- 원본 데이터: `{raw_file}`

## 에러 로그
{error_section}

## 개선 제안
{suggestion_str}

---
*자동 생성: Purchase Research Agent*
"""


@router.post("/search")
async def search_products(
    keyword: str,
    site: str = "musinsa",
    max_items: int = 500,
    with_reviews: bool = False,
    reviews_per_item: int = 5,
):
    """
    상품 검색 크롤링 + A-Z 분석 리포트 생성

    - **keyword**: 검색 키워드
    - **site**: `musinsa` 또는 `abcmart`
    - **max_items**: 최대 수집 개수 (기본 500)
    """
    if site not in SUPPORTED_SITES:
        return {"status": "error", "message": f"지원 사이트: {SUPPORTED_SITES}"}

    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    ts_file = datetime.now().strftime("%Y%m%d_%H%M%S")
    started_at = time.time()

    try:
        crawler = CrawlerService()
        products, errors = await crawler.search_items(
            keyword, site, max_items=max_items,
            with_reviews=with_reviews, reviews_per_item=reviews_per_item,
        )
    except Exception:
        tb = traceback.format_exc()
        errors = [f"치명적 오류:\n{tb}"]
        products = []

    elapsed = round(time.time() - started_at, 2)
    analysis = _analyze(products)

    refined = [p for p in products if p.get("price")]
    output_dir = Path("output")
    output_dir.mkdir(exist_ok=True)
    output_file = output_dir / f"{site}_{keyword}_top{max_items}_{ts_file}.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(refined, f, ensure_ascii=False, indent=2)

    raw_file = crawler.save_raw(keyword, site, products, ts_file)

    report_dir = Path("logs/reports")
    report_dir.mkdir(parents=True, exist_ok=True)
    report_file = report_dir / f"{site}_{keyword}_{ts_file}.md"
    report_md = _build_report(
        keyword=keyword, site=site, ts=ts,
        elapsed=elapsed, products=products,
        errors=errors, analysis=analysis,
        max_items=max_items,
        output_file=str(output_file),
        raw_file=raw_file,
    )
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report_md)

    _append_summary_log(ts, site, keyword, elapsed, analysis, bool(errors))

    print(f"[DONE] [{site}] '{keyword}' {len(refined)}개 / {elapsed}s")

    return {
        "status": "ok",
        "site": site,
        "keyword": keyword,
        "timestamp": ts,
        "elapsed_seconds": elapsed,
        "max_requested": max_items,
        "total_found": analysis["total"],
        "returned": len(refined),
        "errors": errors,
        "analysis": analysis,
        "output_file": str(output_file),
        "raw_file": raw_file,
        "report_file": str(report_file),
        "items": refined,
    }


def _append_summary_log(
    ts: str, site: str, keyword: str,
    elapsed: float, analysis: dict, has_error: bool,
) -> None:
    log_file = Path("logs/search_log.md")
    log_file.parent.mkdir(exist_ok=True)
    if not log_file.exists():
        log_file.write_text("# Purchase Research Agent - 크롤링 요청 로그\n\n", encoding="utf-8")

    status = "ERROR" if has_error else "ok"
    pr = analysis.get("price_range")
    price_str = f"{pr['min']}~{pr['max']}" if pr else "N/A"

    block = (
        f"## {ts}  |  {site}  |  {keyword}\n"
        f"- 상태: {status}\n"
        f"- 소요: {elapsed}s\n"
        f"- 수집: {analysis['total']}개\n"
        f"- 가격 범위: {price_str}\n"
        f"- 중복/누락: 중복 {analysis['duplicates']}개 / 가격누락 {analysis['price_missing']}개\n"
        f"\n"
    )
    with open(log_file, "a", encoding="utf-8") as f:
        f.write(block)
