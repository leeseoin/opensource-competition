"""여러 (키워드, 사이트) 조합을 멀티프로세스 워커 풀 + 공유 큐로 처리한다.

문제의식: 별도 OS 프로세스를 여러 개 띄우고 각 프로세스 안에서 asyncio.Semaphore로
동시성을 제한하는 방식은, Semaphore가 프로세스 로컬이라 프로세스 간 전체 동시성을
통제하지 못한다 (2026-08-06 실측: 프로세스 2개를 20초 간격으로 병렬 실행하면 서로
자원을 잠식해 성공률이 급락, 특히 코드를 건드리지 않은 29CM 쪽도 같이 나빠짐).

이 스크립트는 (키워드, 사이트) 조합을 하나의 공유 큐에 담고, 고정된 수의 워커
프로세스가 큐에서 한 번에 하나씩 꺼내 처리하게 한다. 워커는 조합을 절대 동시에
두 개 이상 처리하지 않으므로, 시스템 전체 동시 처리량은 오직 '워커 프로세스 수'로만
결정된다 — 프로세스가 몇 개든 전체 동시성을 큐 하나가 통제하는 구조라, 기존 방식의
"세마포어가 프로세스를 못 넘어간다"는 근본 한계를 비켜간다.

지금은 RabbitMQ 브로커가 이 환경에 없어(Docker 미설치) multiprocessing.Queue로
같은 구조를 검증했다. 실제 크롤링 로직(app/services/crawl_job.py의 crawl_one)은
큐 트랜스포트와 분리해뒀으므로, RabbitMQ로 옮긴 scripts/rabbitmq_crawl.py도 같은
함수를 그대로 재사용한다 — 두 스크립트는 "조합을 한 번에 하나씩 꺼내 처리"하는
워커 로직은 동일하고 큐 트랜스포트(multiprocessing.Queue vs RabbitMQ)만 다르다.

사용법:
    python scripts/run_queue_crawl.py [keyword[,keyword2,...]] [max_items] [detail_limit] [worker_count]
"""

import asyncio
import json
import multiprocessing as mp
import sys
import time
from datetime import datetime
from pathlib import Path

from app.crawlers import SITE_CRAWLERS
from app.normalizer import normalize
from app.services.contract_validation import validate_items
from app.services.crawl_job import crawl_one


def _worker_main(
    worker_id: int,
    job_queue: "mp.Queue",
    max_items: int,
    detail_limit: int,
    run_id: str,
) -> None:
    """워커 프로세스 진입점. 큐가 빌 때까지(None 센티널을 받을 때까지) (keyword, site)
    조합을 하나씩 꺼내 순차 처리한다. 조합 내부의 동시성(브라우저 탭 수 등)은 기존
    크롤러의 세마포어/디스패처 캡을 그대로 따른다 — 이 스크립트가 통제하는 건
    '조합 단위' 동시성뿐이다."""
    _RAW_DIR.mkdir(parents=True, exist_ok=True)

    while True:
        job = job_queue.get()
        if job is None:
            break
        keyword, site = job
        t0 = time.perf_counter()
        started_at = datetime.now().strftime("%H:%M:%S")
        print(f"[WORKER {worker_id}] 시작 {started_at}: {keyword}/{site}")
        try:
            products, errors = asyncio.run(crawl_one(site, keyword, max_items, detail_limit))
        except Exception as e:
            products, errors = [], [f"{keyword}/{site} 워커 예외: {e}"]
        elapsed = time.perf_counter() - t0
        print(f"[WORKER {worker_id}] 완료: {keyword}/{site} {len(products)}개 ({elapsed:.1f}s, 오류 {len(errors)}건)")

        out = _RAW_DIR / f"{run_id}_{site}_{keyword}_w{worker_id}.json"
        out.write_text(
            json.dumps(
                {"keyword": keyword, "site": site, "products": products, "errors": errors, "elapsed": elapsed},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )


def main() -> None:
    raw_keyword = sys.argv[1] if len(sys.argv) > 1 else "구두"
    keywords = [k.strip() for k in raw_keyword.split(",") if k.strip()] or ["구두"]
    max_items = int(sys.argv[2]) if len(sys.argv) > 2 else 250
    detail_limit = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    worker_count = int(sys.argv[4]) if len(sys.argv) > 4 else 4

    sites = list(SITE_CRAWLERS)
    jobs = [(keyword, site) for keyword in keywords for site in sites]
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")

    job_queue: "mp.Queue" = mp.Queue()
    for job in jobs:
        job_queue.put(job)
    for _ in range(worker_count):
        job_queue.put(None)

    print(f"[QUEUE] run_id={run_id} 총 {len(jobs)}개 조합, 워커 {worker_count}개")
    t_start = time.perf_counter()

    workers = [
        mp.Process(target=_worker_main, args=(i, job_queue, max_items, detail_limit, run_id))
        for i in range(worker_count)
    ]
    for w in workers:
        w.start()
        # 워커 전원이 같은 순간 첫 요청을 쏘면 로컬 DNS 조회가 한꺼번에 몰려
        # getaddrinfo failed로 죽는 현상이 실측됐다(2026-08-06). 기동을 흩어
        # 첫 요청이 겹치지 않게 한다.
        time.sleep(1.5)
    for w in workers:
        w.join()

    total_elapsed = time.perf_counter() - t_start
    print(f"[QUEUE] 전체 소요: {total_elapsed:.1f}s")

    # ── 이번 run_id로 워커가 만든 결과만 취합 ──
    unified: list[dict] = []
    all_errors: list[str] = []
    raw_files = sorted(_RAW_DIR.glob(f"{run_id}_*.json"))
    for f in raw_files:
        data = json.loads(f.read_text(encoding="utf-8"))
        products = data.get("products", [])
        errors = data.get("errors", [])
        site = data.get("site")
        keyword = data.get("keyword")
        valid, contract_errors = validate_items(products, site=site)
        all_errors.extend(f"{keyword}/{site}: {e}" for e in errors)
        all_errors.extend(f"{keyword}/{site}: {e}" for e in contract_errors)
        unified.extend(normalize(p) for p in valid)
        print(f"{keyword}/{site}: {len(valid)}개 (원본 {len(products)}개, contract 위반 {len(contract_errors)}건)")

    out_dir = Path("output")
    keyword_label = "_".join(keywords) if len(keywords) <= 3 else f"{keywords[0]}외{len(keywords) - 1}건"
    out_file = out_dir / f"queue_unified_{keyword_label}_{len(unified)}_{run_id}.json"
    out_file.write_text(json.dumps(unified, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n총 {len(unified)}개 통합 수집, 오류 {len(all_errors)}건")
    print(f"저장: {out_file}")


if __name__ == "__main__":
    main()
