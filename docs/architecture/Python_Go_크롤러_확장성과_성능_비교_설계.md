# Python/Go 크롤러 확장성과 성능 비교 설계

작성일: 2026-08-03
상태: 부분 구현

현재 Python 전용 브랜치와 Go 브랜치에 판매처별 JSON Adapter, pagination, 중복 제거,
checkpoint, 요청 예산, 안전 중단과 gzip NDJSON 저장이 구현됐다. 두 언어의 동일 fixture
benchmark와 단계별 실수집 결과 보고서는 남아 있다.

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

sandbox-python-croller/ls
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
