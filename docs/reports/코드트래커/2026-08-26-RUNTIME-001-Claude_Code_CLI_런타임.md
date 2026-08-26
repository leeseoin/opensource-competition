# 2026-08-26 RUNTIME-001 Claude Code CLI 런타임

- 기능 ID: `RUNTIME-001`
- 구현 commit: `b664708`
- 기록 상태: 구현 기록

## 배경과 범위

Codex로만 고정돼 있던 자연어 구매 조건 구조화 경로에 Claude Code CLI를 추가했다.
두 CLI는 같은 Plugin 규칙, PurchaseCondition 계약과 MCP Server를 사용하며, 소스를 처음
받은 사용자가 설치와 로그인 상태를 확인할 수 있는 점검 명령도 포함한다.

## 구현 내용

- `frontend/purchase-web/app/lib/agent-runtime.ts:4` `AgentRuntime`: Codex/Claude 공통 runtime과 안전한 오류 계약
- `frontend/purchase-web/app/lib/claude-runtime.ts:139` `structurePurchaseQuestionWithClaude`: 도구를 비활성화한 비대화형 JSON Schema 구조화 실행
- `frontend/purchase-web/app/api/research/conditions/handler.ts:40` `handleConditionsRequest`: 사용자 선택 runtime으로 조건 구조화 후 MCP DRAFT 저장
- `services/mcp-server/src/index.ts:76` `create_research_session`: `codex/claude` runtime을 Product Backend에 전달
- `services/product-backend/src/main/java/com/purchasesearch/product_backend/research/dto/ResearchSessionRequest.java:17` `ResearchSessionRequest`: 두 runtime의 조사 세션을 같은 계약으로 허용
- `scripts/check-ai-runtimes.sh:18` `check_codex`와 `check_claude`: 설치와 인증 상태를 `READY/AUTH_REQUIRED/NOT_INSTALLED`로 구분
- `frontend/purchase-web/app/lib/claude-runtime.test.ts:27` `Claude를 도구 없는 구조화 출력 모드로 실행한다`: 권한, Schema와 prompt 계약 검증

## 발생 문제와 원인

Claude CLI는 공통 Schema의 JSON Schema draft URI를 해석하지 못했고, 미로그인 오류를
stderr가 아니라 JSON stdout으로 반환했다. 또한 소스를 처음 받은 사용자는 CLI 설치와
인증 준비 여부를 실행 전에 확인할 방법이 없었다.

## 해결

도메인 Schema는 유지하되 Claude CLI에 전달하는 복사본에서 `$schema` 식별자만 제거했다.
실패 분류는 제한된 stdout/stderr를 함께 검사하되 원문을 browser에 노출하지 않는다.
Claude built-in tool과 session 저장을 비활성화하고 model 선택은 로그인된 CLI 기본값에
위임했다. 루트 점검 명령과 최초 실행 문서를 추가했다.

## 검증

- `cd frontend/purchase-web && npm test && npm run lint && npm run build`: 71개 및 전체 통과
- `cd services/mcp-server && npm test`: 4개 통과
- `cd services/product-backend && ./gradlew test`: 전체 통과
- `make ai-runtime-check`: Codex 0.147.0 `READY`, Claude Code 2.1.211 `AUTH_REQUIRED` 판정
- Codex 0.147.0 실제 구조화 호출: 운동화/15만 원/출근 선호 조건 JSON 생성 성공
- `make docs-check`: 통과

## 남은 작업

- Claude Code CLI 로그인 후 실제 조건 구조화, MCP DRAFT 저장과 후보 검색 E2E
- Codex/Claude 응답 stream, 취소와 공통 동시 요청 정책
- Ollama, llama.cpp와 GPU model server adapter 및 모델별 품질 평가
