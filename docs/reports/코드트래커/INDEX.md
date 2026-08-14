# 코드트래커 인덱스

기능 ID별 구현 commit, 변경 파일, 문제 원인과 검증 결과를 연결한다.

## 최신 기록

- 2026-08-13 / [`MCP-003` 조건부 수집 freshness 범위 수정](2026-08-13-MCP-003-조건부_수집_freshness_범위_수정.md) / `b9e5616`, `80ca436` / 다른 검색어의 최신 상품을 현재 검색 범위로 오인하지 않고 동일 판매처/검색어/필터와 정확한 TTL 경계로 판정
- 2026-08-11 / [`MCP-003` 상태 기반 구매 조사 Agent Run](2026-08-11-MCP-003-상태_기반_Agent_Run.md) / `721581e`, `400dbf6`, `6872baf`, `6d218e1`, `56c7a23`, `206b168`, `dee92e7`, `74223ea` / DB 우선 검색과 조건부 수집 및 선택 상품 재검증을 하나의 복구 가능한 실행 상태로 연결
- 2026-08-11 / [`WEB-002` 조건 강도와 Select 대비 수정](2026-08-11-WEB-002-조건_강도_Select_대비_수정.md) / `b392bd2`, `3524655`, `88c2304` / 조건값을 비우고 교체해도 기존 필수/선호 강도를 유지하고 Portal hover 글자 대비 보정
- 2026-08-11 / [`WEB-001` 공통 Radix Select](2026-08-11-WEB-001-공통_Radix_Select.md) / `62d50a2` / 실행 환경과 구매 조건 강도 dropdown의 접근성 및 시각 상태 통일
- 2026-08-11 / [`ANALYSIS-002` 명시 색상 필수 조건](2026-08-11-ANALYSIS-002-명시_색상_필수_조건.md) / `54ec6b2` / 단정한 색상 요청의 불일치 후보를 제외하고 완화 표현만 선호로 유지
- 2026-08-11 / [`MCP-002` Codex 출력 Schema 호환성](2026-08-11-MCP-002-Codex_출력_Schema_호환성.md) / `a34787b` / 공용 계약과 Codex 엄격 출력 Schema를 분리하고 실제 Web DRAFT 저장 복구
- 2026-08-11 / [`VERIFY-001` 우선순위 상품 재검증](2026-08-11-VERIFY-001-우선순위_상품_재검증.md) / `1538c03` / 추천 snapshot과 새 검색 snapshot의 가격/재고 비교 및 실제 Queue VERIFIED 전환
- 2026-08-11 / [`MCP-001` 상품 조사 도구 확장](2026-08-11-MCP-001-상품_조사_도구_확장.md) / `874c378`, `1538c03` / 상품 상세/근거/비교/조건부 수집/재검증을 포함한 MCP 도구 9개 연결
- 2026-08-11 / [`ANALYSIS-004` 범용 구매 조건 정규화](2026-08-11-ANALYSIS-004-범용_구매_조건_정규화.md) / `a30ace4`, `60a0116` / 원문 보존 정규화와 범용 속성 및 PUBLISHED Wiki 동의어 검색 연결
- 2026-08-10 / [`QUEUE-002` 실제 판매처 Queue E2E와 RUNNING 상태](2026-08-10-QUEUE-002-실제_판매처_Queue_E2E.md) / `3ff397c`, `523ba3f` / ABC마트 수집 저장과 Spring의 RUNNING 시작 상태 및 실행 작업 수 검증
- 2026-08-10 / [`QUEUE-001` 계약 위반 복구와 RUNNING 시작 상태](2026-08-10-QUEUE-001-계약_위반_작업_실패_연결.md) / `ca58f3b`, `523ba3f` / 계약 위반 실패 연결과 Python Worker의 시작 이벤트 발행 구현
- 2026-08-10 / [`OPS-002` Python Swagger 단계 테스트](2026-08-10-OPS-002-Python_Swagger_단계_테스트.md) / `7852165`, `fdd16f3` / Python Queue 수집 준비부터 PostgreSQL 결과 조회까지 번호가 붙은 Swagger 흐름과 세 필드 작업 등록 입력 추가
- 2026-08-09 / [`MERCHANT-001` Python 접근 안전성](2026-08-09-MERCHANT-001-Python_접근_안전성.md) / `6ecc6c8` / TLS 검증과 redirect 차단 및 401/403/429/5xx 안전 분류를 Python 수집 경로에 적용
- 2026-08-09 / [`QUEUE-001` Python Collection Queue Worker](2026-08-09-QUEUE-001-Python_Collection_Queue_Worker.md) / `32d6446` / Python runtime을 Queue v1에 연결하고 confirm 뒤 ACK, retry, DLQ를 실제 broker로 검증
- 2026-08-08 / [`ANALYSIS-004` 범용 상품군 선택 UI](2026-08-08-ANALYSIS-004-범용_상품군_선택_UI.md) / `e8dc28d` / 최대 5개 상품군으로 중복을 묶고 컬러별 원본 판매 행과 재고 선택 UI 구현
- 2026-08-08 / [`ANALYSIS-003` 검토 Wiki 운영 검색](2026-08-08-ANALYSIS-003-검토_Wiki_운영_검색.md) / `e8dc28d` / PUBLISHED 운동화 관계를 운영 후보 검색과 실패 fallback에 연결
- 2026-08-08 / [`ANALYSIS-002` 검색 A/B 평가 기반](2026-08-08-ANALYSIS-002-검색_A_B_평가_기반.md) / `cf1f0d9` / 20개 snapshot과 60개 DRAFT 질문의 자동 지표 및 사람 검토 자료 생성
- 2026-08-06 / [`ANALYSIS-003` 검토형 구매 도메인 Wiki](2026-08-06-ANALYSIS-003-검토형_구매_도메인_Wiki.md) / `0cf1761` / source 기반 DRAFT Wiki를 같은 검색 평가에 비교하고 nDCG@3 하락으로 운영 비활성화
- 2026-08-06 / [`ANALYSIS-002` Hybrid 상품 후보 검색](2026-08-06-ANALYSIS-002-Hybrid_상품_후보_검색.md) / `0cf1761` / 필수/선호 조건, FTS/trigram, 선택적 pgvector fallback과 60개/10,000개 평가 구현
- 2026-08-06 / [`MCP-002`, `WEB-002` Codex 인증 오류 비노출](2026-08-06-MCP-002-Codex_인증_오류_비노출.md) / `eb55cef` / 폐기된 OAuth token과 Plugin prompt 원문을 browser에 노출하지 않고 빈 실행 설정을 기본값으로 복구
- 2026-08-06 / [`MCP-002`, `WEB-002` Python 브랜치 동일 사용자 E2E](2026-08-06-MCP-002-Python_브랜치_동일_E2E.md) / `914990f`, `8c10286`, `fa57978` / Python 수집 DB 저장과 Codex 조건 확인 후 MCP 후보 검색을 같은 화면 흐름으로 검증
- 2026-08-06 / [`OPS-004` Python/Go 10,000개 최신 성능 재검증](2026-08-06-OPS-004-Python_Go_10000개_최신_성능_재검증.md) / Go `2d2e64f`, Python `82aa8cd` / ABC마트 결과 소진과 29CM 10,000개를 최신 조건으로 재측정
- 2026-08-05 / [`OPS-004` Python 검증 집계 공통 계약 정렬](2026-08-05-OPS-004-Python_검증_집계_공통_계약_정렬.md) / `f08958e` / Python ABC마트/29CM 검증 집계를 Go와 같은 CollectorResult 구조로 정렬하고 전송 전 Schema 검사 추가
- 2026-07-31 / [`BACKEND-001` Collector 결과와 상품 snapshot 저장](2026-07-31-BACKEND-001-Collector_결과_상품_저장.md) / `3b59cd7` / ABC마트 실제 결과를 상품 3개와 옵션 19개로 PostgreSQL에 저장
- 2026-07-31 / [`OPS-002` Product Backend Swagger와 OpenAPI](2026-07-31-OPS-002-Product_Backend_Swagger_OpenAPI.md) / `3b59cd7` / 수동 적재와 상품 조회 API를 Swagger UI에서 검증
- 2026-07-31 / [`OPS-003` 기능 ID 기반 개발 추적](2026-07-31-OPS-003-기능_ID_기반_개발_추적.md) / `3b59cd7` / 기능 목록과 commit 및 진행상황을 연결하는 세 스킬 구성

## 목록 형식

```md
- YYYY-MM-DD / [`<기능-ID>` <제목>](<파일명>.md) / `<commit>` / 핵심 한 줄
```
