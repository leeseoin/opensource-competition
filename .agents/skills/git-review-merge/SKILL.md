---
name: git-review-merge
description: GitHub PR 또는 GitLab MR을 리뷰하고, diff/checks/충돌/민감정보를 점검한 뒤 사용자 승인과 조건 충족 시 merge까지 수행할 때 사용한다.
metadata:
  short-description: PR/MR review + merge
---

# PR/MR Review & Merge

버그와 회귀 위험을 우선하는 엔지니어링 리뷰 관점으로 PR/MR을 검토한다. 문제가 없고 checks/승인 조건이 충족되며 사용자가 merge를 요청 또는 승인한 경우에만 merge한다.

## 원칙

1. 발견 사항을 먼저 보고, 심각도 순서로 정렬한다.
2. 파일과 라인 번호를 가능한 한 포함한다.
3. CI/check 실패, 충돌, 민감정보, 의도치 않은 파일이 있으면 merge하지 않는다.
4. 명시적으로 요청받지 않았다면 approve/request changes/merge를 하지 않는다.
5. merge 방식은 저장소 정책을 따른다. 알 수 없으면 사용자에게 확인한다.
6. branch delete는 사용자가 원할 때만 한다.

## 정보 수집

로컬/원격 상태:

```bash
git status --short
git branch --show-current
git remote -v
```

GitHub:

```bash
gh pr view <PR_NUMBER>
gh pr diff <PR_NUMBER>
gh pr diff <PR_NUMBER> --name-only
gh pr checks <PR_NUMBER>
```

GitLab:

```bash
glab mr view <MR_NUMBER>
glab mr diff <MR_NUMBER>
```

## 리뷰 기준

우선순위:

- 정확성 버그
- 보안 이슈
- 데이터 손실/마이그레이션 위험
- 에러 핸들링 누락
- 성능 회귀
- API/스키마 계약 깨짐
- 동시성/트랜잭션 문제
- 테스트 누락
- 운영/배포 설정 오류

변경 파일과 관련 주변 코드를 함께 확인한다. Diff만 보고 확정하기 어려운 문제는 주변 구현을 읽는다.

## 민감정보/위험 파일 확인

위험 파일 예:

- `.env`
- `*.pem`, `*.key`, 인증서/private key
- DB dump
- 운영 secret
- 대용량 build artifact
- 개인 로컬 설정

## 리뷰 출력 형식

```markdown
## Code Review

### 발견 사항

| # | 심각도 | 위치 | 내용 |
|---|--------|------|------|
| 1 | High | `path:line` | ... |

### 확인 질문
- ...

### 요약
- ...
```

문제가 없으면 "확인된 주요 이슈 없음"이라고 명확히 말하고, 남은 테스트 공백이나 잔여 리스크를 적는다.

## 리뷰 게시

사용자가 리뷰 게시를 요청한 경우에만 CLI로 게시한다.

GitHub comment:

```bash
gh pr comment <PR_NUMBER> --body "<body>"
```

GitLab comment:

```bash
glab mr note <MR_NUMBER> --message "<body>"
```

## Merge 조건

아래 조건이 모두 충족될 때만 merge한다.

- 사용자가 merge를 요청했거나 merge 진행을 승인했다.
- 리뷰에서 blocking 이슈가 없다.
- checks/CI가 통과했거나 사용자가 실패/미확인 상태를 인지하고 진행 승인했다.
- 충돌이 없다.
- base/head 브랜치가 확인됐다.

GitHub merge:

```bash
gh pr merge <PR_NUMBER> --merge
```

GitHub squash merge:

```bash
gh pr merge <PR_NUMBER> --squash
```

GitLab merge:

```bash
glab mr merge <MR_NUMBER>
```

merge 후 필요하면 로컬 기본 브랜치를 갱신한다.

```bash
git checkout <base-branch>
git pull origin <base-branch>
```

## 응답 형식

```markdown
리뷰/머지 결과:
- 대상: <PR/MR URL 또는 번호>
- 리뷰: 주요 이슈 없음/이슈 있음
- checks: 성공/실패/대기/미확인
- merge: 수행함/대기/미수행

주의:
- 
```
