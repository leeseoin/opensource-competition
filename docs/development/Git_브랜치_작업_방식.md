# Git 브랜치 작업 방식

- 작성일: 2026-07-29
- 목적: 개인 실험과 협업 코드를 분리하면서 빠르게 개발하기 위한 기준

## 1. 전체 흐름

```text
sandbox/ls
    ↓ 정리된 커밋만 이동
dev-ls ── PR ──┐
                ├──> develop ── PR ──> main
dev-jw ── PR ──┘
```

브랜치를 한 문장으로 설명하면 다음과 같다.

- `sandbox/ls`: 서인의 자유로운 실험 공간
- `dev-ls`: 서인이 협업에 공유할 수 있도록 정리한 작업
- `dev-jw`: 정우가 협업에 공유할 수 있도록 정리한 작업
- `develop`: 두 사람의 코드를 합쳐서 함께 검사하는 공간
- `main`: 시연 및 배포가 가능한 안정 버전

## 2. 브랜치별 사용 기준

### 2.1 `sandbox/ls`

서인이 혼자 빠르게 실험하는 브랜치다.

허용하는 작업:

- 동작 여부를 확인하기 위한 빠른 구현
- 구조 변경 실험
- 실패할 수 있는 코드
- 작업 중간 WIP 커밋
- 여러 방법을 비교하기 위한 임시 코드

지켜야 할 기준:

- `sandbox/ls`에서 `develop`으로 직접 PR을 만들지 않는다.
- API key, 비밀번호, 쿠키 및 개인정보는 실험 브랜치에도 커밋하지 않는다.
- 협업에 전달할 때는 필요한 커밋만 `dev-ls`로 옮긴다.
- 실험이 실패해도 `dev-ls`, `develop`, `main`을 되돌리지 않는다.

### 2.2 `dev-ls`

서인이 만든 코드 중 다른 사람이 읽고 검토할 수 있는 작업을 모은다.

들어올 수 있는 작업:

- `sandbox/ls`에서 검증한 커밋
- 한국어 주석 규칙을 적용한 코드
- 관련 테스트가 통과한 코드
- 계약과 TODO 및 개발 기록이 함께 갱신된 기능

`dev-ls`에서 `develop`으로 PR을 만들기 전에 확인한다.

- 기능이 실제로 실행되는가
- 관련 테스트가 통과하는가
- 미완성 기능을 완료로 표시하지 않았는가
- 민감정보와 임시 결과물이 포함되지 않았는가
- 협업자가 이해할 수 있는 PR 설명이 있는가

### 2.3 `dev-jw`

정우의 개인 통합 브랜치다. 역할과 PR 기준은 `dev-ls`와 같다.

정우의 작업은 `dev-jw`에서 정리한 뒤 `develop`으로 PR을 만든다. 서인의
`sandbox/ls` 작업을 바로 가져오지 않고, 필요한 경우 `develop`에 합쳐진 결과를
기준으로 동기화한다.

### 2.4 `develop`

협업 결과를 합치고 전체 동작을 확인하는 브랜치다.

규칙:

- `dev-ls` 또는 `dev-jw`의 PR로만 변경한다.
- 일반 기능 개발을 `develop`에서 직접 시작하지 않는다.
- PR에서 충돌, 테스트 결과, 계약 변경 및 문서 변경을 확인한다.
- 두 사람의 기능이 함께 실행되는지 통합 테스트한다.
- `develop`에서 확인되지 않은 코드는 `main`으로 보내지 않는다.

### 2.5 `main`

시연, 제출 및 배포 기준이 되는 안정 브랜치다.

규칙:

- 기본적으로 `develop`에서 만든 PR만 받는다.
- 직접 push하지 않는다.
- 실행 방법과 migration이 검증된 상태를 유지한다.
- 긴급 수정이 필요하면 별도 수정 브랜치에서 검증한 뒤 PR로 반영한다.

## 3. 실제 작업 순서

### 3.1 자유롭게 실험할 때

```bash
git switch sandbox/ls
git pull
```

실험 작업을 커밋하고 원격에 백업한다.

```bash
git add <변경한 파일>
git commit -m "wip: 수집 작업 분할 실험"
git push
```

### 3.2 성공한 실험을 `dev-ls`로 옮길 때

먼저 옮길 커밋 ID를 확인한다.

```bash
git log --oneline sandbox/ls
```

`dev-ls`를 최신 상태로 만든 뒤 필요한 커밋만 가져온다.

```bash
git switch dev-ls
git pull
git cherry-pick <커밋 ID>
```

실험 커밋이 너무 많으면 모든 WIP 커밋을 그대로 옮기지 않는다. 기능 단위로 정리한
커밋을 새로 만들거나 squash한 뒤 옮긴다.

### 3.3 `develop`으로 협업 PR을 만들 때

1. `dev-ls` 또는 `dev-jw`에서 테스트한다.
2. 개인 브랜치를 원격에 push한다.
3. GitHub에서 base를 `develop`으로 선택한다.
4. 변경 목적, 구현 내용, 테스트 결과 및 남은 문제를 PR에 작성한다.
5. 충돌과 리뷰 의견을 해결한 뒤 merge한다.

PR 방향을 반드시 확인한다.

```text
base: develop
compare: dev-ls 또는 dev-jw
```

### 3.4 협업 결과를 `main`으로 반영할 때

`develop`의 통합 검증이 끝난 경우에만 PR을 만든다.

```text
base: main
compare: develop
```

`main` PR에서는 다음 항목을 다시 확인한다.

- Go 및 Spring Boot 테스트
- Next.js lint와 production build
- Docker Compose 설정
- DB migration 적용 가능 여부
- 실행 문서와 환경변수 예제
- 실제 시연 시나리오

## 4. `develop` 변경을 개인 브랜치에 반영하는 방법

다른 사람의 코드가 `develop`에 합쳐졌다면 새 작업을 시작하기 전에 개인 브랜치에
반영한다.

```bash
git fetch origin
git switch dev-ls
git merge origin/develop
git push
```

`dev-jw`도 같은 방식으로 `origin/develop`을 merge한다.

공유하는 장기 브랜치에서는 강제 push가 필요한 rebase보다 일반 merge를 우선한다.
충돌이 발생하면 관련 파일 담당자와 확인하고 해결한다.

## 5. 브랜치를 바꾸기 전 확인

먼저 작업 트리를 확인한다.

```bash
git status --short
```

커밋되지 않은 변경이 있다면 다음 중 하나를 선택한다.

- 현재 브랜치에 커밋한다.
- 아직 커밋할 수 없다면 `git stash`로 임시 보관한다.
- 불필요한 변경인지 담당자에게 확인한다.

기존 사용자 변경을 임의로 삭제하거나 다른 파일로 덮어쓰지 않는다.

## 6. 현재 생성된 브랜치

2026-07-29 기준으로 다음 브랜치를 로컬과 원격에 생성했다.

| 브랜치 | 생성 기준 | 용도 |
|---|---|---|
| `develop` | `origin/main` | 협업 통합 및 테스트 |
| `sandbox/ls` | `dev-ls`의 `adbbccc` | 서인 개인 실험 |

브랜치 생성 직후에는 `dev-ls`를 유지했고, 이후 개인 구조 실험을 위해
`sandbox/ls`로 전환했다. 브랜치를 생성할 때 남아 있던
`frontend/purchase-web/README.md` 변경은 삭제하지 않고 현재 구조에 맞게 갱신했다.
