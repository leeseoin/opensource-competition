---
name: git-ship
description: 로컬 변경을 브랜치 생성 → 커밋 → 푸시 → MR/PR 생성 → 리뷰 → 머지 → (선택)브랜치 정리까지 한 흐름으로 진행하되, 각 단계마다 사용자 승인을 받고 진행할 때 사용한다. GitHub PR·GitLab MR 동시 운영(한 쪽 merge + 다른 쪽 mirror). "브랜치 파서 커밋하고 푸시하고 MR 날리고 리뷰하고 머지까지" 요청 시 사용.
metadata:
  short-description: 브랜치→커밋→푸시→MR→리뷰→머지 (단계별 승인)
---

# Git Ship — 단계별 승인 파이프라인

로컬 변경을 **브랜치 생성 → 커밋 → 푸시 → MR/PR → 리뷰 → 머지 → 브랜치 정리**까지 한 번에
진행하되, **모든 단계 전환마다 사용자에게 물어보고 승인받은 뒤에만** 다음 단계로 넘어간다.

세부 규약은 형제 스킬을 그대로 따른다: 커밋=`git-commit`, 푸시·MR/PR=`git-pr`,
리뷰·머지·mirror=`git-review-merge`. 이 스킬은 그 셋을 **게이트로 묶는 오케스트레이터**다.

## 핵심 원칙 — 게이트(gate)

- 각 단계는 **제안 → 사용자 승인 → 실행** 순서다. 승인 없이 실행하지 않는다.
- 사용자가 수정을 요청하면 그 단계에서 반영 후 **다시 확인**받는다.
- 사용자가 "여기까지만" 하면 그 단계에서 **멈춘다**(나머지 강행 금지).
- 되돌리기 어려운 단계(푸시·MR·머지·브랜치 삭제)는 특히 명시적 승인을 받는다.
- 이미 승인받은 범위를 임의로 확장하지 않는다(파일·브랜치·원격 추가 금지).

## 0. 사전 점검 (승인 불필요, 상태만 파악)

```bash
git status --short
git branch --show-current
git remote -v            # host 로 GitHub/GitLab 원격 이름 파악
git fetch --all --quiet
```

- 미커밋 변경 범위·민감정보(.env·키·덤프)·의도치 않은 파일을 확인한다.
- 원격이 GitHub·GitLab 둘 다면 "한 쪽 merge + 다른 쪽 mirror" 원칙을 적용(아래 6단계).

## 1. 게이트 — 브랜치명 제안

- 변경 내용(주제)에서 브랜치명을 **제안**하고 "이 이름으로 만들까요?" 라고 **묻는다**.
  - 관례: `feat/<주제>` · `fix/<주제>` · `refactor/<주제>` · `docs/<주제>` (kebab-case, 영문).
  - 이미 적절한 작업 브랜치에 있고 새로 팔 필요가 없으면 그 사실을 알리고 새로 팔지 물어본다.
- 사용자가 **이름을 확정**하면:

```bash
git checkout -b <승인된-브랜치명>
```

- (main/기본 브랜치 위에서 시작하는 게 보통 안전 — 그렇지 않으면 base 를 사용자와 확인.)

## 2. 게이트 — 커밋 (규약: `git-commit`)

- **커밋 대상 파일 + 논리 그룹 분할 + 커밋 메시지 초안 + 최소 검증 결과**를 제시하고 승인받는다.
  - Conventional Commit, 저장소 기존 언어/스타일 우선.
  - 변경 범위에 맞는 검증(테스트·lint·tsc·build)을 돌리고 결과를 보고. 못 돌린 건 명시.
- 승인 후 파일을 **명시적으로 stage** 하고 커밋(여러 논리 커밋이면 그룹별로). `git add .` 금지.
- 커밋 후 `git log --oneline` 과 남은 트리 상태를 보여준다.

## 3. 게이트 — 푸시 (규약: `git-pr`)

- "양쪽 원격(GitHub·GitLab)에 push 할까요?" 물어본 뒤 승인받아 **양쪽 모두** push.

```bash
git push -u <github-remote> HEAD
git push -u <gitlab-remote> HEAD
```

- 한쪽만 push 하면 갈라진다 — 원격이 둘이면 둘 다. force push 는 명시 승인 없이 금지.

## 4. 게이트 — MR/PR 생성 (규약: `git-pr`)

- **title + body(요약·변경·검증·주의) 초안**을 보여주고 승인받는다.
- 승인 후 GitHub PR·GitLab MR **둘 다** 생성(같은 title/body).

```bash
gh pr create --repo <owner/repo> --base <base> --head <branch> --title "<t>" --body-file <f>
glab mr create --repo <group/project> --source-branch <branch> --target-branch <base> --yes --title "<t>" --description "<body>"
```

- CLI 미인증/불가면 그쪽은 웹 링크 안내(멈추지 말 것).

## 5. 게이트 — 리뷰 (규약: `git-review-merge`)

- diff 를 **버그·보안·데이터손실·계약·회귀 우선**으로 검토(파일:라인 인용).
- 결과를 표로 정리해 **PR·MR 양쪽에 게시**하고, 사용자에게도 요약 보고.
- blocking 이슈가 있으면 여기서 멈추고 사용자와 상의(머지로 넘어가지 않는다).

## 6. 게이트 — 머지 + mirror (규약: `git-review-merge`)

- "머지할까요?" 승인받는다. **원격 둘이면 절대 양쪽 각각 merge 금지**(두 main 갈라짐).
  1. **canonical 한 쪽에서만 merge**(보통 팀 저장소=GitLab). 방식(merge/squash)·canonical 은 관례 기본, 애매하면 물어본다.
  2. 그 결과 main 을 **반대쪽 원격 main 으로 mirror**(FF push):
     ```bash
     git push <other-remote> <canonical-main-sha>:refs/heads/<base>
     ```
  3. **두 원격 main 동일 검증**(필수): `git rev-list --left-right --count <gh>/<base>...<gl>/<base>` → `0 0`.
  4. 반대쪽 PR/MR 은 자동 "merged" 로 닫힘.
- ⚠️ 하네스 가드: `gh pr merge`·`git push ...:main`·때론 `glab mr merge` 가 **자동 차단**될 수 있다.
  우회하지 말고 그 명령을 사용자가 `!` 프리픽스로 직접 실행하도록 정확히 제시한다.

## 7. 게이트 — 브랜치 정리 (선택)

- "머지된 브랜치 삭제할까요?" 물어보고 승인 시:

```bash
git checkout <base> && git merge --ff-only <canonical-remote>/<base>   # 로컬 base 최신화
git push <github-remote> --delete <branch>
git push <gitlab-remote> --delete <branch>
git branch -d <branch>
```

- 장기 브랜치(예: 진행 중 리팩토링)면 삭제하지 말고 유지 권고.

## 응답/보고

각 게이트에서 **무엇을 할지 제안 + 결과 요약**을 짧게 보여주고 승인을 구한다. 마지막에:

- 브랜치 / 커밋 수 / push / PR·MR URL / 리뷰 결과 / merge+mirror / 두 main 동일(0 0) / 브랜치 정리 상태를 정리.
- 검증하지 않은 것을 했다고 쓰지 않는다.
