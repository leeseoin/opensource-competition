# 2026-08-11 MCP-002 Codex 출력 Schema 호환성

- 기능 ID: `MCP-002`
- 구현 commit: `a34787b`
- 기록 상태: 버그 수정 기록

## 배경과 범위

범용 PurchaseCondition에 선택 정규화 필드를 추가한 뒤 Web의 Codex CLI 실행이
`invalid_json_schema`로 실패했다. 공용 계약의 하위 호환성을 유지하면서 Codex 구조화
출력 제약을 만족하도록 실행용 Schema를 분리했다.

## 구현 내용

- `contracts/research/v1/purchase-condition.codex-output.schema.json:1`
  `PurchaseConditionCodexOutput`: 모든 객체의 properties를 required로 선언하고 선택 값은
  null로 표현하는 Codex 전용 Schema
- `frontend/purchase-web/app/lib/codex-runtime.ts:36` `classifyCodexProcessFailure`:
  `invalid_json_schema`를 원본 stderr 비노출 계약 오류로 분류
- `frontend/purchase-web/app/lib/codex-runtime.ts:175` `structurePurchaseQuestion`: 공용 계약 대신
  Codex 전용 출력 Schema를 전달하고 null/빈 attributes 작성 규칙 안내
- `frontend/purchase-web/app/lib/codex-runtime.test.ts:46`
  `Codex 출력 Schema는 모든 객체 속성을 required로 선언한다`: 구조화 출력 제약 회귀 검증

## 발생 문제와 원인

Codex CLI 0.146.1의 구조화 출력은 객체의 `properties` 전체가 `required`에 있어야 한다.
공용 Schema의 `normalizedValue` 등은 기존 Web/MCP/Backend 입력과 호환하기 위해 선택
필드였으므로 OpenAI API가 요청을 400으로 거절했다. Runtime은 원본 stderr를 안전상
노출하지 않았고 일반 실패 시 안전한 서버 로그도 남기지 않아 안내와 실제 상태가 달랐다.

## 해결

공용 Schema를 강화해 기존 입력을 깨지 않고, Codex 생성 단계만 엄격 Schema를 사용한다.
실행 실패 로그에는 원본 stderr 대신 분류 코드와 process exit code만 기록한다.

## 검증

- `uvx check-jsonschema --check-metaschema`: 통과
- `cd frontend/purchase-web && npm test`: 28개 통과
- `cd frontend/purchase-web && npm run lint`: 통과
- Codex CLI 0.146.1 실제 구조화 출력: 성공
- 실제 `POST /api/research/conditions`: 조건 정규화와 MCP DRAFT 저장 성공

## 남은 작업

- Codex CLI version 변경 시 실제 구조화 출력 smoke test 재실행
- 안전한 서버 오류 코드를 운영 구조화 로그 수집기로 연결
