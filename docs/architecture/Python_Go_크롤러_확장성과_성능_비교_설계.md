# Python/Go 크롤러 확장성과 성능 비교 설계

작성일: 2026-08-03
상태: 부분 구현

현재 `origin/dev-jw@e2d863c`의 Python ABC마트/29CM 크롤러와 ABC마트 Contract를 원본
그대로 선별 이식했다. 별도 Python 비교 구현은 판매처별 JSON Adapter, pagination,
중복 제거, checkpoint, 요청 예산, 안전 중단과 gzip NDJSON 저장까지 구현됐다. 두 Python
구현의 차이는 `docs/reports/2026-08-04_dev-jw_Python_크롤러_가져오기와_차이_분석.md`에
기록했다. 정우님 Python 결과를 현재 Spring Boot `CollectorResult`로 변환해 수동
PostgreSQL 적재 API로 보내는 연결은 구현했다. 이 연결은 DB 저장 확인용이며
`v1-unified` 성능 비교 Adapter와는 별개다. Python ABC마트 검색은 JSON을
기본 저장값으로 사용하고 같은 검색어와 페이지의 렌더링 HTML을 모든 상품에
대해 대조한다. 비교 상태와 필드별 차이는 Product Backend를 통해
`product_verifications`에 저장하도록 구현했다. 29CM은 검색 HTML에 상품이 없으므로
검색 JSON으로 수집한 모든 상품의 공개 상세 HTML을 요청하고 Product JSON-LD를
비교하도록 구현했다. Go의 동일 수집/비교/저장 기능과
최종 성능 보고서는 Python 수동 E2E 확인 후 구현한다.

## 1. 목표

ABC마트와 29CM의 공개 상품을 Python과 Go로 같은 조건에서 수집하고, 최대 10,000개
고유 상품까지 확장 가능한지 확인한다. 단순히 어느 언어가 빠른지만 보는 것이 아니라
다음 항목을 함께 비교한다.

- 실제로 수집한 고유 상품 수
- 중복률과 누락 필드
- 공통 Contract 통과율
- 외부 요청 수와 오류 수
- 403 및 429 발생 여부
- 전체 소요시간과 초당 처리량
- CPU 시간과 최대 메모리
- 중단 후 checkpoint 재개 가능 여부

## 2. 브랜치와 코드 경계

```text
sandbox/ls
  └── Go Collector 확장과 운영 CollectorResult 유지

sandbox-python-crawler/ls
  └── origin/dev-jw의 Python ABC마트/29CM 크롤러와 Contract만 선별 이식
```

`origin/dev-jw` 전체를 merge하지 않는다. `purchase-research-agent`의 판매처 Adapter와
관련 Contract를 현재 저장소 구조에 맞게 옮기고, AgentPay와 이전 Java Backend 같은
관계없는 코드는 가져오지 않는다.

Java Product Backend와 MCP Server의 책임은 바꾸지 않는다. Python 비교 구현도
PostgreSQL에 직접 쓰지 않으며 운영에 연결할 때는 기존 RabbitMQ 작업/결과 계약과
Product Backend 저장 경계를 따라야 한다.

## 3. Contract 전략

두 종류의 계약을 구분한다.

| 구분 | 위치 | 사용 목적 |
|---|---|---|
| 운영 계약 | `contracts/collector/v1` | Go/Python Collector와 Product Backend 사이의 근거 포함 결과 |
| 비교 계약 | `contracts/collector/unified` | Python과 Go 결과의 동일 필드 정확성 및 성능 비교 |

각 언어의 판매처 Adapter는 운영 모델을 만들고, 별도 비교 Adapter가 이를
`v1-unified`로 변환한다. 비교를 위해 운영 모델의 가격 단위나 출처 구조를 약하게
바꾸지 않는다.

### JSON 기본 수집과 HTML 전수 검증

ABC마트에서 JSON 검색 응답은 상품 ID, 가격, 재고 수량과 페이지 정보를
구조화해 제공하므로 기본 수집 경로로 사용한다. 동일한 검색 페이지를 browser로
렌더링한 HTML은 사용자에게 실제로 보이는 상품 정보와 JSON이 일치하는지
검사하는 두 번째 경로로 사용한다.

비교는 표본 추출이 아니라 해당 실행에서 수집한 모든 JSON 상품을 대상으로 한다.
상품 ID로 JSON과 HTML을 연결하고 상품명, 브랜드, 가격, 정상가, 할인율, 이미지와
상품 URL을 비교한다. 색상과 스타일 코드는 JSON에는 있지만 검색 HTML에 노출되지
않으므로 JSON 상품값으로만 저장하고 HTML 비교 필드에서는 제외한다. HTML 로드
실패나 상품 누락도
성공으로 간주하지 않고 `FAILED` 또는 `MISSING_IN_HTML`로 저장한다.

29CM 검색 페이지는 JavaScript 실행 전과 후 모두 비교할 상품 정보를 HTML에
유지하지 않았다. 반면 공개 상품 상세 HTML은 SEO를 위한 `Product` JSON-LD에
상품 ID, 상품명, 브랜드, 현재가, 정상가, 이미지와 재고 상태를 포함한다.
따라서 29CM은 검색 JSON 상품 하나당 상세 HTML 요청 하나를 순차 실행한다.
이 방식은 브라우저가 필요 없지만 ABC마트의 페이지 단위 비교보다 요청 수와
소요 시간이 크다.

## 4. 10,000개 수집 방식

10,000개는 HTTP 요청 수가 아니라 판매처별 고유 상품 레코드의 최대 목표다.

```text
검색어 또는 허용된 카테고리 목록
        ↓
페이지 작업 생성
        ↓
판매처별 전역 요청 간격 적용
        ↓
상품 ID 기준 중복 제거
        ↓
checkpoint와 압축 NDJSON 저장
        ↓
고유 상품 10,000개 또는 안전 중단 조건 도달
```

한 검색어의 `totalCount`가 10,000보다 작거나 판매처가 접근 가능한 페이지를 제한하면
여러 검색어 또는 공개 카테고리를 사용할 수 있다. 이 경우 검색어/카테고리 목록과
중복 제거 전후 개수를 결과에 기록한다. 상품 수를 맞추려고 같은 상품을 복제하거나
필드를 만들어내지 않는다.

## 5. 단계별 확대와 안전 중단

실수집은 언어별/판매처별로 동시에 실행하지 않고 다음 순서로 확대한다.

1. 100개 smoke
2. 1,000개 안정성 확인
3. 최대 10,000개 수집

기본 원칙은 판매처 전체 최소 요청 간격 1초, timeout 10초에서 15초, 일시 오류 retry
최대 1회다. 실제 값은 robots 정책과 공개 응답 상태를 확인한 뒤 더 보수적으로 조정할
수 있다.

다음 조건에서는 해당 판매처 실수집을 중단한다.

- robots 정책 또는 공개 접근 범위를 벗어남
- 로그인, CAPTCHA 또는 접근 통제가 나타남
- HTTP 401, 403 또는 429 응답
- 동일 구조 오류가 반복돼 parser가 결과를 신뢰할 수 없음
- 설정한 요청 예산 또는 실행시간 상한 도달

차단 우회, User-Agent 위장, proxy 회전, CAPTCHA 우회와 비공개 인증정보 사용은 하지
않는다.

## 6. Queue와 Redis

RabbitMQ는 페이지 또는 검색 단위 작업과 결과 전달에 사용한다. Redis는 판매처별 전역
속도 제한, 처리 중/완료 상품 ID 중복 방지와 짧은 진행 상태에만 사용한다.

Python에 Celery를 적용할 경우 RabbitMQ를 broker로 사용하고 Redis를 두 번째 작업
Queue로 만들지 않는다. Python crawler core는 Celery task에서 분리해 fixture만으로도
실행할 수 있어야 한다. Go도 RabbitMQ adapter와 parser/normalizer를 분리해 같은 수준의
비교가 가능해야 한다.

## 7. 성능 평가를 두 번 나누는 이유

### 순수 parser/normalizer benchmark

동일하게 저장한 ABC마트/29CM 원본 fixture를 Python과 Go가 반복 처리한다. 네트워크가
없으므로 JSON 해석, 정규화, Contract 변환의 CPU와 메모리를 비교할 수 있다.

### 실제 수집 E2E benchmark

각 언어가 판매처 요청부터 결과 파일 저장까지 순차 실행한다. 이 결과에는 판매처 응답
속도와 네트워크 변동이 포함되므로 언어 자체 성능이라고 단정하지 않는다.

## 8. 결과 파일과 커밋 정책

대량 결과는 `tmp/` 아래 압축 NDJSON과 checkpoint로 저장하고 Git에 커밋하지 않는다.
저장소에는 실행 설정, 작은 fixture, 요약 통계와 재현 명령만 남긴다.

최종 보고서는 다음 표를 Python/Go 및 ABC마트/29CM 조합별로 작성한다.

| 지표 | 값 |
|---|---:|
| 목표/실제 고유 상품 | 확인 예정 |
| 중복 제거 전 상품 | 확인 예정 |
| Contract 성공/실패 | 확인 예정 |
| 요청/오류/429 | 확인 예정 |
| wall time/처리량 | 확인 예정 |
| CPU/최대 메모리 | 확인 예정 |
| checkpoint 재개 | 확인 예정 |
