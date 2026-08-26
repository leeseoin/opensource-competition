#!/bin/sh

set -u

ready_count=0

# resolve_command는 환경변수 경로 또는 PATH 명령을 실제 실행 가능한 경로로 해석한다.
resolve_command() {
  configured="$1"
  if [ -x "$configured" ]; then
    printf '%s\n' "$configured"
    return 0
  fi
  command -v "$configured" 2>/dev/null
}

# check_codex는 Codex CLI 설치, version과 ChatGPT/API 인증 상태를 확인한다.
check_codex() {
  configured="${CODEX_CLI_PATH:-codex}"
  command_path="$(resolve_command "$configured" || true)"
  if [ -z "$command_path" ]; then
    printf '%s\n' '[NOT_INSTALLED] Codex CLI'
    printf '%s\n' '  설치: https://developers.openai.com/codex/cli/'
    return
  fi
  version="$("$command_path" --version 2>/dev/null || printf '%s' 'version 확인 실패')"
  if "$command_path" login status >/dev/null 2>&1; then
    printf '%s\n' "[READY] Codex CLI / $version"
    ready_count=$((ready_count + 1))
  else
    printf '%s\n' "[AUTH_REQUIRED] Codex CLI / $version"
    printf '%s\n' '  인증: codex를 실행하고 Sign in with ChatGPT 선택'
  fi
}

# check_claude는 Claude Code CLI 설치, version과 first-party 인증 상태를 확인한다.
check_claude() {
  configured="${CLAUDE_CLI_PATH:-claude}"
  command_path="$(resolve_command "$configured" || true)"
  if [ -z "$command_path" ]; then
    printf '%s\n' '[NOT_INSTALLED] Claude Code CLI'
    printf '%s\n' '  설치: https://docs.anthropic.com/en/docs/claude-code/getting-started'
    return
  fi
  version="$("$command_path" --version 2>/dev/null || printf '%s' 'version 확인 실패')"
  auth_status="$("$command_path" auth status 2>/dev/null || true)"
  if printf '%s' "$auth_status" | grep -q '"loggedIn": true'; then
    printf '%s\n' "[READY] Claude Code CLI / $version"
    ready_count=$((ready_count + 1))
  else
    printf '%s\n' "[AUTH_REQUIRED] Claude Code CLI / $version"
    printf '%s\n' '  인증: claude를 실행하고 /login 수행'
  fi
}

check_codex
check_claude

if [ "$ready_count" -eq 0 ]; then
  printf '%s\n' '사용 가능한 AI runtime이 없습니다. 하나 이상 로그인해야 /chat 질문을 실행할 수 있습니다.'
  exit 1
fi

printf '%s\n' "사용 가능한 AI runtime: ${ready_count}개"
