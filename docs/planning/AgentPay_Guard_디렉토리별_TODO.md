# AgentPay Guard 디렉토리별 ToDo

작성일: 2026-06-24  
상태: active

## 목적

이 문서는 AgentPay Guard monorepo의 각 구현 디렉토리별 개발 ToDo를 실행 가능한 단위로 정리한다.

우선순위 기준:

```text
P0: PoC end-to-end 시연에 반드시 필요
P1: PoC 품질과 협업 안정성에 필요
P2: 시간이 남으면 고도화
```

## 현재 디렉토리

```text
agentpay-guard-api-server/
agentpay-guard-dashboard/
agentpay-guard-sample-agent/
agentpay-guard-audit-anchor/
```

## 1. agentpay-guard-api-server

역할:

```text
Spring Boot backend.
Intent, Agent, Payment Request, Policy Engine, Approval, Payment Simulator,
Mock Merchant, Audit/Hash, Blockchain 연동을 담당한다.
```

현재 완료:

- Spring Boot 프로젝트 생성
- PostgreSQL datasource 설정
- Docker PostgreSQL 연결 확인
- Flyway dependency 추가
- `V1__init_schema.sql` 작성
- 초기 테이블 생성 확인
- Springdoc OpenAPI dependency 추가
- `command.md` 작성

### P0 ToDo

- [ ] 공통 패키지 구조 생성
  - `common`
  - `user`
  - `agent`
  - `intent`
  - `payment`
  - `policy`
  - `approval`
  - `merchantmock`
  - `audit`
  - `blockchain`
- [ ] 공통 API 응답 포맷 정의
- [ ] 공통 예외 처리 구현
- [ ] request validation 적용
- [ ] `users` entity/repository 구현
- [ ] `agents` entity/repository 구현
- [ ] `payment_intents` entity/repository 구현
- [ ] `payment_requests` entity/repository 구현
- [ ] `policy_decisions` entity/repository 구현
- [ ] `approvals` entity/repository 구현
- [ ] `payment_results` entity/repository 구현
- [ ] `audit_events` entity/repository 구현
- [ ] `audit_anchors` entity/repository 구현
- [ ] enum 정의
  - payment request status
  - policy decision
  - approval decision
  - payment result status
  - audit verify status
- [ ] Intent 생성 API 구현
- [ ] Intent 목록 API 구현
- [ ] Intent 상세 API 구현
- [ ] Agent 생성 API 구현
- [ ] Agent 목록 API 구현
- [ ] Payment Request 생성 API 구현
- [ ] Payment Request 목록 API 구현
- [ ] Payment Request 상세 API 구현
- [ ] Payment Request evaluate API 구현
- [ ] Policy Engine 1차 규칙 구현
  - active intent 검사
  - 만료 검사
  - 총 예산 검사
  - 단건 한도 검사
  - merchant allowlist 검사
  - merchant blocklist 검사
  - category 검사
  - 승인 필요 금액 검사
  - prompt injection 의심 문구 검사
- [ ] Approval approve API 구현
- [ ] Approval reject API 구현
- [ ] Payment Simulator pay API 구현
- [ ] Mock Merchant quote API 구현
- [ ] Mock Merchant paid resource redeem API 구현
- [ ] canonical JSON envelope 구현
- [ ] SHA-256 eventHash 생성 구현
- [ ] Audit event 저장 구현

### P1 ToDo

- [ ] seed/demo data 전략 확정
- [ ] demo user/agent/intent seed 작성
- [ ] Swagger tag/description 정리
- [ ] API request/response 예시 문서화
- [ ] Repository/service/controller 테스트 추가
- [ ] Policy Engine 단위 테스트 추가
- [ ] payment request 상태 전이 테스트 추가
- [ ] 중복 결제 방지 로직 추가
- [ ] quoteId/quoteHash 중복 처리 추가
- [ ] `spring.jpa.open-in-view=false` 설정 검토
- [ ] `.env.example` 또는 profile 기반 설정 정리

### P2 ToDo

- [ ] web3j 직접 연동 구현 또는 Hardhat script 호출 방식 확정
- [ ] AuditAnchor eventHash 기록 API 구현
- [ ] AuditAnchor verify API 구현
- [ ] batch hash anchoring 검토
- [ ] policy version 테이블 분리 검토
- [ ] merchant/category 별도 테이블 분리 검토

## 2. agentpay-guard-dashboard

역할:

```text
React + TypeScript dashboard.
사용자가 intent, payment request, approval, audit 상태를 보고 조작하는 화면을 담당한다.
```

현재 완료:

- React + TypeScript 프로젝트 골격 생성됨
- Vite 설정 파일 존재
- `package.json`, `package-lock.json` 존재
- `node_modules`는 로컬 설치물이며 커밋 대상이 아님

### P0 ToDo

- [ ] 현재 Vite scaffold 정리
- [ ] dashboard README 업데이트
- [ ] `.env.example` 작성
- [ ] API base URL 환경 변수 정의
- [ ] API client 기본 구조 작성
- [ ] 라우팅 구조 작성
- [ ] 기본 레이아웃 작성
- [ ] Intent 목록 화면 구현
- [ ] Intent 생성 화면 구현
- [ ] Intent 상세 화면 구현
- [ ] Payment Request 목록 화면 구현
- [ ] Payment Request 상세 화면 구현
- [ ] 정책 판단 결과 표시
- [ ] 승인 버튼 구현
- [ ] 거절 버튼 구현
- [ ] mock 결제 실행 버튼 구현
- [ ] Audit Anchor 목록 또는 상세 영역 구현
- [ ] txHash 표시
- [ ] hash verify 상태 표시

### P1 ToDo

- [ ] loading 상태 구현
- [ ] error 상태 구현
- [ ] empty state 구현
- [ ] form validation 구현
- [ ] API 에러 메시지 표시
- [ ] demo scenario용 빠른 입력값 제공
- [ ] dashboard build 검증
- [ ] dashboard lint 검증
- [ ] README에 실행 방법 작성

### P2 ToDo

- [ ] intent별 사용량 요약
- [ ] agent별 payment request 통계
- [ ] merchant별 사용량 표시
- [ ] 정책 판단 사유 시각화
- [ ] txHash explorer 링크
- [ ] 반응형 UI 보강

## 3. agentpay-guard-sample-agent

역할:

```text
Python sample agent.
AI Agent가 유료 리소스를 사용하려는 흐름을 CLI로 재현한다.
```

현재 완료:

- placeholder README 존재

### P0 ToDo

- [ ] Python 프로젝트 구조 생성
- [ ] dependency 파일 작성
  - `requirements.txt` 또는 `pyproject.toml`
- [ ] 설정 파일 구조 작성
- [ ] API base URL 설정
- [ ] CLI entrypoint 작성
- [ ] intent id 입력 처리
- [ ] resource 입력 처리
- [ ] Mock Merchant 호출 구현
- [ ] 402-style quote 응답 파싱 구현
- [ ] Guard payment request 생성 API 호출 구현
- [ ] evaluate API 호출 구현
- [ ] `ALLOW` 결과 처리
- [ ] `REQUIRE_APPROVAL` 결과 처리
- [ ] `DENY` 결과 처리
- [ ] `PAID` 후 resource 재요청 처리
- [ ] CLI 출력 포맷 정리

### P1 ToDo

- [ ] 정상 허용 시나리오 스크립트 작성
- [ ] 예산 초과 차단 시나리오 스크립트 작성
- [ ] 승인 필요 시나리오 스크립트 작성
- [ ] `.env.example` 작성
- [ ] README에 실행 방법 작성
- [ ] API server 미실행 시 에러 메시지 개선
- [ ] JSON 출력 옵션 추가

### P2 ToDo

- [ ] 여러 resource 연속 요청 시나리오
- [ ] prompt injection 의심 reason 시나리오
- [ ] retry/backoff 처리
- [ ] 간단한 demo recording script

## 4. agentpay-guard-audit-anchor

역할:

```text
Hardhat + Solidity contract.
eventHash를 로컬 블록체인에 기록하고 조회한다.
```

현재 완료:

- placeholder README 존재

### P0 ToDo

- [ ] Hardhat 프로젝트 생성
- [ ] Solidity 버전 결정
- [ ] `AuditAnchor.sol` 작성
- [ ] `anchorEvent` 함수 구현
- [ ] `getEventHash` 함수 구현
- [ ] `EventAnchored` 이벤트 정의
- [ ] 중복 eventId 처리 정책 구현
- [ ] 컨트랙트 단위 테스트 작성
- [ ] Hardhat local node 실행 방법 작성
- [ ] local deploy script 작성
- [ ] deploy 결과로 contract address 출력
- [ ] ABI 위치 정리

### P1 ToDo

- [ ] API server 연동용 ABI/address 전달 방식 정리
- [ ] contract address 파일 생성 방식 정의
- [ ] README 실행 방법 작성
- [ ] eventHash 조회 테스트 추가
- [ ] 잘못된 hash/eventId 케이스 테스트 추가

### P2 ToDo

- [ ] Sepolia 배포 script 작성
- [ ] explorer 링크 표시용 network config 정리
- [ ] batch hash anchoring 실험
- [ ] Merkle root 기반 anchoring 검토

## 5. 공통 / Monorepo ToDo

### P0 ToDo

- [ ] 루트 `.gitignore` 정리
  - `node_modules/`
  - `dist/`
  - `build/`
  - `.gradle/`
  - `.env`
  - DB dump
- [ ] 루트 `.env.example` 작성
- [ ] README 빠른 시작 최신화
- [ ] Docker PostgreSQL 실행 검증 유지
- [ ] DB 협업 정책 유지
- [ ] Flyway migration 변경 규칙 유지

### P1 ToDo

- [ ] 전체 실행 순서 문서화
  - postgres
  - api-server
  - dashboard
  - hardhat
  - sample-agent
- [ ] 데모 시나리오 실행 순서 작성
- [ ] API server와 dashboard CORS 정책 정리
- [ ] 포트 목록 정리
  - API server
  - dashboard
  - PostgreSQL
  - Hardhat node
- [ ] 커밋 전 검증 명령 정리

### P2 ToDo

- [ ] 루트 task runner 검토
- [ ] CI 기본 workflow 검토
- [ ] 라이선스 정리
- [ ] 오픈소스 사용 목록 정리

## 추천 개발 순서

1. 루트 `.gitignore` 정리
2. API server entity/repository 구현
3. Intent / Agent / Payment Request API 구현
4. Policy Engine 1차 구현
5. Approval / Payment Simulator 구현
6. Mock Merchant 구현
7. Sample Agent 구현
8. Audit hash 구현
9. AuditAnchor 컨트랙트 구현
10. Dashboard 구현
11. 3개 MVP 시나리오 통합 검증

## 이번 주 우선순위

이번 주는 API server 기반을 먼저 닫는다.

- [ ] 루트 `.gitignore` 정리
- [ ] entity/repository 생성
- [ ] 공통 응답/예외 구조 생성
- [ ] Intent API 구현
- [ ] Agent API 구현
- [ ] Payment Request 생성/조회 API 구현
- [ ] Swagger에서 API 확인
