#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
evaluation_file="$repository_root/knowledge/eval/retrieval-v1.json"

# 평가 data의 개수, 유형, ID와 사람 검토 상태를 고정한다.
jq -e '
  (.cases | length) == 60
  and ([.cases[].id] | length) == ([.cases[].id] | unique | length)
  and ((.cases | group_by(.type) | map({key: .[0].type, value: length}) | from_entries)
    == {exact: 15, semantic: 20, relaxation: 10, no_result: 10, reverification: 5})
  and ([.cases[] | select(.expectNoResults and (.judgments | length) != 0)] | length) == 0
  and ((.reviewStatus == "DRAFT" and .humanReviewRequired == true)
    or (.reviewStatus == "REVIEWED" and .humanReviewRequired == false))
' "$evaluation_file" >/dev/null

snapshot_relative_path="$(jq -r '.snapshot' "$evaluation_file")"
snapshot_file="$repository_root/$snapshot_relative_path"
test -f "$snapshot_file"

# 모든 relevance 판정이 고정 snapshot에 실제 존재하는 판매처 상품을 가리키는지 확인한다.
jq -e --slurpfile snapshot "$snapshot_file" '
  all(.cases[].judgments[];
    . as $judgment
    | any($snapshot[0][];
        .site == $judgment.merchant and .source_product_id == $judgment.externalId))
' "$evaluation_file" >/dev/null

printf '%s\n' '검색 평가 data 검사 통과'
