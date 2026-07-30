#!/usr/bin/env bash
set -euo pipefail

# 이 검사는 의존성 및 AI 관련 설정 변경에 공개 문서 갱신이 포함됐는지 확인한다.
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

for required_document in THIRD_PARTY_NOTICES.md AI_USAGE.md; do
  if [[ ! -s "$required_document" ]]; then
    printf '문서 동기화 실패: %s 파일이 없거나 비어 있습니다.\n' "$required_document" >&2
    exit 1
  fi
done

grep -Eq '^## 이 문서를 공개하는 이유$' THIRD_PARTY_NOTICES.md
grep -Eq '^## 갱신 방법$' THIRD_PARTY_NOTICES.md
grep -Eq '^## 이 문서를 공개하는 이유$' AI_USAGE.md
grep -Eq '^## 변경 시 갱신 규칙$' AI_USAGE.md

if [[ -n "${DOC_SYNC_CHANGED_FILES:-}" ]]; then
  changed_files="$DOC_SYNC_CHANGED_FILES"
elif [[ $# -gt 0 && -n "$1" && "$1" != "0000000000000000000000000000000000000000" ]] && git cat-file -e "$1^{commit}" 2>/dev/null; then
  changed_files="$(git diff --name-only "$1"...HEAD)"
else
  changed_files="$(
    {
      git diff --name-only HEAD
      git ls-files --others --exclude-standard
    } | sort -u
  )"
fi

if [[ -z "$changed_files" ]]; then
  printf '%s\n' '문서 동기화 검사 통과: 변경 파일이 없습니다.'
  exit 0
fi

dependency_pattern='(^|/)(go\.mod|go\.sum|package\.json|package-lock\.json|build\.gradle|build\.gradle\.kts|settings\.gradle|settings\.gradle\.kts|gradle\.properties|gradle-wrapper\.properties|compose\.ya?ml|Dockerfile[^/]*)$|^plugins/.*/(\.codex-plugin/plugin\.json|\.mcp\.json)$|(^|/)(models?|model-servers?)/'
ai_pattern='^plugins/|^services/mcp-server/|(^|/)(models?|model-servers?)/|(^|/)(ollama|llama-cpp|llama_cpp)(/|$)|^AGENTS\.md$|^\.agents/|^\.codex/'

if printf '%s\n' "$changed_files" | grep -Eq "$dependency_pattern"; then
  if ! printf '%s\n' "$changed_files" | grep -Fxq 'THIRD_PARTY_NOTICES.md'; then
    printf '%s\n' '문서 동기화 실패: 의존성, image, plugin 또는 model 설정이 변경됐지만 THIRD_PARTY_NOTICES.md가 갱신되지 않았습니다.' >&2
    exit 1
  fi
fi

if printf '%s\n' "$changed_files" | grep -Eq "$ai_pattern"; then
  if ! printf '%s\n' "$changed_files" | grep -Fxq 'AI_USAGE.md'; then
    printf '%s\n' '문서 동기화 실패: AI, Plugin 또는 MCP 관련 설정이 변경됐지만 AI_USAGE.md가 갱신되지 않았습니다.' >&2
    exit 1
  fi
fi

printf '%s\n' '문서 동기화 검사 통과'
