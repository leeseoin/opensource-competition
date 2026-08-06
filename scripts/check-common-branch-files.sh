#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
left_ref="${1:-sandbox/ls}"
right_ref="${2:-sandbox-python-crawler/ls}"

cd "$repository_root"
git rev-parse --verify "$left_ref^{commit}" >/dev/null
git rev-parse --verify "$right_ref^{commit}" >/dev/null

# Collector 구현을 제외한 runtime 계약/서비스/UI/Plugin/검색 지식의 동일성을 확인한다.
common_paths=(
  contracts/collection
  contracts/research
  frontend/purchase-web
  knowledge
  plugins/purchase-research-agent
  services/mcp-server
  services/product-backend
  scripts/check-retrieval-evaluation.sh
  scripts/check-wiki.sh
)

if ! differences="$(git diff --name-status "$left_ref" "$right_ref" -- "${common_paths[@]}")"; then
  printf '%s\n' '공통 브랜치 파일 비교 중 git 오류가 발생했습니다.' >&2
  exit 1
fi
if [[ -n "$differences" ]]; then
  printf '공통 Purchase Research Agent 파일이 다릅니다: %s / %s\n' "$left_ref" "$right_ref" >&2
  printf '%s\n' "$differences" >&2
  exit 1
fi

printf '공통 Purchase Research Agent 파일 일치: %s / %s\n' "$left_ref" "$right_ref"
