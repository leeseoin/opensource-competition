# Research Backend

Python 기반 구매 조사 application 서비스다.

예정 책임:

- Codex용 MCP server
- Next.js와 Codex Gateway용 FastAPI와 SSE
- 장기 서비스 전환 시 OpenAI API Agent orchestration
- Go Collector client
- 수집 결과 검증·정규화
- PostgreSQL 적재
- 리뷰 신호 추출
- 상품 비교, evidence, 최신 정보 변경 검증

외부 판매처에 직접 접근하지 않으며 최종 PostgreSQL 쓰기를 소유한다.
