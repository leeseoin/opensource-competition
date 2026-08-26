# 2026-08-11 WEB-001 공통 Radix Select

- 기능 ID: `WEB-001`
- 구현 commit: `62d50a2`
- 기록 상태: 구현 기록

## 배경과 범위

실행 환경과 구매 조건 강도 입력이 browser 기본 select를 사용해 운영체제마다 모양이
달랐다. 접근 가능한 공통 dropdown과 프로젝트의 Editorial Commerce 시각 규칙으로
통일했다.

## 구현 내용

- `frontend/purchase-web/app/components/ui/app-select.tsx:22` `AppSelect`: Radix Select 기반
  trigger/portal/item/indicator와 공통 option 계약
- `frontend/purchase-web/app/components/ui/app-select.module.css:1` `.trigger`: paper/lime tone,
  hover/focus/open/disabled/reduced-motion 상태
- `frontend/purchase-web/app/chat/chat-experience.tsx:27` `runtimeOptions`: 실행 환경 및
  필수/선호 option을 공통 컴포넌트로 사용
- `frontend/purchase-web/package.json:13` `@radix-ui/react-select`: 2.3.7 직접 의존성
- `THIRD_PARTY_NOTICES.md:92` `Radix UI Select`: 출처와 MIT 라이선스 공개

## 발생 문제와 원인

native select는 macOS/browser 기본 rendering을 사용해 기존 직각 border, lime/paper 배경과
focus 규칙이 일관되지 않았다.

## 해결

한 개의 headless 공통 컴포넌트로 모든 native select를 교체했다. 선택 목록은 portal로
표시하며 키보드 탐색과 focus 관리는 Radix가 담당하고 시각 표현은 프로젝트 CSS가 담당한다.

## 검증

- `rg '<select' frontend/purchase-web/app`: native select 0건
- `cd frontend/purchase-web && npm test`: 29개 통과
- `cd frontend/purchase-web && npm run lint`: 통과
- `cd frontend/purchase-web && npm run build`: production build 통과
- `make docs-check`: 통과

## 남은 작업

- 현재 자동화 브라우저 세션을 사용할 수 없어 실제 dropdown 시각/키보드 동작은 사용자
  브라우저에서 최종 확인해야 한다.
- `npm audit --omit=dev`의 production high 4건은 Radix가 아니라 기존 Next.js의
  nanoid/postcss/sharp 경로다. 강제 Next.js 업그레이드 전 별도 회귀 검토가 필요하다.
