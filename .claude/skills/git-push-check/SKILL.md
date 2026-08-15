---
name: git-push-check
description: Push/PR 전에 이 저장소의 Document Sync CI가 요구하는 문서 갱신(THIRD_PARTY_NOTICES.md, AI_USAGE.md)과 기본 검증을 빠짐없이 확인한다. develop/main에 push하거나 PR을 올리기 직전에 사용한다.
---

# Git Push 전 체크리스트

이 저장소는 push/PR마다 GitHub Actions **Document Sync** 워크플로가 돈다
(`.github/workflows/document-sync.yml` → `scripts/check-document-sync.sh`).
의존성이나 AI 관련 설정 파일이 바뀌었는데 대응 문서를 안 바꾸면 CI가 실패한다.
실제로 이 실수를 두 번 반복해 지적받은 적이 있다 — push 전에 반드시 아래를 확인한다.

## 1. 문서 동기화(Document Sync) 확인

바뀐 파일 목록(`git diff --name-only <base>...HEAD`)에 아래 패턴이 있으면
대응 문서도 **같은 push 범위**에 포함됐는지 확인한다.

**의존성/설정 파일 변경 → `THIRD_PARTY_NOTICES.md` 갱신 필요**

```
go.mod, go.sum, pyproject.toml, uv.lock, package.json, package-lock.json,
build.gradle(.kts), settings.gradle(.kts), gradle.properties,
gradle-wrapper.properties, compose.y(a)ml, Dockerfile*,
plugins/*/.codex-plugin/plugin.json, plugins/*/.mcp.json,
(models?|model-servers?)/ 아래 파일
```

→ 실제 의존성 추가가 없어도(예: package.json의 test 스크립트 목록만 늘어난
경우) `THIRD_PARTY_NOTICES.md`의 "최종 갱신일" 줄을 오늘 날짜로 올린다.
CI는 파일이 바뀌었는지만 보지 내용은 안 본다.

**AI/plugin/MCP 관련 파일 변경 → `AI_USAGE.md` 갱신 필요**

```
plugins/, services/mcp-server/, (models?|model-servers?)/,
(ollama|llama-cpp|llama_cpp)/, AGENTS.md, .agents/, .codex/
```

→ `AI_USAGE.md`도 같은 push 범위에 포함시킨다.

**로컬에서 CI와 동일하게 검증:**

```bash
./scripts/check-document-sync.sh "$(git merge-base HEAD origin/develop)"
```

이 스크립트는 base..HEAD 사이 변경 파일을 기준으로 판정한다. 커밋을 여러 개
쌓은 뒤라면 커밋 하나하나가 아니라 **push 직전 최종 diff** 기준으로 한 번 더
돌려 확인한다.

## 2. 병합/푸시 전 일반 체크

- `git status`로 의도치 않은 파일(`.idea/`, 스크린샷 등)이 add 안 됐는지 확인.
- 백엔드 변경 시 `./gradlew test`, 프런트 변경 시
  `npx tsc --noEmit && npm run lint && npm test`.
- `develop`에 합칠 때는 먼저 `git merge-tree $(git merge-base develop <branch>) develop <branch>`로
  충돌 여부를 보고, 충돌 시 양쪽 브랜치가 추가한 내용을 다 보존하도록 수동 해결한다.
- 병합/변경 후에는 실제로 서버를 띄우거나(백엔드 `bootRun`, 프런트 dev 서버) 핵심
  경로를 한 번 확인하고 push한다.

## 왜 필요한가

2026 오픈소스 개발자대회 운영규정이 의존성·AI 사용 출처 공개를 요구해서 생긴
CI 게이트다. 이 체크를 건너뛰면 push 자체는 되지만 `develop`/`main`에서
Document Sync 워크플로가 실패해 GitHub 알림이 온다.
