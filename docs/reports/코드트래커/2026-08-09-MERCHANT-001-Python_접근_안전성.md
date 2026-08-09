# 2026-08-09 MERCHANT-001 Python 접근 안전성

- 기능 ID: `MERCHANT-001`
- 구현 commit: `6ecc6c8`
- 기록 상태: 버그 수정 기록

## 배경과 범위

Python Collector의 ABC마트 상세 경로가 TLS 인증서 오류를 무시했고 ABC마트/29CM 일부
HTTP client가 redirect를 자동 추적했다. 접근 통제 우회 없이 실패를 분류하고 Queue
경고에 URL/query, 응답 body와 traceback이 노출되지 않도록 공통 안전 경계를 추가했다.

## 구현 내용

- `purchase-research-agent/app/crawlers/access_safety.py:11` `MerchantAccessError`: 안전한
  오류 코드와 retryable 의미 보관
- `purchase-research-agent/app/crawlers/access_safety.py:24` `ensure_success`: 3xx 차단,
  401/403 non-retryable, 429/5xx retryable 상태 분류
- `purchase-research-agent/app/crawlers/access_safety.py:69` `safe_exception_message`: URL,
  query, 응답 body와 traceback을 제외한 네트워크/timeout/parsing 오류 생성
- `purchase-research-agent/app/crawlers/abcmart/detail_fetcher.py` `_BROWSER_CFG/DetailFetcher`:
  TLS 검증 활성화, 명시적 User-Agent와 redirect 자동 추적 제거
- `purchase-research-agent/app/crawlers/cm29/detail_fetcher.py` `Cm29DetailFetcher`: 상세/리뷰
  HTTP 상태 검증과 안전한 오류 변환
- `purchase-research-agent/tests/test_access_safety.py:17` `AccessSafetyTests`: 상태 분류,
  TLS 설정과 민감 오류 원문 비노출 검증
- `scripts/check-python-crawler-safety.sh:1` `Python safety check`: 위험 설정 재도입 차단

## 발생 문제와 원인

이식한 상세 수집 코드의 browser와 HTTPX client에 `ignore_https_errors=True`,
`verify=False`, `follow_redirects=True`가 남아 있었다. 일반 browser User-Agent와 raw
exception/traceback도 사용해 프로젝트의 접근 비우회 및 정보 비노출 원칙과 맞지 않았다.

## 해결

TLS 검증을 기본 활성화하고 HTTP redirect 자동 추적을 모두 제거했다. 공통 응답 분류를
모든 Python 검색/상세/리뷰/옵션 HTTP 경로에 적용하고 식별 가능한 프로젝트 User-Agent를
사용했다. 위험 설정은 루트 전체 테스트에 포함된 정적 검사로 차단한다.

## 검증

- `make python-crawler-test`: 33개 통과
- `make python-crawler-safety-check`: 통과
- `make test`: Go/Python 비교기/Python runtime/Spring Boot/MCP/Next.js test와 lint 통과
- `make docs-check`: 통과
- `git diff --check`: 통과

## 남은 작업

- 판매처 응답에서 받은 상품 URL의 scheme/host/port allowlist 검증
- DNS 해석 결과의 private IP/localhost 차단
- 여러 process에 적용되는 판매처별 rate/concurrency limiter
