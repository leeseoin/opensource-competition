from fastapi import APIRouter, BackgroundTasks
from app.services.crawler_service import CrawlerService
import json
import asyncio
from pathlib import Path

router = APIRouter()

async def process_heavy_crawling_and_ai(keyword: str):
    print(f"🔄 [백그라운드] 작업 시작: '{keyword}' 크롤링 및 정제")
    
    # 1. 크롤링 (200~1000개 로우 데이터 수집)
    crawler = CrawlerService()
    raw_products = await crawler.search_items(keyword) 
    
    # 2. 파이썬 기반 데이터 정제 (현재 AI 백엔드 미구현이므로 Python 로직으로 대체)
    # 예: 가격 정보가 있는 데이터만 필터링하여 상위 30개로 압축
    refined_products = [p for p in raw_products if p.get("price")]
    top_30_products = refined_products[:30]
    
    # 3. 정제된 결과 JSON 파일 저장
    output_dir = Path("output")
    output_dir.mkdir(exist_ok=True)
    refined_file = output_dir / f"{keyword}_refined_top30.json"
    
    def save_refined():
        with open(refined_file, 'w', encoding='utf-8') as f:
            json.dump(top_30_products, f, ensure_ascii=False, indent=2)
            
    await asyncio.to_thread(save_refined)
    print(f"✅ [백그라운드] '{keyword}' 정제 완료. 상위 30개 추출 후 저장됨: {refined_file}")

@router.post("/search")
async def start_product_search(keyword: str, background_tasks: BackgroundTasks):
    # 크롤링과 데이터 정제는 시간이 오래 걸리므로 BackgroundTasks로 비동기 처리하고 즉시 응답을 반환
    background_tasks.add_task(process_heavy_crawling_and_ai, keyword)
    
    return {
        "status": "processing",
        "message": f"'{keyword}' 상품 탐색 및 정제 작업을 백그라운드에서 시작했습니다. 완료 시 output 폴더에 JSON 파일로 결과가 저장됩니다."
    }
