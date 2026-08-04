# ABC마트 Crawl Contract v1-abcmart

작성일: 2026-07-28

## 이건 뭔가

`contracts/collector/v1`(collector-result.schema.json 등)은 아직 구현되지 않은
"Go Collector + RabbitMQ" 구조를 위한 계약이다. 지금 실제로 동작하는 파이프라인은
그것과 무관하게 별도로 존재한다.

```text
purchase-research-agent (FastAPI, Python)
  └─ AbcMartCrawler.crawl_category()  ABC마트 실제 크롤링 (crawl4ai + BeautifulSoup)
          │  raw dict: source_product_id, title, price="89,000원" 문자열 ...
          ↓ POST /api/v1/products/batch
purchase-research-backend (Spring Boot, MySQL)
  └─ ProductIngestService  product / price_history / product_option / product_review 적재
```

이 디렉토리는 위 실제 파이프라인의 JSON 모양을 검증하기 위한 계약이다. 두 단계를 다룬다.

- `abcmart-crawl-item.schema.json`: `AbcMartCrawler`가 만들어내는 원본 상품 dict 1건
  (파싱 직후, 또는 `attach_details()`로 리뷰/옵션이 붙은 상태).
- `product-batch-request.schema.json`: `POST /api/v1/products/batch`에 실제로 보내는
  요청 본문(`ProductBatchRequest`). 이걸 통과하면 Spring `@NotBlank` 검증과 MySQL
  컬럼 제약(VARCHAR 길이, NOT NULL)도 통과한다.

## 크롤러 → 백엔드 사이에 실제로 발견된 어긋남

코드를 직접 대조해서 찾은 것들이다. "스키마에 안 맞으면"에 해당하는 실제 사례들.

1. **옵션/리뷰가 카테고리 트리거 경로로는 DB에 전혀 안 들어간다.**
   `CrawlTriggerService.toPayload()`(백엔드가 agent의 `/api/v1/search`를 호출하는
   경로)는 `options`, `reviews`를 항상 `null`로 고정해서 넘긴다. `/api/v1/category`
   + `detail_limit`로 수집한 리뷰/옵션을 백엔드로 넣으려면 지금은 `/api/v1/products/batch`를
   직접 호출하는 별도 코드가 필요하다 — 연결된 경로가 없다.
2. **리뷰의 `review_source_id`가 `null`일 수 있다.**
   `DetailFetcher._parse_review()`는 ABC마트 응답에 `prdtRvwSeq`가 없으면
   `review_source_id`를 `None`으로 만든다. 그런데 DB `product_review.review_source_id`는
   `NOT NULL`이고 `ReviewPayload.reviewSourceId`는 `@NotBlank`다 — 이 리뷰는 배치
   적재 시 해당 상품 전체가 아니라 그 리뷰 하나만 조용히 빠지는 게 아니라 예외로
   실패 처리된다(`ProductIngestService.ingestOne`은 상품 단위 try/catch).
3. **필드 이름이 다르다.** 크롤러 리뷰 dict는 `date` 키를 쓰는데 `ReviewPayload`는
   `reviewDate`를 기대한다. 지금은 이 경로 자체가 연결 안 돼 있어서(#1) 드러나지
   않지만, 나중에 연결하면 그대로 두면 `reviewDate`가 항상 비게 된다.
4. **가격이 문자열이다.** 크롤러는 `"89,000원"`처럼 통화 기호가 붙은 문자열을 주고,
   `CrawlTriggerService.parsePrice()`가 숫자만 뽑아 `Integer`로 바꾼다. 빈 문자열이면
   `null`이 된다 — `product.price`가 `null`인 상품은 `price_history`에도 안 남는다
   (`ProductIngestService.ingestOne`: `if (payload.price() != null)`).
5. **`color` 필드는 크롤러가 만들어내지만 어디에도 저장되지 않는다.**
   `ProductPayload`, DDL 어디에도 대응 컬럼이 없다. `options.colors[]`와는 별개다.

## 검증

```bash
cd contracts/collector/v1-abcmart
python -m pip install jsonschema  # 최초 1회

python -c "
import json, jsonschema
schema = json.load(open('abcmart-crawl-item.schema.json', encoding='utf-8'))
data = json.load(open('examples/abcmart-crawl-item.valid.json', encoding='utf-8'))
jsonschema.validate(data, schema)
print('ok')
"
```

실제 크롤링 결과 파일 전체를 검사하려면 `purchase-research-agent/scripts/validate_abcmart_crawl.py`를 쓴다.

## 변경 정책

`contracts/collector/v1`과 동일하게, 필드 의미나 필수 여부를 바꾸는 변경은 새 버전
디렉토리(`v2-abcmart` 등)에서 진행하고, 실제 DTO(`purchase-research-backend/.../dto/`)나
DDL(`V1__init_schema.sql`)이 바뀌면 이 스키마도 같은 커밋에서 갱신한다.
