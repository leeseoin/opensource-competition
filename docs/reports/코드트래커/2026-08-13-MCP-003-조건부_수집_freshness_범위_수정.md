# 2026-08-13 MCP-003 조건부 수집 freshness 범위 수정

- 기능 ID: `MCP-003`
- 구현 commit: `cc628e0`
- 기록 상태: 버그 수정 기록

## 배경과 범위

확정 조건과 일치하는 후보가 없을 때 최근 `페니 로퍼` 상품이 구두 카테고리에 포함된다는
이유로 `구두` 검색 범위 전체를 최신으로 판단하고 수집을 생략했다. 조건부 수집의 최신성
근거를 상품 유사 검색 결과에서 정확한 수집 요청 문맥으로 교체했다.

## 구현 내용

- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/repository/CollectionSearchContextRepository.java:25`
  `findLatestDefaultSearchCollectedAt`: 판매처, 앞뒤 공백과 대소문자를 정규화한 검색어 및
  기본 필터가 같은 마지막 수집 완료 시각 조회
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/collection/service/CollectionRefreshService.java:46`
  `request`: 정확한 수집 문맥의 완료 시각으로 FRESH/STALE/MISSING 판정 및 Queue 발행
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/collection/service/CollectionRefreshServiceTests.java:40`
  `doesNotCollectFreshDataWithoutForce`, `collectsStaleData`,
  `collectsWhenExactSearchContextIsMissing`: 최신/만료/누락 단위 경로 검증
- `services/product-backend/src/test/java/com/purchasesearch/product_backend/ProductStorageIntegrationTests.java:170`
  `findsLatestCollectionOnlyForExactDefaultSearchScope`: 실제 PostgreSQL JSONB 필터와 검색 범위
  조회 검증

## 발생 문제와 원인

- 증상: 검정/270/10만 원 이하 구두 후보가 없을 때 `COLLECTION_SKIPPED`로 즉시 종료됐다.
- 원인: 상품 이름/브랜드/카테고리 및 관련 수집 검색어로 조회한 상품의 최신 snapshot을
  현재 검색 범위의 freshness 근거로 사용했다. 따라서 다른 검색어로 방금 갱신된 상품이
  현재 검색 범위 전체를 최신으로 만들었다.
- 추가 확인: PostgreSQL native timestamp projection은 JDBC에서 `Instant`로 반환되므로
  `OffsetDateTime` projection은 실행 시 형변환 오류를 만들 수 있었다.

## 해결

`collection_search_contexts`에서 동일 판매처, 정규화 검색어 및
`{"inStockOnly": false}` 기본 필터가 모두 같은 마지막 완료 기록만 사용한다. DB 경계에서는
`Instant`를 사용하고 API 응답 경계에서 UTC `OffsetDateTime`으로 변환한다.

## 검증

- `cd services/product-backend && ./gradlew --no-daemon test`: 전체 통과
- 단위/Agent Run/실제 PostgreSQL 범위 회귀 테스트: 통과
- `git diff --check`: 통과
- `make docs-check`: 통과
- 실제 로컬 DB 조회: 기존 `abcmart/구두/{"inStockOnly": false}` 기록이 약 43시간 전으로
  24시간 TTL 기준 STALE임을 확인
- 실제 Web/Queue 확인: 수정 후 Agent Run이 STALE/COLLECTING으로 진입하고 Python Worker가
  구두 5개를 수집해 job COMPLETED로 종료함

## 남은 작업

- 자동 조건부 수집 한도가 현재 5개이므로 판매처 전체 부재로 단정할 수 없다. 수집 예산과
  pagination 및 조사 범위 표시를 별도 기능으로 보강한다.
- Python Worker가 없을 때 Queue 대기 상태와 재개 방법을 Web에 표시한다.
