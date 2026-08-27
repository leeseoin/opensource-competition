# 2026-08-27 RUNTIME-001 Claude 인증 환경

- 기능 ID: `RUNTIME-001`
- 구현 commit: `a2b7544`
- 기록 상태: 버그 수정 기록

## 배경과 범위

일반 터미널의 Claude Code CLI 로그인 검사는 성공했지만 Next.js Agent Gateway가 실행한
Claude child process만 인증정보를 읽지 못해 `AI_AUTH_REQUIRED` 503을 반환했다. Claude
인증에 필요한 사용자 문맥과 지원 환경을 안전하게 전달하고, README 첫 화면을 신규 사용자의
완전한 실행 순서로 갱신했다.

## 구현 내용

- `frontend/purchase-web/app/lib/claude-runtime.ts:26` `buildClaudeEnvironment`: macOS
  Keychain 사용자 문맥과 Claude/Anthropic 인증 및 통신 설정만 allowlist로 구성
- `frontend/purchase-web/app/lib/claude-runtime.ts:91` `runClaudeCommand`: 제한된 환경을
  Claude child process에 전달
- `frontend/purchase-web/app/lib/claude-runtime.test.ts:55`
  `Claude 인증에 필요한 환경만 선별해 전달한다`: 사용자/인증값 전달과 DB password 차단 검증
- `README.md:3` `처음 실행`: `.env`, AI CLI, 인프라, Python Collector, MCP/Web 설치와
  두 터미널 실행 순서 배치

## 발생 문제와 원인

`make ai-runtime-check`는 부모 shell 환경 전체를 사용했지만 Agent Gateway의 Claude 실행은
`USER`/`LOGNAME`/`SHELL`과 지원 인증 설정을 제거했다. 같은 계정에서도 두 실행 경계의
인증 판정이 달라 사용자가 불필요하게 로그인을 반복하게 됐다.

## 해결

모든 환경을 그대로 상속하지 않고 allowlist 원칙을 유지했다. 사용자 식별값, Claude 설정
경로, Claude/Anthropic 인증값, proxy와 인증서 설정만 추가하고 DB password 등 프로젝트의
다른 secret은 계속 차단했다. README의 중복 최초 실행 설명과 오래된 구현 상태도 현재
Python Collector/MCP/Agent 구조에 맞췄다.

## 검증

- `cd frontend/purchase-web && npm test`: 72개 통과
- `cd frontend/purchase-web && npm run lint`: 통과
- `cd frontend/purchase-web && npm run build`: Next.js production build 통과
- `make docs-check`: 통과
- `git diff --check`: 통과
- 사용자 수동 E2E: Claude Code CLI 2.1.211 `READY` 확인 후 `/chat` 조건 구조화와 상품 검색 성공

## 남은 작업

Claude/Codex stream과 요청 취소 및 runtime별 정량 평가는 `RUNTIME-001` 후속 범위다.
