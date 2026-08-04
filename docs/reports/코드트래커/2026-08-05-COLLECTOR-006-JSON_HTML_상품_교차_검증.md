# 2026-08-05 COLLECTOR-006 JSON/HTML 상품 교차 검증

- 기능 ID: `COLLECTOR-006`
- 구현 commit: `e1fa451`
- Swagger와 실제 smoke 보강 commit: `a9b5b80`
- 기록 상태: 완료 기록

## 배경과 범위

검색 JSON은 구조화된 대량 수집에 유리하지만 실제 공개 화면과 값이 같은지 확인할 수
없었다. JSON을 기본 상품값으로 유지하면서 ABC마트 검색 HTML과 29CM 상세 HTML의
Product JSON-LD를 선택 상품 전체에 대해 비교하고, 원본과 차이를 남기는 흐름을
구현했다.

## 구현 내용

- `services/collector/internal/collector/search.go:156` `VerificationSummary`와
  `SummarizeVerifications`: 응답 최상위 검증 상태 집계
- `services/collector/internal/verification/product.go:37` `Compare`: 상품 필드
  정규화와 JSON/HTML 차이 판정
- `services/collector/internal/merchants/abcmart/verification.go:24`
  `verifySearchPage`: ABC마트 검색 JSON과 렌더링 HTML의 선택 상품 전수 비교
- `services/collector/internal/merchants/twentyninecm/verification.go:24`
  `verifyProducts`: 29CM 검색 JSON과 상품별 상세 Product JSON-LD 전수 비교
- `services/collector/internal/artifact/store.go:25` `SaveJSON`: 판매처별 원본 JSON 저장
- `services/collector/internal/render/chrome.go:41` `ChromeRenderer.Render`: ABC마트 공개
  검색 화면 렌더링과 HTML 저장
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectorResultStoreService.java:123`
  `saveVerification`: snapshot과 연결된 검증 결과 PostgreSQL 저장
- `services/collector/internal/transport/http/swagger.go:15` `openAPIHandler`와
  `swaggerUIHandler`: 브라우저에서 수집 요청과 검증 집계 확인

## 발생 문제와 원인

- ABC마트는 JavaScript 실행 후 검색 상품 카드가 만들어져 단순 HTTP HTML만으로 같은
  상품을 비교할 수 없었다.
- 29CM 검색 HTML에는 비교 가능한 카드 데이터가 없지만 공개 상세 HTML에는 Product
  JSON-LD가 있었다.
- Chrome 실행 및 상품별 1초 간격 상세 요청 때문에 기존 15초 작업 timeout이 부족했다.

## 해결

- ABC마트는 시스템 Chrome을 한 번 실행해 같은 검색 페이지를 렌더링했다.
- 29CM는 기존 판매처 limiter를 공유하며 선택한 각 상품의 공개 상세 JSON-LD를
  순차적으로 비교했다.
- 기본 Worker와 HTTP write timeout을 90초로 조정하고 검증 실패도 숨기지 않고
  `FAILED` 상태와 원인을 반환했다.
- User-Agent 위장, 프록시, IP 회전 및 접근 통제 우회는 추가하지 않았다.

## 검증

- `cd services/collector && go test ./...`: 통과.
- `cd services/collector && go vet ./...`: 통과.
- `cd services/product-backend && ./gradlew test`: `BUILD SUCCESSFUL`.
- CollectorResult 예제 3개 JSON Schema 직접 검사: 3개 통과, 실패 0개.
- 실제 ABC마트 `구두` 3개: `status=success`, `matched=3`, `mismatched=0`.
- 실제 29CM `구두` 3개: `status=success`, `matched=3`, `mismatched=0`.
- Queue E2E job `job-0ac41449-8c28-4c92-95a3-f0613cf6a7c2`: `COMPLETED`, 상품 3개,
  `matched=3`.
- Queue E2E job `job-a54f7dfd-9fb9-458a-bc9d-bc0226f69df1`: `COMPLETED`, 상품 3개,
  `matched=3`.

## 남은 운영 보강

- 판매처 HTML/JSON-LD 구조 변화의 주기적 감지와 알림
- 원본 JSON/HTML 파일 보존 기간과 용량 상한
- 배포 환경의 Chrome 설치 및 Swagger UI 운영 환경 인증 정책
