# AGENTS.md

## 프로젝트 개요

- 프로젝트명: AgentPay Guard
- 목적: AI Agent가 유료 API, 구독형 서비스, 크레딧 기반 서비스, 사용량 기반 외부 리소스를 사용하기 전에 사용자 intent, 예산, 허용 서비스, 위험 요소를 검증하고 감사 가능한 기록을 남기는 보안 게이트웨이 PoC를 구현한다.
- 핵심 방향: 실제 결제 시스템이 아니라 mock 결제와 감사 hash anchoring을 사용하는 PoC이다.
- 현재 상태: 문서 기획 단계. 코드 구조는 아직 생성 전이며, 구현 항목은 planned로 취급한다.
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
- 진행 중:
  - POC 시스템 아키텍처 구체화
  - 구현용 저장소 구조 정의
- 다음 작업:
  - 루트 `README.md` 작성
  - `agentpay-guard-api-server` Spring Boot 프로젝트 정리
  - `agentpay-guard-dashboard` React + TypeScript 프로젝트 생성
  - `agentpay-guard-sample-agent` Python Agent 프로젝트 생성
  - `agentpay-guard-audit-anchor` Hardhat 프로젝트 생성
  - `.gitignore`, `.env.example` 작성

## 실행/검증

현재 실행 가능한 애플리케이션 코드는 없다.

planned:

- API 서버: Spring Boot 기반으로 구현 예정
- Sample Agent: Python 기반으로 구현 예정
- Smart Contract: Hardhat + Solidity 기반으로 구현 예정
- Dashboard: React + TypeScript 기반으로 구현 예정
- DB: PostgreSQL 기반으로 구현 예정

검증 기준:

- 정상 허용, 예산 초과 차단, 승인 필요 시나리오가 end-to-end로 동작해야 한다.
- 주요 이벤트 hash가 생성되고 AuditAnchor 컨트랙트에 기록되어야 한다.
- 대시보드 또는 API에서 txHash와 hash 검증 상태를 확인할 수 있어야 한다.

## 프로젝트 구조

현재 구조:

```text
agentpay-guard-api-server/
docs/
  AgentPay_Guard_기획안.md
  AgentPay_Guard_PoC_범위.md
  AgentPay_Guard_작업목록.md
  AgentPay_Guard_고도화_방향.md
  온프레미스-ai-시스템제어-보안-poc.md
```

planned 구조:

```text
opensource-competition/
  agentpay-guard-api-server/       # Spring Boot API, policy engine, mock merchant
  agentpay-guard-dashboard/        # React + TypeScript dashboard
  agentpay-guard-sample-agent/     # Python sample agent
  agentpay-guard-audit-anchor/     # Solidity contract, Hardhat tests, deploy scripts
  docs/                            # 기획, 설계, 작업 계획
```

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

- 프로젝트 개요와 범위: `docs/AgentPay_Guard_기획안.md`, `docs/AgentPay_Guard_PoC_범위.md`
- 작업 목록과 일정: `docs/AgentPay_Guard_작업목록.md`
- 구현 아키텍처: `docs/AgentPay_Guard_시스템_아키텍처.md`
- 디렉토리별 개발 계획: `docs/AgentPay_Guard_디렉토리별_개발계획.md`
- DB 협업 정책: `docs/AgentPay_Guard_DB_협업_정책.md`
- 고도화 항목: `docs/AgentPay_Guard_고도화_방향.md`

문서를 수정할 때는 날짜를 `YYYY-MM-DD` 형식으로 쓰고, 구현된 내용과 계획된 내용을 구분한다.

## 확인 필요

- Spring Boot 버전, Java 버전
- web3j 사용 여부와 컨트랙트 연동 방식
- React 빌드 도구 선택
