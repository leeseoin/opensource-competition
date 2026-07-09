---
name: git-pr
description: Git 작업 2단계. 커밋된 작업 브랜치를 원격에 push하고 GitHub Pull Request 또는 GitLab Merge Request를 생성한 뒤, Codex 또는 Claude Code 협업/code review 내용을 PR/MR 본문에 명시할 때 사용한다. 로컬 commit 생성과 merge는 수행하지 않는다.
metadata:
  short-description: git push + PR/MR
---

# Git PR/MR & AI Code Review

커밋된 작업 브랜치를 원격에 올리고 PR/MR을 만든다. PR/MR 본문에는 Codex 또는 Claude Code와 협업한 범위와 code review 수행 여부를 명시한다. 로컬 commit은 `git-commit`, merge는 `git-merge`의 책임이다.

## 원칙

1. push 전 `git status`, 현재 브랜치, remote, base/head 브랜치를 확인한다.
2. 미커밋 변경이 있으면 push/PR에 포함되지 않는다는 점을 사용자에게 알리거나 별도 commit 필요 여부를 확인한다.
3. remote가 GitHub인지 GitLab인지 확인한다.
4. GitHub는 `gh`, GitLab은 `glab`이 가능하면 사용한다.
5. CLI가 없거나 인증되지 않았으면 웹 UI 절차를 안내한다.
6. force push는 사용자 명시 요청과 승인 없이는 수행하지 않는다.
7. merge는 수행하지 않는다.
8. PR/MR 본문에는 Codex, Claude 등 AI 도구와 협업한 경우 그 사실을 명시한다. 실제 사용하지 않은 도구는 표기하지 않는다.
9. PR/MR 생성 전 가능한 범위에서 Codex 관점의 code review를 수행하고, 주요 이슈가 있으면 PR 생성 전에 사용자에게 알린다.
10. Claude Code가 별도로 리뷰했거나 사용자가 Claude Code와 함께 작업했다고 말한 경우에만 Claude Code를 표기한다.

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

## PR 전 Code Review

PR/MR 생성 전 다음을 확인한다.

- `git diff <base>...HEAD --stat`
- `git diff <base>...HEAD`
- 민감정보, 의도치 않은 산출물, 대용량 파일 포함 여부
- 테스트/검증 결과와 PR 본문 검증 항목의 일치 여부
- Codex가 발견한 blocking 이슈가 있는지

blocking 이슈가 있으면 PR/MR 생성 전에 사용자에게 보고하고 진행 여부를 확인한다.
Claude Code 리뷰 결과는 사용자가 제공했거나 명시적으로 사용한 경우에만 포함한다.

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

## AI 협업
- 사용 도구: Codex/Claude Code/없음
- 사용 범위:
- Code review: Codex 검토/Claude Code 검토/미수행

## 주의사항
- 
```

검증하지 못한 항목은 했다고 쓰지 않는다.
AI 협업 도구를 사용한 경우 사용 도구와 사용 범위를 사실대로 적는다.
Code review를 수행하지 못했으면 미수행으로 적고 이유를 남긴다.

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
