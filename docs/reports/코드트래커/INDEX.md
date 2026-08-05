# 코드트래커 인덱스

기능 ID별 구현 commit, 변경 파일, 문제 원인과 검증 결과를 연결한다.

## 최신 기록

- 2026-08-05 / [`MCP-001` 구매 조사 MCP 서버](2026-08-05-MCP-001-구매_조사_MCP_서버.md) / `1a0065c` / 조사 세션 생성과 사용자 확인 및 확정 조건 검색을 Product Backend REST API에 연결
- 2026-08-05 / [`MCP-002` AI 구매 조건 조사 세션](2026-08-05-MCP-002-AI_구매_조건_조사_세션.md) / `e60d4f4` / AI 조건을 DRAFT로 저장하고 사용자 확인 후에만 상품 검색 허용
- 2026-08-05 / [`BACKEND-001` 사용자 질문 상품 후보 API](2026-08-05-BACKEND-001-사용자_질문_상품_후보_API.md) / `8e1bd89`, `f7f4ec9` / 질문 문맥과 명시적 검색어로 PostgreSQL 최신 후보 최대 3개 반환
- 2026-08-05 / [`WEB-002` 사용자 구매 채팅과 비교 화면](2026-08-05-WEB-002-사용자_구매_채팅_비교_화면.md) / `9ad32c1`, `8e1bd89`, `f7f4ec9` / 랜딩에서 질문 입력, 실제 DB 후보와 상품 비교까지 사용자 동선 구현
- 2026-08-05 / [`WEB-001` Next.js 공통 화면 기반](2026-08-05-WEB-001-Nextjs_공통_화면_기반.md) / `9ad32c1` / Figma Landing V2와 로컬 이미지 및 반응형 화면 기반 구현
- 2026-08-05 / [`COLLECTOR-006` JSON/HTML 상품 교차 검증](2026-08-05-COLLECTOR-006-JSON_HTML_상품_교차_검증.md) / `e1fa451`, `a9b5b80` / ABC마트/29CM JSON과 공개 HTML을 전수 비교하고 실제 상품 각 3개 `MATCHED` 확인
- 2026-08-05 / [`BACKEND-002` 수집 job 영구 상태와 조회](2026-08-05-BACKEND-002-수집_job_영구_상태와_조회.md) / `d0dab6f` / PostgreSQL job/task 상태와 상품 수 및 JSON/HTML 검증 집계 조회 구현
- 2026-08-04 / [`OPS-004` Python/Go 크롤러 확장성과 성능 비교](2026-08-04-OPS-004-Python_Go_크롤러_확장성과_성능_비교.md) / `2715c1a`, `30e9b60` / 두 언어로 ABC마트 9,417개와 29CM 10,000개를 수집하고 fixture 성능을 비교
- 2026-07-31 / [`BACKEND-001` Collector 결과와 상품 snapshot 저장](2026-07-31-BACKEND-001-Collector_결과_상품_저장.md) / `3b59cd7` / ABC마트 실제 결과를 상품 3개와 옵션 19개로 PostgreSQL에 저장
- 2026-07-31 / [`OPS-002` Product Backend Swagger와 OpenAPI](2026-07-31-OPS-002-Product_Backend_Swagger_OpenAPI.md) / `3b59cd7` / 수동 적재와 상품 조회 API를 Swagger UI에서 검증
- 2026-07-31 / [`OPS-003` 기능 ID 기반 개발 추적](2026-07-31-OPS-003-기능_ID_기반_개발_추적.md) / `3b59cd7` / 기능 목록과 commit 및 진행상황을 연결하는 세 스킬 구성

## 목록 형식

```md
- YYYY-MM-DD / [`<기능-ID>` <제목>](<파일명>.md) / `<commit>` / 핵심 한 줄
```
