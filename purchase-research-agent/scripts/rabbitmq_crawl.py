"""scripts/run_queue_crawl.py의 multiprocessing.Queue를 RabbitMQ로 교체한 버전.

동시성 통제 구조는 동일하다 — (키워드, 사이트) 조합을 큐 하나에 넣고, 고정된 수의
워커가 한 번에 하나씩만 꺼내 처리한다(RabbitMQ에서는 채널 `prefetch_count=1`로
구현). 실제 크롤링 로직(app/services/crawl_job.py의 crawl_one)은 두 스크립트가
그대로 공유한다 — 바뀐 건 큐 트랜스포트뿐이다.

multiprocessing.Queue 버전과 달리 워커가 프로세스 경계뿐 아니라 **머신 경계**도
넘을 수 있다 — RabbitMQ가 실제 네트워크 브로커라, 워커를 다른 컴퓨터에서 띄워도
동시성 통제(= 워커 수)는 그대로 유지된다.

주의: 이 환경엔 Docker가 없어 브로커를 못 띄웠다. import/문법 수준까지만
검증했고, 실제 RabbitMQ에 연결해 끝까지 돌려본 적은 없다(2026-08-07 기준).
Docker Desktop 설치 후 `docker compose up -d rabbitmq`로 브로커를 띄우고
아래 사용법대로 검증해야 한다.

사용법 (터미널 여러 개, 또는 여러 머신):
    # 1) 잡을 큐에 채운다 (한 번만 실행)
    python scripts/rabbitmq_crawl.py enqueue "구두,스니커즈" 250 30 4

    # 2) 워커를 worker_count(위에서 4)개만큼 띄운다 (터미널별로 하나씩, 또는 다른 머신에서)
    python scripts/rabbitmq_crawl.py worker 0 <run_id>
    python scripts/rabbitmq_crawl.py worker 1 <run_id>
    ...

    # 3) 워커가 전부 끝나면 결과를 취합한다
    python scripts/rabbitmq_crawl.py aggregate "구두,스니커즈" <run_id>

같은 머신에서 빠르게 검증하고 싶으면 한 번에 다 처리하는 편의 모드도 있다:
    python scripts/rabbitmq_crawl.py local-demo "구두,스니커즈" 250 30 4
"""

import asyncio
import json
import multiprocessing as mp
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import aio_pika
from dotenv import load_dotenv

from app.normalizer import normalize
from app.services.contract_validation import validate_items
from app.services.crawl_job import crawl_one

load_dotenv()

_QUEUE_NAME = "crawl_jobs"
_RAW_DIR = Path("output/rabbitmq_raw")
_STOP_SENTINEL = "__STOP__"


def _rabbitmq_url() -> str:
    """루트 .env.example의 PURCHASE_RESEARCH_RABBITMQ_URL 규약을 그대로 따른다
    (docker compose의 RabbitMQ 기본 계정/포트/vhost와 일치)."""
    return os.environ.get(
        "PURCHASE_RESEARCH_RABBITMQ_URL",
        "amqp://purchase_research:purchase_research@127.0.0.1:35672/purchase_research",
    )


async def _enqueue(keywords: list[str], max_items: int, detail_limit: int, worker_count: int, run_id: str) -> None:
    from app.crawlers import SITE_CRAWLERS

    sites = list(SITE_CRAWLERS)
    jobs = [(keyword, site) for keyword in keywords for site in sites]

    connection = await aio_pika.connect_robust(_rabbitmq_url())
    async with connection:
        channel = await connection.channel()
        queue = await channel.declare_queue(_QUEUE_NAME, durable=True)

        for keyword, site in jobs:
            payload = {
                "run_id": run_id,
                "keyword": keyword,
                "site": site,
                "max_items": max_items,
                "detail_limit": detail_limit,
            }
            await channel.default_exchange.publish(
                aio_pika.Message(
                    body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key=queue.name,
            )

        # 워커 수만큼 종료 센티널을 같이 넣는다 — multiprocessing 버전의
        # job_queue.put(None) * worker_count 와 동일한 역할.
        for _ in range(worker_count):
            await channel.default_exchange.publish(
                aio_pika.Message(body=_STOP_SENTINEL.encode("utf-8")),
                routing_key=queue.name,
            )

    print(f"[ENQUEUE] run_id={run_id} 조합 {len(jobs)}개 + 종료 센티널 {worker_count}개를 '{_QUEUE_NAME}'에 등록")


async def _worker(worker_id: int, expected_run_id: str) -> None:
    """조합을 한 번에 하나씩만 처리한다(prefetch_count=1) — 워커가 몇 개 떠 있든
    시스템 전체 동시 처리량은 '기동한 워커 수'로 고정된다."""
    _RAW_DIR.mkdir(parents=True, exist_ok=True)

    connection = await aio_pika.connect_robust(_rabbitmq_url())
    async with connection:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)
        queue = await channel.declare_queue(_QUEUE_NAME, durable=True)

        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                async with message.process():
                    body = message.body.decode("utf-8")
                    if body == _STOP_SENTINEL:
                        print(f"[WORKER {worker_id}] 종료 센티널 수신, 종료")
                        break

                    job = json.loads(body)
                    if job.get("run_id") != expected_run_id:
                        # 다른 run의 잔여 메시지 — 재발행해두고 넘어간다(간단한 안전장치).
                        await channel.default_exchange.publish(
                            aio_pika.Message(body=message.body),
                            routing_key=queue.name,
                        )
                        continue

                    keyword, site = job["keyword"], job["site"]
                    t0 = time.perf_counter()
                    print(f"[WORKER {worker_id}] 시작: {keyword}/{site}")
                    try:
                        products, errors = await crawl_one(
                            site, keyword, job["max_items"], job["detail_limit"]
                        )
                    except Exception as e:
                        products, errors = [], [f"{keyword}/{site} 워커 예외: {e}"]
                    elapsed = time.perf_counter() - t0
                    print(
                        f"[WORKER {worker_id}] 완료: {keyword}/{site} {len(products)}개 "
                        f"({elapsed:.1f}s, 오류 {len(errors)}건)"
                    )

                    out = _RAW_DIR / f"{expected_run_id}_{site}_{keyword}_w{worker_id}.json"
                    out.write_text(
                        json.dumps(
                            {
                                "keyword": keyword,
                                "site": site,
                                "products": products,
                                "errors": errors,
                                "elapsed": elapsed,
                            },
                            ensure_ascii=False,
                        ),
                        encoding="utf-8",
                    )


def _aggregate(keywords: list[str], run_id: str) -> None:
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
    out_file = out_dir / f"rabbitmq_unified_{keyword_label}_{len(unified)}_{run_id}.json"
    out_file.write_text(json.dumps(unified, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n총 {len(unified)}개 통합 수집, 오류 {len(all_errors)}건")
    print(f"저장: {out_file}")


def _local_demo_worker_entry(worker_id: int, run_id: str) -> None:
    asyncio.run(_worker(worker_id, run_id))


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        return

    mode = sys.argv[1]

    if mode == "enqueue":
        keywords = [k.strip() for k in sys.argv[2].split(",") if k.strip()]
        max_items = int(sys.argv[3]) if len(sys.argv) > 3 else 250
        detail_limit = int(sys.argv[4]) if len(sys.argv) > 4 else 30
        worker_count = int(sys.argv[5]) if len(sys.argv) > 5 else 4
        run_id = sys.argv[6] if len(sys.argv) > 6 else datetime.now().strftime("%Y%m%d_%H%M%S")
        asyncio.run(_enqueue(keywords, max_items, detail_limit, worker_count, run_id))
        print(f"run_id={run_id}")

    elif mode == "worker":
        worker_id = int(sys.argv[2])
        run_id = sys.argv[3]
        asyncio.run(_worker(worker_id, run_id))

    elif mode == "aggregate":
        keywords = [k.strip() for k in sys.argv[2].split(",") if k.strip()]
        run_id = sys.argv[3]
        _aggregate(keywords, run_id)

    elif mode == "local-demo":
        keywords = [k.strip() for k in sys.argv[2].split(",") if k.strip()]
        max_items = int(sys.argv[3]) if len(sys.argv) > 3 else 250
        detail_limit = int(sys.argv[4]) if len(sys.argv) > 4 else 30
        worker_count = int(sys.argv[5]) if len(sys.argv) > 5 else 4
        run_id = datetime.now().strftime("%Y%m%d_%H%M%S")

        asyncio.run(_enqueue(keywords, max_items, detail_limit, worker_count, run_id))

        workers = [
            mp.Process(target=_local_demo_worker_entry, args=(i, run_id))
            for i in range(worker_count)
        ]
        for w in workers:
            w.start()
            time.sleep(1.5)  # run_queue_crawl.py와 동일한 이유(동시 기동 DNS 폭주 방지)
        for w in workers:
            w.join()

        _aggregate(keywords, run_id)

    else:
        print(f"알 수 없는 모드: {mode}")
        print(__doc__)


if __name__ == "__main__":
    main()
