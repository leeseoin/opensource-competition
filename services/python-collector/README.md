# Python Collector 비교 구현

작성일: 2026-08-03
상태: Python/Go 성능 비교 PoC

## 출처와 이식 범위

이 코드는 `origin/dev-jw`의 `e2d863c` 커밋에서 다음 부분만 선별해 현재 구조에 맞게
이식했다.

- `purchase-research-agent/app/crawlers/base.py`
- `purchase-research-agent/app/crawlers/abcmart/`
- `purchase-research-agent/app/crawlers/cm29/`
- `purchase-research-agent/app/services/contract_validation.py`

AgentPay, 이전 Java Backend, 추천기와 API 화면 코드는 가져오지 않았다. 원본 Python
ABC마트 구현은 browser와 HTML을 사용했지만, 현재 Go 구현과 같은 공개 검색 JSON을
사용해야 요청량과 parser 조건을 더 가깝게 비교할 수 있어 JSON Adapter로 바꿨다.
29CM은 원본과 동일하게 검색 화면이 사용하는 JSON 응답을 해석한다.

## 제공 기능

- ABC마트/29CM 페이지 수집
- 판매처와 검색어 전체에서 상품 ID 중복 제거
- 최대 10,000개 고유 상품 상한
- 요청 간격/timeout/retry/요청 예산
- 401/403/429 즉시 중단
- gzip NDJSON 결과와 JSON checkpoint 저장
- 중단 후 `--resume` 재개
- `v1-unified` Schema 검증과 누락 필드 통계

## 설치와 테스트

```bash
cd services/python-collector
uv sync
uv run python -m unittest discover -s tests -v
```

## 실수집

대량 수집 전에 100건부터 실행한다. 결과와 checkpoint는 Git에서 제외된 루트 `tmp/`
아래에 저장한다.

```bash
cd services/python-collector

uv run purchase-python-collector \
  --merchant abcmart \
  --query 구두 \
  --max-items 100 \
  --request-budget 10 \
  --output-dir ../../tmp/python-collector/abcmart-100
```

29CM은 `--merchant 29cm`를 사용한다. 같은 실행을 이어갈 때는 같은 출력 디렉토리에
`--resume`을 추가한다. 1,000건과 10,000건은 앞 단계의 오류/429/계약 실패를 확인한
뒤에만 순차 실행한다.

## 저장 fixture parser benchmark

실제 네트워크 시간과 언어의 JSON decode/정규화 비용을 분리하기 위해 Go와 같은
`services/collector/testdata` fixture를 반복 처리한다. 측정 범위는 fixture bytes의
JSON decode, 판매처별 공통 상품 변환과 `v1-unified` Contract 검증이다.

```bash
uv run python -m purchase_collector.benchmark --merchant abcmart --iterations 1000 --warmup 100
uv run python -m purchase_collector.benchmark --merchant 29cm --iterations 1000 --warmup 100
```
