# AgentPay Guard 초기 프로젝트 골격

작성일: 2026-06-28

이 문서는 1차 PoC 구현을 시작하기 위해 잡아둔 Spring Boot API Server와 Audit Anchor의 초기 코드 골격을 설명한다.

현재 골격은 완성 기능이 아니라, 앞으로 정책 판단, 결제 시뮬레이션, 감사 hash anchoring을 넣기 위한 기본 구조다.

## 전체 방향

초기 구현 흐름은 다음과 같다.

```text
Sample Agent 또는 Dashboard
        |
        v
Spring Boot API Server
  - mock merchant quote
  - payment request 생성
  - rule 기반 policy 판단
  - audit eventHash 생성
  - anchor client 호출 지점 확보
        |
        v
Audit Anchor
  - eventHash를 Solidity contract에 기록
```

## Spring Boot API Server

디렉토리:

```text
agentpay-guard-api-server/
```

현재 구현된 큰 틀:

```text
src/main/java/com/agentpayguard/api/
  payment/      # payment request 생성/조회
  policy/       # rule 기반 정책 판단
  merchant/     # mock merchant quote API
  audit/        # canonical JSON, eventHash 생성
  anchor/       # Audit Anchor 연동 인터페이스와 Noop 구현
  approval/     # approve/reject endpoint scaffold
```

### payment

주요 파일:

```text
payment/CreatePaymentRequestRequest.java
payment/PaymentRequestController.java
payment/PaymentService.java
payment/PaymentRequestResponse.java
payment/PaymentRequestStatus.java
```

역할:

- 결제 요청을 받는다.
- Policy Engine을 호출한다.
- 정책 판단 결과를 payment request 상태로 변환한다.
- 감사 이벤트 생성을 호출한다.
- 현재는 PoC scaffold 단계라 메모리 map에 요청을 저장한다.

현재 endpoint:

```text
POST /api/payment-requests
GET  /api/payment-requests/{id}
```

planned:

- JPA entity와 repository 연결
- `payment_requests`, `policy_decisions`, `audit_events`, `audit_anchors` 테이블 저장
- 결제 시뮬레이터 연결
- 상태 전이 규칙 정리

### policy

주요 파일:

```text
policy/PolicyEngine.java
policy/RuleBasedPolicyEngine.java
policy/PolicyDecision.java
policy/PolicyDecisionResult.java
```

역할:

- 결제 요청을 받아 `ALLOW`, `REQUIRE_APPROVAL`, `DENY` 중 하나로 판단한다.
- 현재는 하드코딩된 PoC rule을 사용한다.

현재 rule:

```text
blocked-merchant, unknown-risky-api -> DENY
100.00 초과 -> DENY
50.00 초과 -> REQUIRE_APPROVAL
그 외 -> ALLOW
```

planned:

- 사용자 intent별 예산 rule 적용
- merchant allow/block list 적용
- 누적 사용량 기반 판단
- 정책 버전 관리

### merchant

주요 파일:

```text
merchant/MockMerchantController.java
merchant/MockMerchantService.java
merchant/QuoteRequest.java
merchant/QuoteResponse.java
```

역할:

- 1차 PoC용 mock merchant quote를 제공한다.
- 실제 외부 결제나 실제 merchant API를 호출하지 않는다.

현재 endpoint:

```text
POST /api/mock-merchant/quote
```

planned:

- 시나리오별 quote 응답 고정
- quote hash 생성
- sample agent와 end-to-end 연결

### audit

주요 파일:

```text
audit/AuditEventService.java
audit/EventHashService.java
audit/AuditEventResult.java
```

역할:

- 결제 요청과 정책 판단 결과를 canonical JSON으로 만든다.
- canonical JSON을 SHA-256으로 hash 처리해 `eventHash`를 만든다.

현재 hash 형식:

```text
sha256:<hex>
```

planned:

- canonical JSON envelope 최종 규칙 확정
- DB 저장
- audit event type 표준화
- hash 재검증 API 추가

### anchor

주요 파일:

```text
anchor/AuditAnchorClient.java
anchor/NoopAuditAnchorClient.java
anchor/AnchorResult.java
```

역할:

- Spring Boot API Server에서 Audit Anchor를 호출할 자리를 미리 잡아둔다.
- 현재는 실제 블록체인 호출 없이 `PENDING`을 반환하는 Noop 구현이다.

planned:

- Hardhat local node 또는 RPC endpoint 연결
- eventHash를 `bytes32`로 변환
- 컨트랙트 호출 txHash 저장
- 실패 시 재시도 정책 추가

### approval

주요 파일:

```text
approval/ApprovalController.java
approval/ApprovalService.java
approval/ApprovalResponse.java
```

역할:

- `REQUIRE_APPROVAL` 상태의 요청을 승인 또는 거절하는 endpoint 자리를 만든다.

현재 endpoint:

```text
POST /api/payment-requests/{paymentRequestId}/approve
POST /api/payment-requests/{paymentRequestId}/reject
```

planned:

- 실제 payment request 상태 조회
- 승인자 user 검증
- 승인 결과 DB 저장
- 승인 후 mock payment 실행

## Audit Anchor

디렉토리:

```text
agentpay-guard-audit-anchor/
```

현재 구현된 큰 틀:

```text
contracts/AuditAnchor.sol
scripts/deploy.ts
scripts/anchor-event.ts
test/AuditAnchor.test.ts
hardhat.config.ts
package.json
```

### contracts

주요 파일:

```text
contracts/AuditAnchor.sol
```

역할:

- `bytes32 eventHash`를 블록체인에 기록한다.
- 이미 기록된 hash는 중복 기록하지 못하게 막는다.
- hash가 기록되었는지 조회할 수 있다.

현재 contract 기능:

```text
anchor(bytes32 eventHash)
isAnchored(bytes32 eventHash)
anchoredAt(bytes32 eventHash)
```

주의:

- 원문 데이터는 저장하지 않는다.
- 개인정보나 결제 상세 정보도 저장하지 않는다.
- 실제 결제 기능은 없다.

### scripts

주요 파일:

```text
scripts/deploy.ts
scripts/anchor-event.ts
```

역할:

- `deploy.ts`: `AuditAnchor.sol` 컨트랙트를 Hardhat local node에 배포한다.
- `anchor-event.ts`: 배포된 컨트랙트에 `EVENT_HASH`를 기록한다.

스크립트가 TypeScript인 이유:

- Hardhat이 Node.js 기반 도구다.
- 배포와 테스트 스크립트는 JavaScript 또는 TypeScript로 작성하는 것이 일반적이다.
- TypeScript를 쓰면 ethers API 사용 시 타입 도움을 받을 수 있다.

### test

주요 파일:

```text
test/AuditAnchor.test.ts
```

역할:

- eventHash 기록이 되는지 검증한다.
- 같은 eventHash를 두 번 기록할 수 없는지 검증한다.

## 현재 검증 상태

검증됨:

```text
agentpay-guard-api-server ./gradlew compileJava
```

결과:

```text
BUILD SUCCESSFUL
```

아직 검증 전:

```text
agentpay-guard-audit-anchor npm install
agentpay-guard-audit-anchor npm test
```

Hardhat 프로젝트는 현재 scaffold만 생성된 상태다. 의존성을 설치한 뒤 테스트를 실행해야 한다.

## 다음 구현 순서

1. Spring Boot payment scaffold를 JPA entity/repository에 연결한다.
2. Payment request 생성 시 DB에 요청, policy decision, audit event를 저장한다.
3. Approval flow에서 상태 전이를 실제로 처리한다.
4. Audit Anchor에서 `npm install`, `npm test`를 실행해 컨트랙트 scaffold를 검증한다.
5. Spring Boot API Server에서 Audit Anchor 호출 방식을 결정한다.
6. Sample Agent CLI에서 mock merchant quote와 payment request API를 호출한다.

## 현재 한계

- Spring Boot payment request는 아직 메모리 map 기반이다.
- Audit event와 policy decision은 아직 DB에 저장되지 않는다.
- Audit Anchor는 Spring Boot와 아직 연결되지 않았다.
- 실제 LLM API, 실제 결제, 실제 외부 merchant API는 포함하지 않는다.
