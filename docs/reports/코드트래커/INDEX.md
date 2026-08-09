# 코드트래커 인덱스

기능 ID별 구현 commit, 변경 파일, 문제 원인과 검증 결과를 연결한다.

## 최신 기록

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
