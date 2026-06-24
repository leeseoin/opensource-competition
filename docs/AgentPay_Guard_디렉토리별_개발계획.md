# AgentPay Guard 디렉토리별 개발 계획

작성일: 2026-06-24  
상태: planned

## 목적

이 문서는 AgentPay Guard PoC를 구성하는 4개 구현 프로젝트의 목표, 관계, 디렉토리별 개발 리스트를 정리한다.

`opensource-competition` 저장소 안에 문서와 구현 프로젝트를 함께 둔다.

```text
opensource-competition/
  docs/                            # 기획/문서/제출 산출물
  agentpay-guard-api-server/       # Spring Boot backend
  agentpay-guard-dashboard/        # React + TypeScript dashboard
  agentpay-guard-sample-agent/     # Python sample agent
  agentpay-guard-audit-anchor/     # Hardhat + Solidity contract
```

## 전체 관계

```text
User
  -> agentpay-guard-dashboard
  -> agentpay-guard-api-server
  -> PostgreSQL

agentpay-guard-sample-agent
  -> agentpay-guard-api-server 내부 mock merchant
  -> agentpay-guard-api-server payment request API

agentpay-guard-api-server
  -> agentpay-guard-audit-anchor contract
  -> Hardhat local node
```

역할 요약:

- `agentpay-guard-api-server`: 정책 판단, 승인/거절, mock 결제, 감사 이벤트, DB, mock merchant를 담당한다.
- `agentpay-guard-dashboard`: 사용자가 intent와 payment request를 보고 승인/거절하며, audit 결과를 확인하는 화면이다.
- `agentpay-guard-sample-agent`: AI Agent 역할을 흉내 내는 CLI 클라이언트이다.
- `agentpay-guard-audit-anchor`: eventHash를 기록하고 조회하는 Solidity 컨트랙트 프로젝트이다.

## 1. agentpay-guard-api-server

### 목표

Spring Boot 기반 AgentPay Guard 핵심 백엔드이다.

PoC에서 다음 책임을 가진다.

- 사용자 intent 관리
- agent 등록 관리
- payment request 관리
- 규칙 기반 Policy Engine 실행
- 승인/거절 처리
- Payment Simulator 실행
- Mock Merchant API 제공
- audit event와 eventHash 생성
- AuditAnchor 컨트랙트 연동
- PostgreSQL 저장

### 주요 기술

- Spring Boot
- Java 21 또는 Java 17
- Spring Web
- Spring Data JPA
- PostgreSQL
- Validation
- Flyway
- Springdoc OpenAPI
- web3j 또는 Hardhat script 호출 방식 검토

### 다른 프로젝트와의 관계

- `agentpay-guard-dashboard`가 호출하는 REST API를 제공한다.
- `agentpay-guard-sample-agent`가 payment request를 생성할 API를 제공한다.
- 내부 Mock Merchant가 sample agent에 402-style quote를 반환한다.
- `agentpay-guard-audit-anchor`의 컨트랙트에 eventHash를 기록한다.

### 개발 리스트

- Spring Boot 프로젝트 생성
- PostgreSQL 연결 설정
- Flyway migration 구조 작성
- 공통 API 응답 구조 작성
- 공통 예외 처리 작성
- validation 적용
- OpenAPI/Swagger 설정
- `users` entity/repository 구현
- `agents` entity/repository 구현
- `payment_intents` entity/repository 구현
- `payment_requests` entity/repository 구현
- `policy_decisions` entity/repository 구현
- `approvals` entity/repository 구현
- `payment_results` entity/repository 구현
- `audit_events` entity/repository 구현
- `audit_anchors` entity/repository 구현
- intent 생성/목록/상세 API 구현
- agent 생성/목록 API 구현
- payment request 생성/목록/상세 API 구현
- payment request evaluate API 구현
- approve/reject API 구현
- pay API 구현
- audit anchor 목록/검증 API 구현
- Policy Engine 구현
- Payment Simulator 구현
- Mock Merchant API 구현
- canonical JSON serializer 구현
- SHA-256 eventHash 생성 구현
- blockchain client 구현
- seed/demo data 생성
- 단위 테스트 작성
- 통합 테스트 작성

## 2. agentpay-guard-dashboard

### 목표

React + TypeScript 기반 PoC 시연용 대시보드이다.

사용자가 AgentPay Guard의 핵심 흐름을 화면에서 확인하고 조작할 수 있어야 한다.

### 주요 기술

- React
- TypeScript
- Vite 검토
- REST API client
- CSS 방식은 구현 시 결정

### 다른 프로젝트와의 관계

- `agentpay-guard-api-server`의 REST API를 호출한다.
- 사용자가 intent를 생성하고 payment request 상태를 확인한다.
- `REQUIRE_APPROVAL` 요청에 대해 승인/거절 API를 호출한다.
- audit anchor의 txHash와 verify 결과를 보여준다.

### 개발 리스트

- React + TypeScript 프로젝트 생성
- API base URL 환경 변수 설정
- API client 작성
- 기본 라우팅 구성
- 공통 레이아웃 작성
- Intent 목록 화면 구현
- Intent 생성 화면 구현
- Intent 상세 화면 구현
- Payment Request 목록 화면 구현
- Payment Request 상세 화면 구현
- 정책 판단 결과 표시
- 승인/거절 버튼 구현
- mock 결제 실행 버튼 구현
- Audit Anchor 목록 화면 구현
- txHash 표시
- hash verify 결과 표시
- 로딩/에러 상태 구현
- demo scenario 실행 흐름 정리
- dashboard 빌드/실행 문서 작성

## 3. agentpay-guard-sample-agent

### 목표

Python 기반 샘플 Agent이다.

실제 AI Agent 전체를 구현하지 않고, Agent가 유료 리소스를 사용하려는 상황을 CLI로 재현한다.

### 주요 기술

- Python
- requests 또는 httpx
- argparse 또는 Typer 검토
- `.env` 기반 설정

### 다른 프로젝트와의 관계

- `agentpay-guard-api-server` 내부 Mock Merchant를 호출한다.
- Mock Merchant의 402-style quote를 파싱한다.
- `agentpay-guard-api-server`에 payment request를 생성한다.
- 정책 결과에 따라 mock 결제 요청 또는 승인 대기 메시지를 출력한다.

### 개발 리스트

- Python 프로젝트 구조 생성
- 의존성 파일 작성
- 설정 파일 작성
- CLI 옵션 정의
- intent id 입력 처리
- mock merchant resource 요청 구현
- 402-style quote 파싱 구현
- payment request 생성 API 호출 구현
- evaluate 결과 조회 구현
- `ALLOW` 처리 흐름 구현
- `REQUIRE_APPROVAL` 처리 흐름 구현
- `DENY` 처리 흐름 구현
- `PAID` 후 resource 재요청 구현
- CLI 출력 포맷 정리
- 정상 허용 시나리오 스크립트 작성
- 예산 초과 차단 시나리오 스크립트 작성
- 승인 필요 시나리오 스크립트 작성
- README 작성

## 4. agentpay-guard-audit-anchor

### 목표

Hardhat + Solidity 기반 감사 hash anchoring 프로젝트이다.

블록체인에는 원문 데이터를 올리지 않고 eventHash만 기록한다.

### 주요 기술

- Solidity
- Hardhat
- TypeScript 또는 JavaScript deploy script
- Hardhat local node

### 다른 프로젝트와의 관계

- `agentpay-guard-api-server`가 생성한 eventHash를 컨트랙트에 기록한다.
- `agentpay-guard-api-server`가 컨트랙트에서 eventHash를 조회해 DB hash와 비교한다.
- `agentpay-guard-dashboard`는 API server를 통해 txHash와 verify 결과를 확인한다.

### 개발 리스트

- Hardhat 프로젝트 생성
- `AuditAnchor.sol` 작성
- `anchorEvent` 함수 구현
- `getEventHash` 함수 구현
- `EventAnchored` 이벤트 emit 구현
- 중복 eventId 처리 정책 구현
- 컨트랙트 단위 테스트 작성
- Hardhat local node 실행 문서 작성
- local deploy script 작성
- ABI 추출 방식 정리
- contract address 출력/저장 방식 정리
- API server 연동용 ABI/address 전달 방식 정리
- optional 테스트넷 배포 스크립트 작성

## 개발 순서

1. `agentpay-guard-api-server` 기본 프로젝트와 PostgreSQL 연결을 먼저 만든다.
2. `agentpay-guard-audit-anchor` 컨트랙트 skeleton을 만들고 Hardhat local node에서 배포한다.
3. API server에서 intent/payment request/policy decision까지 먼저 구현한다.
4. API server 내부 Mock Merchant와 Payment Simulator를 구현한다.
5. `agentpay-guard-sample-agent`로 quote -> payment request -> evaluate 흐름을 붙인다.
6. audit eventHash 생성과 컨트랙트 anchoring을 붙인다.
7. `agentpay-guard-dashboard`에서 intent, payment request, approval, audit verify 화면을 만든다.
8. 3개 MVP 시나리오를 end-to-end로 검증한다.

## PoC 완료 기준

- 4개 구현 프로젝트의 실행 방법이 문서화되어 있다.
- PostgreSQL 기반 API server가 실행된다.
- Hardhat local node와 AuditAnchor 컨트랙트가 실행된다.
- sample agent가 mock merchant quote를 받고 payment request를 생성한다.
- Policy Engine이 `ALLOW`, `REQUIRE_APPROVAL`, `DENY`를 판단한다.
- dashboard에서 승인/거절과 audit verify 결과를 확인할 수 있다.
- 정상 허용, 예산 초과 차단, 승인 필요 시나리오가 모두 시연 가능하다.
