#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
evaluation_file="$repository_root/knowledge/eval/retrieval-v1.json"
summary_file="$repository_root/knowledge/eval/reports/retrieval-ab-v1-summary.json"
review_file="$repository_root/knowledge/eval/reviews/retrieval-ab-v1-human-review.csv"
first_review_guide="$repository_root/knowledge/eval/reviews/retrieval-ab-v1-first10-human-review.md"

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

# 도입 전/후 DRAFT 자동 지표와 사람 검토표의 고정 계약을 확인한다.
jq -e '
  .datasetVersion == "v1"
  and .datasetReviewStatus == "DRAFT"
  and .humanReviewRequired == true
  and .caseCount == 60
  and (.initialHumanReviewCaseIds | length) == 10
  and .before.hardConstraintViolationRateAt3 == 0
  and .after.hardConstraintViolationRateAt3 == 0
  and .after.recallAt20 >= .before.recallAt20
  and .after.ndcgAt3 >= .before.ndcgAt3
  and .after.falseZeroRate <= .before.falseZeroRate
  and .after.noResultAccuracy >= .before.noResultAccuracy
' "$summary_file" >/dev/null

test "$(grep -c '^"' "$review_file")" -eq 60
test "$(grep -c '^"FIRST_10"' "$review_file")" -eq 10
head -n 1 "$review_file" | grep -q 'preferred_variant,review_notes$'

# 첫 검토자가 CSV 없이도 평가 목적과 판정 기준을 읽을 수 있는지 문서 계약을 확인한다.
test "$(grep -c '^### [0-9][0-9]*\. ' "$first_review_guide")" -eq 10
grep -q '10개 평가는 코드를 작성하거나 지표를 계산하는 작업이 아니다' "$first_review_guide"
grep -q 'CSV는 이후 전체 결과를 집계할 때 사용하는 원자료' "$first_review_guide"
grep -q '면접용 갈색 구두 265 10만 원 이하' "$first_review_guide"

printf '%s\n' '검색 평가 data 검사 통과'
