---
name: git-pr
description: 커밋된 작업 브랜치를 원격에 push하고 GitHub Pull Request 또는 GitLab Merge Request를 생성/조회/상태 확인할 때 사용한다. 로컬 commit 생성과 merge는 이 스킬에서 수행하지 않는다.
metadata:
  short-description: git push + PR/MR
---

# Git Push & PR/MR

커밋된 작업 브랜치를 원격에 올리고 PR/MR을 만든다. 로컬 commit은 `git-commit`, 리뷰 후 merge는 `git-review-merge`의 책임이다.

## 원칙

1. push 전 `git status`, 현재 브랜치, remote, base/head 브랜치를 확인한다.
2. 미커밋 변경이 있으면 push/PR에 포함되지 않는다는 점을 사용자에게 알리거나 별도 commit 필요 여부를 확인한다.
3. remote가 GitHub인지 GitLab인지 확인한다.
4. GitHub는 `gh`, GitLab은 `glab`이 가능하면 사용한다.
5. CLI가 없거나 인증되지 않았으면 웹 UI 절차를 안내한다.
6. force push는 사용자 명시 요청과 승인 없이는 수행하지 않는다.
7. merge는 수행하지 않는다.

## 로컬 확인

```bash
git status --short
git branch --show-current
git remote -v
git log --oneline --decorate -5
```

필요하면 원격 상태를 갱신한다.

```bash
git fetch origin
git branch -r
```

확인할 것:

- 작업 브랜치가 PR/MR head로 맞는지
- base 브랜치가 무엇인지
- 원격에 push되지 않은 커밋이 있는지
- 의도치 않은 파일이나 민감정보가 커밋에 포함되지 않았는지

## Push

현재 브랜치를 원격에 올린다.

```bash
git push -u origin HEAD
```

이미 upstream이 있으면:

```bash
git push
```

## GitHub PR 생성

```bash
gh auth status
gh pr create \
  --base <base-branch> \
  --head <head-branch> \
  --title "<title>" \
  --body "<body>"
```

## GitLab MR 생성

```bash
glab auth status
glab mr create \
  --target-branch <base-branch> \
  --source-branch <head-branch> \
  --title "<title>" \
  --description "<body>"
```

## 웹 UI 안내

CLI가 불가능하면 저장소 플랫폼에 맞게 안내한다.

GitHub:

```text
Repository -> Pull requests -> New pull request
base: <base-branch>
compare: <head-branch>
Create pull request
```

GitLab:

```text
Project -> Merge requests -> New merge request
Source branch: <head-branch>
Target branch: <base-branch>
Create merge request
```

## PR/MR 본문 템플릿

```markdown
## 요약
- 

## 변경 사항
- 

## 검증
- [ ] 로컬 테스트
- [ ] CI 통과
- [ ] 수동 확인

## 주의사항
- 
```

검증하지 못한 항목은 했다고 쓰지 않는다.

## 상태 확인

GitHub:

```bash
gh pr view <PR_NUMBER>
gh pr diff <PR_NUMBER> --name-only
gh pr checks <PR_NUMBER>
```

GitLab:

```bash
glab mr view <MR_NUMBER>
glab mr diff <MR_NUMBER>
```

## 응답 형식

```markdown
PR/MR 작업 결과:
- platform: GitHub/GitLab/기타
- base/head: <base> <- <head>
- URL: <PR 또는 MR URL>
- checks: 성공/실패/대기/미확인

주의:
- 
```
