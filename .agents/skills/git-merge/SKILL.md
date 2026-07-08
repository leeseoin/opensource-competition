---
name: git-merge
description: Git 작업 3단계. GitHub PR 또는 GitLab MR의 리뷰, checks, 충돌, 민감정보를 점검한 뒤 사용자 승인과 조건 충족 시 merge만 수행할 때 사용한다. commit 생성과 PR/MR 생성은 수행하지 않는다.
metadata:
  short-description: PR/MR review + merge
---

# Git Merge

열린 PR/MR을 merge하기 전에 최종 안전 점검을 수행한다. 문제가 없고 사용자가 merge를 승인한 경우에만 merge한다.

## 원칙

1. commit 생성, push, PR/MR 생성은 수행하지 않는다.
2. merge 전 PR/MR 상태, base/head, checks, 충돌, diff, 민감정보를 확인한다.
3. blocking 이슈가 있으면 merge하지 않는다.
4. checks가 실패/대기/미확인인 경우 사용자가 상태를 인지하고 명시 승인해야 진행한다.
5. merge 방식은 저장소 정책을 따른다. 알 수 없으면 사용자에게 확인한다.
6. branch delete는 사용자가 원할 때만 한다.
7. merge 후 base 브랜치 상태와 PR/MR 상태를 확인한다.

## 정보 수집

```bash
git status --short
git branch --show-current
git remote -v
git fetch origin
```

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

## 최종 리뷰 기준

- 정확성 버그
- 보안 이슈와 secret 포함 여부
- 데이터 손실/마이그레이션 위험
- API/스키마 계약 깨짐
- 에러 핸들링 누락
- 테스트 누락 또는 검증 불일치
- 의도치 않은 build artifact, 캐시, 대용량 파일

## Merge 조건

아래 조건이 모두 충족될 때만 merge한다.

- 사용자가 merge를 요청했거나 진행을 승인했다.
- blocking 이슈가 없다.
- checks/CI가 통과했거나 사용자가 실패/미확인 상태를 인지하고 승인했다.
- 충돌이 없다.
- base/head 브랜치가 확인됐다.

GitHub:

```bash
gh pr merge <PR_NUMBER> --merge
```

Squash merge가 저장소 정책이면:

```bash
gh pr merge <PR_NUMBER> --squash
```

GitLab:

```bash
glab mr merge <MR_NUMBER>
```

## 응답 형식

```markdown
merge 작업 결과:
- 대상: <PR/MR URL 또는 번호>
- base/head: <base> <- <head>
- checks: 성공/실패/대기/미확인
- merge: 수행함/대기/미수행

주의:
- 
```
