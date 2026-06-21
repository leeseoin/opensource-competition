---
name: github-pr-flow
description: GitHub Pull Request 생성, 조회, diff 점검, CI/check 상태 확인, 리뷰 반영, merge 절차를 수행하거나 안내할 때 사용한다. 작업 브랜치를 main/dev/release 브랜치로 올리는 PR, gh CLI 또는 GitHub 웹 UI 기반 PR 작업, merge 전 안전 점검, PR 생성 문구 작성 요청에 사용한다.
---

# GitHub PR Flow

GitHub Pull Request를 만들고, 확인하고, 병합 전 상태를 점검한다. 저장소별 배포 방식이나 브랜치 이름은 로컬 context에서 확인하고, 특정 프로젝트에 하드코딩하지 않는다.

## 원칙

1. PR 작업 전 `git status`, 현재 브랜치, remote, base/head 브랜치를 확인한다.
2. 미커밋 변경이 있으면 PR에 포함할지 사용자 의도를 확인하거나 명확히 제외한다.
3. `origin`이 GitHub remote인지 확인한다. GitLab, 사내 Git, fork remote가 섞여 있으면 명시적으로 구분한다.
4. PR 생성 후 checks/CI 상태를 확인한다.
5. checks 실패, 충돌, 의도치 않은 파일 포함, 민감정보 포함 가능성이 있으면 merge하지 않는다.
6. force push, branch delete, merge 실행은 사용자 요청 또는 승인 없이 하지 않는다.
7. `gh` CLI가 가능하면 사용하고, 불가능하면 GitHub 웹 UI 절차를 안내한다.

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
- 작업 브랜치가 PR head로 맞는지
- base 브랜치가 `main`, `dev`, `release/*` 중 무엇인지
- 커밋되지 않은 변경이 있는지
- 원격에 push되지 않은 커밋이 있는지

## PR 생성

`gh` CLI 인증 상태를 먼저 확인한다.

```bash
gh auth status
```

작업 브랜치를 원격에 올린다.

```bash
git push -u origin HEAD
```

PR 생성 기본형:

```bash
gh pr create \
  --base <base-branch> \
  --head <head-branch> \
  --title "<title>" \
  --body "<body>"
```

웹 UI로 안내할 때:

```text
GitHub repository
→ Pull requests
→ New pull request
→ base: <base-branch>
→ compare: <head-branch>
→ Create pull request
```

## PR 본문 템플릿

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

작업 성격에 맞게 불필요한 항목은 줄인다. 검증하지 못한 항목은 했다고 쓰지 않는다.

## PR 상태 확인

```bash
gh pr view <PR_NUMBER>
gh pr diff <PR_NUMBER> --name-only
gh pr checks <PR_NUMBER>
```

민감정보나 의도치 않은 파일을 확인한다.

```bash
git diff --name-only origin/<base-branch>...HEAD
git diff origin/<base-branch>...HEAD --stat
```

위험 파일 예:
- `.env`
- `*.pem`
- `*.key`
- 인증서/개인키
- DB 덤프
- 운영 서버 전용 secret

## Merge

사용자가 merge를 요청했고 checks가 통과했을 때만 진행한다.

일반 merge:

```bash
gh pr merge <PR_NUMBER> --merge
```

squash merge:

```bash
gh pr merge <PR_NUMBER> --squash
```

merge 후 로컬 기본 브랜치 갱신:

```bash
git checkout <base-branch>
git pull origin <base-branch>
```

브랜치 삭제는 사용자가 원할 때만 한다.

```bash
git branch -d <head-branch>
git push origin --delete <head-branch>
```

## 자주 쓰는 패턴

작업 브랜치에서 main으로 PR:

```bash
current_branch="$(git branch --show-current)"
gh pr create --base main --head "$current_branch"
```

PR checks 확인:

```bash
gh pr checks
```

현재 브랜치의 PR 보기:

```bash
gh pr view --web
```

## 응답 형식

```markdown
PR 작업 결과:
- base/head: <base> ← <head>
- PR: <number 또는 URL>
- checks: 성공/실패/대기
- merge: 수행함/대기/미요청

주의:
- 
```

