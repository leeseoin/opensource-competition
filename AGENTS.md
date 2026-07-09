# AGENTS.md

## 프로젝트 개요

- 프로젝트명: AgentPay Guard
- 목적: AI Agent가 유료 API, 구독형 서비스, 크레딧 기반 서비스, 사용량 기반 외부 리소스를 사용하기 전에 사용자 intent, 예산, 허용 서비스, 위험 요소를 검증하고 감사 가능한 기록을 남기는 보안 게이트웨이 PoC를 구현한다.
- 핵심 방향: 실제 결제 시스템이 아니라 mock 결제와 감사 hash anchoring을 사용하는 PoC이다.
- 현재 상태: PoC 초기 구현 진행 중. API server, audit anchor, dashboard, sample agent 기본 구조가 생성되어 있으며, 구현된 기능과 planned 항목을 문서에서 구분한다.
- 주요 결정:
  - DB: PostgreSQL
  - Dashboard: React + TypeScript
  - Mock Merchant: 1차 PoC에서는 API server 내부 모듈로 구현
  - Blockchain: Hardhat local node 우선, 테스트넷 배포는 optional
  - eventHash: 정렬된 canonical JSON envelope + SHA-256
  - 구현 프로젝트는 `opensource-competition` 저장소 루트 하위 디렉토리로 둔다.

## 현재 상태

- 완료:
  - AgentPay Guard 기획안 작성
  - PoC 범위 정의
  - 작업 목록 및 일정 초안 작성
  - 고도화 방향 정리
  - Spring Boot API server 기본 구현
  - Hardhat + Solidity AuditAnchor 로컬 구현
  - React + TypeScript dashboard 초기 화면
  - Python sample agent 초기 구현
- 진행 중:
  - API server 정책/감사/anchoring 흐름 고도화
  - sample agent와 API server v1 Guard API 연결
  - dashboard와 API server 연동 준비
- 다음 작업:
  - DB entity/repository 기반 영속화
  - Payment Request 목록 API
  - Approval 상태 전이 실제 반영
  - Audit Anchor 조회/검증 API
  - Dashboard API client 연결
  - Sample Agent demo scenario 정리

## 실행/검증

로컬 PostgreSQL 실행:

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose up -d postgres
```

API server 테스트:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-api-server
./gradlew test
```

Audit anchor 테스트:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-audit-anchor
nvm use
npm test
```

Dashboard 빌드:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-dashboard
npm run build
```

현재 구현:

- API 서버: Spring Boot 기반으로 부분 구현됨
- Sample Agent: Python 기반으로 초기 구현됨
- Smart Contract: Hardhat + Solidity 기반으로 부분 구현됨
- Dashboard: React + TypeScript 기반으로 초기 화면 구현됨
- DB: PostgreSQL + Flyway 초기 schema 구현됨

검증 기준:

- 정상 허용, 예산 초과 차단, 승인 필요 시나리오가 end-to-end로 동작해야 한다.
- 주요 이벤트 hash가 생성되고 AuditAnchor 컨트랙트에 기록되어야 한다.
- 대시보드 또는 API에서 txHash와 hash 검증 상태를 확인할 수 있어야 한다.

## 프로젝트 구조

현재 구조:

```text
agentpay-guard-api-server/
agentpay-guard-dashboard/
agentpay-guard-sample-agent/
agentpay-guard-audit-anchor/
docs/
  README.md
  overview/
  architecture/
  planning/
  policies/
  archive/
```

구현 프로젝트 구조:

```text
opensource-competition/
  agentpay-guard-api-server/       # Spring Boot API, policy engine, mock merchant
  agentpay-guard-dashboard/        # React + TypeScript dashboard
  agentpay-guard-sample-agent/     # Python sample agent
  agentpay-guard-audit-anchor/     # Solidity contract, Hardhat tests, deploy scripts
  docs/                            # 기획, 설계, 작업 계획
```

API server Java package 구조:

```text
com.agentpayguard.api
  controller/{approval,guard,merchant,payment}
  dto/{anchor,approval,audit,guard,merchant,payment,policy}
  service/{anchor,approval,audit,guard,merchant,payment,policy}
  config
```

API server는 layer-first 구조를 사용한다. 새 클래스는 `payment/PaymentService`처럼 도메인 먼저 두지 않고, `service/payment/PaymentService`처럼 역할별 패키지 아래에 둔다. DB 영속화가 추가되면 `entity/payment`, `repository/payment` 구조를 사용한다.

## 작업 원칙

- 기존 사용자 변경을 되돌리지 않는다.
- 문서의 구현 상태는 실제 코드 상태와 구분한다. 아직 없는 기능은 `planned` 또는 `예정`으로 표시한다.
- 실제 결제, 카드, 계좌, PG, 메인넷 자산 이동은 PoC 범위에 포함하지 않는다.
- 블록체인에는 원문 데이터나 개인정보를 올리지 않는다. eventHash만 기록한다.
- 1차 PoC 블록체인은 Hardhat local node를 기준으로 한다. 테스트넷 배포는 시간이 남을 때 optional로 다룬다.
- API key, private key, RPC secret, 지갑 mnemonic 등 민감 정보는 커밋하지 않는다.
- DB schema 변경은 직접 DB에서만 처리하지 않고 Flyway migration으로 남긴다.
- Docker PostgreSQL volume은 개인 로컬 상태로 보고 공유하지 않는다. 협업용 DB 상태는 migration과 seed SQL로 재현한다.
- Agent가 외부 API key를 직접 보유하는 구조를 기본 설계로 두지 않는다. Guard가 정책 검증 후 외부 리소스를 호출하는 proxy/gateway 구조를 우선한다.
- 정책 엔진은 1차 PoC에서 규칙 기반으로 구현한다. LLM 기반 판단은 고도화 항목으로 둔다.
- 구현할 때는 시연 가능한 end-to-end 흐름을 우선하고, 금융 서비스 수준 기능은 제외한다.
- 새로 추가하거나 의미 있게 수정하는 클래스와 함수에는 역할을 설명하는 주석을 남긴다.
- 주석은 "무엇을 하는지"보다 "왜 필요한지", "어떤 책임을 가지는지", "어떤 입력/출력 계약을 갖는지"를 설명한다.
- 단순 getter/setter, record 필드만 가진 DTO, 프레임워크 boilerplate처럼 코드만으로 의미가 분명한 경우에는 불필요한 반복 주석을 달지 않는다.
- 보안, 결제 흐름, 정책 판단, 감사 hash, 블록체인 연동, 외부 프로세스/RPC 호출, 상태 전이 로직은 반드시 클래스 또는 함수 단위 주석으로 의도를 남긴다.
- `agentpay-guard-dashboard`의 프론트엔드 화면이나 컴포넌트를 수정할 때는 `agentpay-guard-dashboard/DESIGN.md`를 먼저 읽고 디자인 규칙을 따른다.

## Review guidelines

- 모든 GitHub Pull Request 코드 리뷰는 한국어로 작성한다.
- 발견 사항은 심각도 순서로 정렬하고, 가능한 경우 파일 경로와 line을 함께 적는다.
- 보안, 민감정보, API 계약 불일치, 결제/감사/블록체인 연동 오류를 우선적으로 확인한다.
- 단순 스타일 의견보다 실제 버그, 회귀, 테스트 누락, 운영 위험을 우선한다.
- 이슈가 없으면 "주요 이슈 없음"이라고 한국어로 명확히 작성한다.

## POC 핵심 시나리오

1. 정상 허용:
   - 허용된 merchant와 예산 안의 요청을 `ALLOW` 처리한다.
   - mock 결제를 성공 처리하고 감사 hash를 기록한다.
2. 예산 초과 차단:
   - 총 예산을 넘는 요청을 `DENY` 처리한다.
   - 실제 외부 리소스 호출 또는 mock 결제는 실행하지 않는다.
3. 승인 필요:
   - 기준 금액을 초과한 요청을 `REQUIRE_APPROVAL` 처리한다.
   - 사용자 승인 후 mock 결제를 실행하고, 거절 시 차단한다.

## 문서 관리

- 문서 인덱스: `docs/README.md`
- 프로젝트 개요와 범위: `docs/overview/AgentPay_Guard_기획안.md`, `docs/overview/AgentPay_Guard_PoC_범위.md`
- 구성요소 쉬운 설명: `docs/overview/AgentPay_Guard_구성요소_쉬운설명.md`
- 작업 목록과 일정: `docs/planning/AgentPay_Guard_작업목록.md`
- 구현 아키텍처: `docs/architecture/AgentPay_Guard_시스템_아키텍처.md`
- 디렉토리별 개발 계획: `docs/planning/AgentPay_Guard_디렉토리별_개발계획.md`
- 디렉토리별 ToDo: `docs/planning/AgentPay_Guard_디렉토리별_TODO.md`
- DB 협업 정책: `docs/policies/AgentPay_Guard_DB_협업_정책.md`
- 고도화 항목: `docs/overview/AgentPay_Guard_고도화_방향.md`

문서를 수정할 때는 날짜를 `YYYY-MM-DD` 형식으로 쓰고, 구현된 내용과 계획된 내용을 구분한다.

## 확인 필요

- DB entity/repository 설계
- Approval 상태 전이 모델
- Audit Anchor 조회/검증 API 응답 형식
- Dashboard API client와 화면 라우팅 구조
