# MCP Server

Codex, Claude Code 및 이후 로컬 LLM이 구매 조사 기능을 같은 방식으로 호출하도록
제공하는 독립 MCP 서버다.

## 현재 상태

디렉토리와 책임만 확정된 planned 단계다. 언어, SDK, 실행 entrypoint 및 MCP tool은
아직 구현하지 않았다.

구현 언어와 실행 명령을 확정하기 전까지 Codex Plugin의 `.mcp.json`에는 실행 설정을
등록하지 않는다. 존재하지 않는 명령을 등록해 Plugin 실행이 실패하는 것을 막기
위한 결정이다.

## 책임

- MCP tool 입력과 출력 검증
- Product Backend 내부 REST API 호출
- 상품 검색, 비교, 수집 요청, 작업 상태 및 구매 전 재검증 도구 제공
- 모델에 구조화된 상품 사실과 근거 전달

## 하지 않는 일

- PostgreSQL 직접 접근
- RabbitMQ 직접 접근
- 판매처 직접 접근
- 상품 데이터 최종 저장

Product Backend가 제공하지 않은 상품 사실을 만들지 않는다.
