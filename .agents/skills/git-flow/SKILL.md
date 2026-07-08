---
name: git-flow
description: Git 작업 통합 스킬. git-commit, git-pr, git-merge의 3단계를 하나의 흐름으로 진행해야 할 때 사용한다. 각 단계마다 상태 점검과 사용자 승인을 거치며, commit부터 PR/MR 생성과 AI code review 표기, 최종 merge까지 순차 수행한다.
metadata:
  short-description: commit + PR + review + merge
---

# Git Flow

Git 작업을 3단계로 통합 진행한다.

```text
1. commit
2. PR/MR 생성 및 Codex/Claude Code 협업 또는 code review 표기
3. merge
```

각 단계는 독립 승인 지점을 가진다. 사용자가 전체 진행을 요청해도 commit, PR/MR 생성, merge는 각각 상태를 보고하고 승인받은 뒤 수행한다.

## 단계별 책임

### 1. Commit

`git-commit` 절차를 따른다.

- 변경사항 점검
- 민감정보/산출물 확인
- 최소 검증 실행
- 커밋 메시지 제안
- 사용자 승인 후 명시적 `git add`
- `git commit`

### 2. PR/MR 및 AI Code Review

`git-pr` 절차를 따른다.

- remote/base/head 확인
- push 전 미커밋 변경 알림
- push
- PR/MR 생성
- Codex 또는 Claude Code 협업 사실 표기
- PR/MR 생성 전 가능한 범위의 Codex code review 수행
- 실제 사용한 AI 도구만 본문에 기재

### 3. Merge

`git-merge` 절차를 따른다.

- PR/MR 상태 확인
- checks/CI 확인
- 충돌과 위험 파일 확인
- blocking 이슈 확인
- 사용자 승인 후 merge
- merge 후 상태 확인

## 금지

- 단계 승인 생략
- 사용자 승인 없는 push 또는 merge
- force push
- 검증하지 않은 항목을 검증 완료로 표기
- 실제 사용하지 않은 Codex/Claude Code 표기
- secret, private key, token 커밋

## 응답 형식

```markdown
git-flow 결과:
- commit: 수행함/대기/미수행
- PR/MR: <URL 또는 미생성>
- AI 협업 표기: Codex/Claude Code/없음
- checks: 성공/실패/대기/미확인
- merge: 수행함/대기/미수행

주의:
- 
```
