---
name: security-review
description: 현재 작업 디렉토리의 프로젝트에서 보안 점검, 취약점 리뷰, threat model, API/웹/인프라/LLM 보안 검토를 요청받았을 때 사용한다.
metadata:
  short-description: 보안 점검 및 취약점 리뷰
---

# 보안 리뷰

현재 작업 디렉토리의 실제 코드와 배포 구성을 기준으로 공격 표면을 찾고, 재현 가능한 위험과 수정안을 제시한다. 특정 프로젝트 구조를 가정하지 않는다.

## 탐색 순서

1. 프로젝트 구조 확인
   - `pwd`, `git status --short`, `rg --files`
   - 주요 매니페스트: `package.json`, `pom.xml`, `build.gradle`, `requirements.txt`, `pyproject.toml`, `go.mod`, `Cargo.toml`, `Dockerfile`, `docker-compose*.yml`

2. 외부 입력 지점 찾기
   - HTTP route/controller/handler, WebSocket, upload, CLI argument, scheduler, webhook
   - 정적 파일과 공개 API, reverse proxy 경로, container port
   - 사용자 입력이 파일/명령/DB/외부 URL/LLM/API key까지 흐르는 경로

3. 보호 대상 확인
   - `.env`, 토큰, API key, 인증 쿠키, DB, 사용자 데이터, 내부망 URL, 운영 설정
   - 영상/파일/로그/메트릭처럼 노출되면 안 되는 운영 데이터

4. 위험도 높은 항목 우선 리뷰
   - 인증/인가 누락, CORS/CSRF/session/cookie 설정
   - 업로드 크기/확장자/MIME 검증, path traversal, zip-slip
   - SSRF, command injection, template injection, SQL/NoSQL injection
   - XSS, open redirect, iframe/embed 정책, CSP 부재
   - 비밀값 커밋, 로그에 credential 출력, 문서의 실제 키
   - Docker socket, privileged container, broad volume mount, host network
   - Nginx/proxy 설정의 내부 서비스 노출, WebSocket upgrade, auth 우회
   - LLM 사용 시 prompt injection, 도구 호출 권한, 비용 폭증, 민감정보 프롬프트 유입

## 리뷰 절차

1. 관련 파일을 먼저 읽고 실제 데이터 흐름을 요약한다.
2. 외부 입력이 신뢰 경계를 넘어 어디까지 전달되는지 추적한다.
3. 실제 코드/설정에 근거한 문제만 발견 사항으로 올린다.
4. 각 발견 사항에 영향, 재현 조건, 수정 방향을 포함한다.
5. 확실하지 않은 내용은 추정으로 표시하고, 확인 방법을 제시한다.
6. 수정 요청이 있으면 작은 패치부터 적용하고 관련 검증을 수행한다.

## 명령 가이드

- 파일 탐색은 `rg`/`rg --files` 우선.
- 비밀값 점검은 실제 값을 출력하지 않는다. 위치와 유형만 요약한다.
- `.env`, DB, key 파일은 필요 이상으로 열지 않는다.
- destructive 명령은 사용하지 않는다.

## 출력 형식

```markdown
## 보안 리뷰

### 발견 사항

| # | 심각도 | 위치 | 내용 | 권장 조치 |
|---|--------|------|------|-----------|
| 1 | High | `path:line` | ... | ... |

### Threat Model
- 외부 입력:
- 보호 대상:
- 신뢰 경계:

### 우선 수정안
1. ...
2. ...

### 검증
- ...
```

발견 사항이 없으면 "확인된 취약점 없음"이라고 명확히 말하고, 남은 검증 공백이나 운영상 잔여 위험을 적는다.

## 심각도 기준

- Critical: 원격 코드 실행, 비밀값 유출, 인증 우회, 광범위 데이터 유출처럼 즉시 악용 가능한 문제.
- High: 서비스 장애, 비용 폭증, 사용자 데이터 노출, 내부망 접근, 넓은 범위의 입력 악용.
- Medium: 특정 조건에서 악용 가능하거나 운영 전환 시 문제가 되는 설계 결함.
- Low: 방어 강화, 로깅/문서 정리, 명확성 개선.

## 수정 원칙

- 보안상 의미 있는 제한을 먼저 둔다. 예: 인증, 입력 길이, 업로드 크기, allowlist, timeout, rate limit.
- 사용자가 요청하지 않았다면 인증/DB/대형 인프라를 새로 도입하지 않는다.
- 실제 비밀값은 읽거나 출력하지 않고, 필요한 경우 placeholder 전환만 제안한다.
- 공격 절차는 재현에 필요한 수준으로만 설명하고, 남용 가능한 세부 절차는 피한다.
- 수정 후 최소 검증을 수행한다. 예: unit test, lint, config parse, curl health check, `git diff --check`.
