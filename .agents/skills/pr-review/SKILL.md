---
name: pr-review
description: GitHub PR 리뷰, diff 점검, gh CLI를 통한 PR 리뷰 코멘트 작성을 요청받았을 때 사용한다.
metadata:
  short-description: GitHub PR 리뷰
---

# PR 리뷰

버그와 회귀 위험을 우선하는 엔지니어링 리뷰 관점으로 Pull Request를 검토한다.

## 절차

1. PR 정보를 수집한다.

```bash
gh pr view <PR_NUMBER>
gh pr diff <PR_NUMBER>
gh pr diff <PR_NUMBER> --name-only
```

2. 변경 파일과 관련 주변 코드를 함께 확인한다.
3. 다음 항목을 우선순위로 본다.
   - 정확성 버그
   - 보안 이슈
   - 에러 핸들링 누락
   - 성능 회귀
   - API/스키마 계약 깨짐
   - 변경 동작에 대한 테스트 누락
4. 리뷰 게시를 요청받았다면 `gh pr comment <PR_NUMBER> --body ...`를 사용한다.

## 리뷰 형식

```markdown
## Code Review

### 발견 사항

| # | 심각도 | 파일 | 내용 |
|---|----------|------|-------|
| 1 | Bug | path:line | ... |

### 확인 질문
- ...

### 요약
- ...
```

## 원칙

- 발견 사항을 먼저 쓰고, 심각도 순서로 정렬한다.
- 가능하면 파일과 라인 번호를 포함한다.
- 명시적으로 요청받지 않았다면 approve 또는 request changes를 하지 않는다.
- 사용자 주석이 실제로 해롭거나 오해를 부르지 않는 한 삭제를 제안하지 않는다.
- 문제가 없으면 명확히 그렇게 말하고, 남은 테스트 공백이나 잔여 리스크를 적는다.
