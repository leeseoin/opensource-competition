# 2026-08-06 MCP-002 Codex 구매 조건 Agent Gateway

- 기능 ID: `MCP-002`
- 구현 commit: `72fcfa4`
- 기록 상태: 구현 기록

## 배경과 범위

사용자 자연어 질문을 실제 Codex CLI가 공통 구매 조건으로 구조화하고, 사용자가 확인한 뒤에만
MCP Server를 통해 PostgreSQL 후보를 검색하는 server 전용 실행 경계를 구현했다.

## 구현 내용

- `frontend/purchase-web/app/lib/codex-runtime.ts:58` `runCodexCommand`: shell 없는 Codex child process/환경 변수 allowlist/timeout
- `frontend/purchase-web/app/lib/codex-runtime.ts:123` `structurePurchaseQuestion`: Plugin 규칙과 출력 Schema 적용 및 동시 실행 상한
- `frontend/purchase-web/app/lib/research-mcp-client.ts:50` `StdioResearchMcpClient`: 조사 세션 MCP 도구 호출과 process 정리
- `frontend/purchase-web/app/api/research/conditions/handler.ts:36` `handleConditionsRequest`: AI 조건 구조화와 DRAFT 저장
- `frontend/purchase-web/app/api/research/confirm/handler.ts:26` `handleConfirmRequest`: 미확정 차단과 확인 후 MCP 검색
- `frontend/purchase-web/app/lib/codex-runtime.test.ts:54` `Codex 구조화 동시 실행을 한 개로 제한한다`: process 상한 검증

## 발생 문제와 원인

첫 실제 E2E에서 조건과 다른 가격의 상품이 반환됐고, 최신 Python snapshot에는 옵션이 없어
엄격한 사이즈/색상 검색 결과가 0건이었다. 첫 문제는 Backend가 일부 조건만 사용했기 때문이며,
두 번째는 오래된 옵션을 현재 정보로 사용하지 않는 최신 snapshot 정책에 따른 정상 결과였다.

## 해결

조건 DB 필터는 별도 `e56090e` 커밋에서 보완했다. Go Collector로 최신 옵션을 소량 재수집한
뒤 같은 Agent Gateway 경로를 다시 실행해 확인 조건과 일치하는 후보만 반환되는지 검증했다.

## 검증

- `cd frontend/purchase-web && npm test`: 통과, 16개
- 실제 Codex CLI 구조화: 공통 Schema JSON과 추가 확인 조건 반환
- 실제 Next/Codex/MCP/Spring/PostgreSQL E2E: DRAFT 검색 없음/CONFIRMED 후 후보 1건
- `cd services/mcp-server && npm test`: 통과, 3개

## 남은 작업

stream/사용자 취소/stale 표시/Plugin marketplace 설치 및 Claude Code runtime이 남아 있다.
