---
name: git-commit-push
description: AI Recipe Server 저장소에서 git 변경사항을 점검하고, 사용자 승인 후 commit 및 push를 수행할 때 사용한다.
metadata:
  short-description: git commit/push
---

# Git Commit & Push

변경사항을 안전하게 확인한 뒤 commit/push를 수행한다.

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

- `.env`, API 키, 토큰, 비밀번호가 포함되지 않았는지 확인한다.
- `backend/record/`, `app/build/`, `.dart_tool/`, `__pycache__/`, 가상환경 파일이 포함되지 않았는지 확인한다.
- 사용자가 만든 변경과 Codex가 만든 변경을 구분한다.
- 의도하지 않은 대량 생성 파일은 커밋 대상에서 제외한다.

4. 필요한 검증을 실행한다.

변경 범위에 따라 최소 검증만 수행한다.

```bash
python -m py_compile backend/routers/voice.py backend/services/llm.py backend/services/session.py
flutter analyze
```

5. 사용자에게 커밋 대상 파일, 검증 결과, 커밋 메시지 초안을 제시하고 승인을 받는다.

6. 승인 후 명시적으로 파일을 stage한다.

```bash
git add <file1> <file2>
```

7. 커밋한다.

```bash
git commit -m "<message>"
```

8. push한다.

```bash
git push
```

## 커밋 메시지 원칙

- 짧은 한글 Conventional Commit을 기본으로 쓴다.
- 타입은 `feat`, `fix`, `docs`, `test`, `refactor`, `chore` 등 Conventional Commit 형식을 유지한다.
- 제목은 한글로 작성하고, 변경 의도가 바로 드러나게 쓴다.
- 예:
  - `feat: 사용자 설정 저장 기능 추가`
  - `docs: API 사용 예시 문서 추가`
  - `fix: 빈 응답 처리 오류 수정`
  - `test: 입력 검증 테스트 추가`

## 주의사항

- `git add .`는 사용하지 않는다. 항상 파일을 명시한다.
- 사용자 승인 없이 commit/push하지 않는다.
- destructive 명령은 사용하지 않는다.
- push 실패가 인증/네트워크 문제면 원인을 짧게 설명하고 멈춘다.
- 브랜치가 불명확하면 push 전에 사용자에게 확인한다.
