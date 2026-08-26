#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_directory="$repository_root/knowledge/raw"
page_directory="$repository_root/knowledge/wiki"

# Wiki source ID의 유일성과 repository snapshot의 내용 해시를 검증한다.
source_ids="$({
  for source_file in "$source_directory"/*.json; do
    jq -r '.sourceId' "$source_file"
    source_type="$(jq -r '.sourceType' "$source_file")"
    if [[ "$source_type" == "repository_snapshot" ]]; then
      source_uri="$(jq -r '.uri' "$source_file")"
      expected_sha256="$(jq -r '.sha256' "$source_file")"
      actual_sha256="$(shasum -a 256 "$repository_root/$source_uri" | awk '{print $1}')"
      test "$actual_sha256" = "$expected_sha256"
    fi
  done
} | sort)"
test "$(printf '%s\n' "$source_ids" | wc -l | tr -d ' ')" = \
  "$(printf '%s\n' "$source_ids" | uniq | wc -l | tr -d ' ')"

# 모든 claim의 ID/source 연결과 PUBLISHED page의 사람 검토 계약을 검증한다.
for page_file in "$page_directory"/*.json; do
  jq -e '
    ([.claims[].claimId] | length) == ([.claims[].claimId] | unique | length)
    and all(.claims[];
      (.sourceIds | length) > 0
      and (.evidencePointers | length) > 0
      and (.derived | type) == "boolean"
      and (.confidence >= 0 and .confidence <= 1))
    and (if .status == "PUBLISHED"
      then (.reviewedBy | type) == "string"
        and (.reviewedBy | length) > 0
        and (.reviewedAt | type) == "string"
      else true end)
  ' "$page_file" >/dev/null

  while IFS= read -r claim_source_id; do
    printf '%s\n' "$source_ids" | grep -Fxq "$claim_source_id"
  done < <(jq -r '.claims[].sourceIds[]' "$page_file" | sort -u)
done

printf '%s\n' 'Wiki 검사 통과'
