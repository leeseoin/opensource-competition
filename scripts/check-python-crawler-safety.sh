#!/usr/bin/env bash
set -euo pipefail

TARGET_DIR="purchase-research-agent/app/crawlers"
FORBIDDEN='verify=False|ignore_https_errors=True|follow_redirects=True'

if rg -n "${FORBIDDEN}" "${TARGET_DIR}" --glob '*.py'; then
  printf '%s\n' 'Python Collector에서 TLS 검증 비활성화 또는 자동 redirect 설정을 찾았습니다.' >&2
  exit 1
fi

printf '%s\n' 'Python Collector 접근 안전성 정적 검사 통과'
