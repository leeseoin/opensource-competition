---
name: git-commit
description: 현재 저장소의 로컬 변경사항을 점검하고, 사용자 승인 후 git commit까지만 수행할 때 사용한다. push, PR/MR 생성, merge는 이 스킬에서 수행하지 않는다.
metadata:
  short-description: git commit
---

# Git Commit

로컬 변경사항을 안전하게 확인한 뒤 commit까지만 수행한다. 원격 push와 PR/MR 생성은 `git-pr` 스킬의 책임이다.

## 절차

1. 저장소 상태를 확인한다.

```bash
git status --short
git branch --show-current
git remote -v
```

2. 변경 파일을 점검한다.

```bash
git diff --stat
git diff -- <file>
```

3. 커밋 전 위험 요소를 확인한다.

- `.env`, API key, token, password, 인증서/private key가 포함되지 않았는지 확인한다.
- 빌드 산출물, 캐시, IDE 설정, 대용량 바이너리, DB 덤프가 의도치 않게 포함되지 않았는지 확인한다.
- 사용자가 만든 변경과 Codex가 만든 변경을 구분한다.
- 의도하지 않은 대량 생성 파일은 커밋 대상에서 제외한다.

4. 변경 범위에 맞는 최소 검증을 실행한다.

- 프로젝트의 기존 명령을 우선한다. 예: `npm test`, `npm run lint`, `./gradlew test`, `mvn test`, `pytest`, `go test ./...`.
- 검증 명령을 알 수 없으면 README/AGENTS/docs/package manifest를 먼저 확인한다.
- 실행하지 못한 검증은 최종 응답에 명확히 남긴다.

5. 사용자에게 커밋 대상 파일, 검증 결과, 커밋 메시지 초안을 제시하고 승인받는다.

6. 승인 후 파일을 명시적으로 stage한다.

```bash
git add <file1> <file2>
```

7. 커밋한다.

```bash
git commit -m "<message>"
```

8. commit hash와 남은 working tree 상태를 확인한다.

```bash
git log --oneline -1
git status --short
```

## 커밋 메시지 원칙

- Conventional Commit 형식을 기본으로 쓴다.
- 타입은 `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci` 중 적절히 선택한다.
- 제목은 짧고 변경 의도가 드러나게 작성한다.
- 저장소의 기존 커밋 언어/스타일을 우선한다.

예:

- `feat: 사용자 설정 저장 기능 추가`
- `docs: 배포 절차 문서 정리`
- `fix: 빈 응답 처리 오류 수정`
- `test: 입력 검증 테스트 추가`

## 주의사항

- `git add .`는 기본적으로 사용하지 않는다. 항상 파일을 명시한다.
- 사용자 승인 없이 commit하지 않는다.
- push, PR/MR 생성, merge는 수행하지 않는다.
- destructive 명령은 사용하지 않는다.
- 브랜치가 불명확하거나 변경 범위가 섞여 있으면 commit 전에 사용자에게 확인한다.
