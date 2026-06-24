---
name: git-push
description: 현재 저장소의 커밋된 로컬 변경사항을 원격 저장소로 push할 때 사용한다. push 전 remote -v와 현재 브랜치를 확인하고, 사용자가 승인한 remote와 branch에만 push한다. PR/MR 생성, merge, force push는 수행하지 않는다.
metadata:
  short-description: git push
---

# Git Push

커밋된 로컬 변경사항을 사용자가 지정한 원격 저장소와 브랜치로 push한다.

이 스킬은 push까지만 수행한다. PR/MR 생성은 `git-pr`, commit 생성은 `git-commit`, merge는 `git-review-merge`의 책임이다.

## 원칙

- 사용자 승인 없이 push하지 않는다.
- `git remote -v`로 push 가능한 원격 저장소를 먼저 확인한다.
- 현재 브랜치와 최근 커밋을 확인한다.
- 사용자가 어느 remote와 어느 branch에 push할지 명확히 승인해야 한다.
- 미커밋 변경은 push되지 않는다는 점을 사용자에게 알린다.
- force push는 수행하지 않는다.
- push 후 원격/브랜치/upstream 상태를 확인한다.

## 절차

1. 저장소 상태를 확인한다.

```bash
git status --short
git branch --show-current
git remote -v
git log --oneline --decorate -5
```

2. 원격 브랜치 상태를 확인한다.

```bash
git fetch --all --prune
git branch -vv
git branch -r
```

3. push 후보를 사용자에게 제시한다.

필수로 물어볼 것:

```text
어느 remote에 push할까요? 예: origin
어느 branch에 push할까요? 예: main 또는 현재 브랜치
```

응답에는 다음을 포함한다.

```text
current branch:
remote candidates:
recent commits:
uncommitted changes:
proposed command:
```

4. 사용자가 승인한 뒤 push한다.

현재 브랜치를 같은 이름의 원격 브랜치로 처음 push할 때:

```bash
git push -u <remote> HEAD
```

현재 브랜치를 특정 원격 브랜치명으로 push할 때:

```bash
git push -u <remote> HEAD:<branch>
```

이미 upstream이 있고 사용자가 그대로 push를 승인한 경우:

```bash
git push
```

5. push 결과를 확인한다.

```bash
git status --short
git branch -vv
git log --oneline --decorate -3
```

## 사용자 승인 메시지 예시

```markdown
push 전 확인:
- current branch: main
- remote candidates:
  - origin https://github.com/user/repo.git
- recent commits:
  - abc1234 feat: ...
- uncommitted changes: 있음/없음

추천 push:
`git push -u origin HEAD`

이대로 `origin`의 `main` 브랜치에 push할까요?
```

사용자가 다른 branch를 원하면 명령을 바꾼다.

```bash
git push -u origin HEAD:<target-branch>
```

## 미커밋 변경 처리

`git status --short`에 변경사항이 있으면 다음을 명확히 알린다.

```text
미커밋 변경은 push에 포함되지 않습니다.
이미 commit된 내용만 push됩니다.
```

사용자가 미커밋 변경까지 포함하길 원하면 push를 멈추고 `git-commit` 절차를 먼저 안내한다.

## 금지

- 사용자 승인 없는 push
- `git push --force`
- `git push --force-with-lease`
- `git push --mirror`
- `git push --all`
- remote URL 임의 변경
- PR/MR 생성
- merge

force push가 꼭 필요하다고 사용자가 명시하면, 이 스킬로 수행하지 말고 위험을 설명한 뒤 별도 명시 승인 절차로 다룬다.

## 응답 형식

push 완료 후:

```markdown
push 완료:
- remote: <remote>
- branch: <branch>
- command: `<실행한 명령>`
- latest commit: `<hash> <message>`

남은 로컬 변경:
- 없음 또는 `git status --short` 요약
```
