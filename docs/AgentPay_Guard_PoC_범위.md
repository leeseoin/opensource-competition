# AgentPay Guard PoC 범위

작성일: 2026-06-22  
PoC 완성 목표일: 2026-08-20  
최종 제출 목표일: 2026-08-27  
상태: planned

## PoC 목표

AgentPay Guard의 PoC 목표는 AI Agent가 유료 리소스를 사용하려고 할 때, 사용자 의도와 예산 정책에 맞는 요청만 허용하고 그 판단 과정을 감사 가능하게 기록하는 흐름을 end-to-end로 보여주는 것이다.

이번 PoC는 실제 결제 시스템이 아니다. 실제 카드, 계좌, PG, 메인넷 결제는 구현하지 않는다. 결제는 mock으로 처리하고, 블록체인은 결제 판단 로그의 해시를 기록하는 감사 용도로만 사용한다.

## 한 줄 정의

```text
AI Agent가 유료 API를 사용하기 전에 사용자 intent, 예산, 허용 서비스, 위험 요소를 검증하고,
허용/승인/차단 결과를 감사 로그와 블록체인 해시로 남기는 보안 게이트웨이 PoC
```

## PoC에서 보여줄 핵심 질문

이 PoC는 다음 질문에 답해야 한다.

```text
AI Agent가 유료 리소스를 사용하려고 할 때,
그 요청이 사용자 의도와 예산 안에 있는지 자동으로 검증할 수 있는가?

위험하거나 예산을 초과하는 요청을 차단할 수 있는가?

승인이 필요한 요청을 사용자에게 넘기고 승인 후 처리할 수 있는가?

이 판단 과정과 결과를 나중에 조작 없이 검증할 수 있는가?
```

## PoC 핵심 시나리오

### 시나리오 1: 정상 허용

사용자가 다음 intent를 등록한다.

```text
목적: 날씨 데이터 수집
총 예산: 1달러
단건 한도: 0.3달러
허용 서비스: weather-api.local
```

Agent가 다음 요청을 만든다.

```text
서비스: weather-api.local
리소스: /premium/weather/seoul
금액: 0.1달러
사유: 서울 날씨 데이터 조회
```

결과:

```text
Policy Engine: ALLOW
Payment Simulator: mock 결제 성공
Audit: eventHash 생성 및 블록체인 기록
Dashboard: 허용 결과, txHash, hash 검증 상태 표시
```

### 시나리오 2: 예산 초과 차단

사용자 intent는 총 예산 1달러까지 허용한다.

현재 사용액:

```text
0.95달러
```

Agent가 다음 요청을 만든다.

```text
금액: 0.1달러
```

결과:

```text
Policy Engine: DENY
사유: 총 예산 초과
Payment Simulator: 실행하지 않음
Audit: 차단 이벤트 hash 기록
Dashboard: 차단 사유와 txHash 표시
```

### 시나리오 3: 사용자 승인 필요

사용자 intent는 다음 조건을 가진다.

```text
0.5달러 초과 요청은 사용자 승인 필요
```

Agent가 다음 요청을 만든다.

```text
금액: 0.6달러
```

결과:

```text
Policy Engine: REQUIRE_APPROVAL
Dashboard: 승인/거절 버튼 표시
사용자 승인 시: mock 결제 성공
사용자 거절 시: 결제 차단
Audit: 승인 또는 거절 결과 hash 기록
```

## PoC 구성 요소

### 1. Spring Boot API 서버

역할:

- 사용자 intent 관리
- Agent 등록 관리
- 유료 리소스 사용 요청 관리
- 정책 엔진 실행
- 승인/거절 처리
- mock 결제 실행
- 감사 이벤트 저장
- 블록체인 hash 기록 요청
- 대시보드 제공

### 2. Policy Engine

1차 PoC에서는 규칙 기반으로 구현한다.

검사 항목:

- intent가 활성 상태인지
- intent가 만료되지 않았는지
- merchant가 허용 목록에 있는지
- merchant가 차단 목록에 없는지
- category가 허용되었는지
- 단건 금액이 한도 이하인지
- 누적 사용액이 총 예산 이하인지
- 승인 필요 금액을 초과했는지
- reason에 의심 문구가 포함되어 있는지

결과:

- `ALLOW`
- `REQUIRE_APPROVAL`
- `DENY`

### 3. Mock Merchant API

실제 외부 유료 API를 흉내 내는 서버 기능이다.

동작:

- 결제 정보 없이 유료 리소스를 요청하면 `402 Payment Required` 스타일 응답을 반환한다.
- 응답에는 quote, merchant, resource, amount, currency를 포함한다.
- mock 결제 완료 후 다시 요청하면 resource 데이터를 반환한다.

### 4. Payment Simulator

실제 결제 대신 결제 성공/실패를 흉내 내는 모듈이다.

역할:

- mock 결제 성공 처리
- mock 결제 실패 처리
- simulated transaction id 생성
- payment result 저장
- payment request 상태 변경

### 5. Audit / Hash 모듈

각 주요 이벤트의 원문은 DB에 저장하고, 원문을 canonical JSON으로 정규화한 뒤 hash를 생성한다.

hash 대상 이벤트:

- intent
- payment request
- policy decision
- approval
- payment result

### 6. AuditAnchor 스마트컨트랙트

블록체인에는 원문 데이터를 올리지 않는다. eventHash만 기록한다.

최소 함수:

```solidity
anchorEvent(eventId, eventType, eventHash)
getEventHash(eventId)
```

역할:

- eventHash 기록
- txHash 생성
- 나중에 DB hash와 온체인 hash 비교 가능하게 함

### 7. Python Sample Agent

시연용 Agent이다.

역할:

- mock merchant의 유료 리소스 호출
- `402 Payment Required` 스타일 응답 수신
- quote를 기반으로 AgentPay Guard에 payment request 생성
- 정책 결과 확인
- mock 결제 후 리소스 재요청

### 8. Dashboard

시연용 화면이다.

필수 화면:

- Intent 목록/상세
- Payment Request 목록/상세
- 정책 판단 결과
- 승인/거절 버튼
- mock 결제 결과
- AuditAnchor 기록
- txHash
- hash 검증 상태

## PoC에서 구현할 데이터

최소 데이터 모델:

- `users`
- `agents`
- `payment_intents`
- `payment_requests`
- `policy_decisions`
- `approvals`
- `payment_results`
- `audit_anchors`

## PoC에서 구현할 API

최소 API:

- `POST /api/intents`
- `GET /api/intents`
- `GET /api/intents/{intentId}`
- `POST /api/agents`
- `GET /api/agents`
- `POST /api/payment-requests`
- `GET /api/payment-requests`
- `GET /api/payment-requests/{paymentRequestId}`
- `POST /api/payment-requests/{paymentRequestId}/evaluate`
- `POST /api/payment-requests/{paymentRequestId}/approve`
- `POST /api/payment-requests/{paymentRequestId}/reject`
- `POST /api/payment-requests/{paymentRequestId}/pay`
- `POST /api/audit-anchors/{eventType}/{eventId}`
- `GET /api/audit-anchors/{anchorId}/verify`

## PoC에서 제외할 것

### 실제 결제

- 카드 결제
- 계좌 이체
- PG 연동
- 실제 USDC 결제
- 실제 x402 결제 처리
- 메인넷 결제

### 금융 서비스 수준 기능

- KYC
- AML
- 실사용 지갑 관리
- 수수료 정산
- 환불 정산
- 회계 시스템 연동

### 과도한 Agent 기능

- 완전 자율 Agent
- 복잡한 쇼핑 Agent
- 다중 Agent 협상
- Agent-to-Agent 실결제
- 실서비스 API 자동 구매

### 과도한 블록체인 기능

- 자체 토큰 발행
- 자체 결제 컨트랙트
- escrow 결제
- 멀티체인 지원
- 온체인 개인정보 저장

## PoC 완료 기준

8월 20일까지 아래가 동작해야 한다.

- 사용자가 payment intent를 등록할 수 있다.
- Python Agent가 mock merchant에 유료 리소스를 요청할 수 있다.
- mock merchant가 `402 Payment Required` 스타일 응답을 반환한다.
- Agent가 quote를 기반으로 payment request를 생성할 수 있다.
- 정책 엔진이 `ALLOW`, `REQUIRE_APPROVAL`, `DENY`를 판단할 수 있다.
- 승인 필요 요청을 대시보드에서 승인/거절할 수 있다.
- mock 결제 성공/실패 결과가 저장된다.
- 주요 이벤트 hash가 생성된다.
- AuditAnchor 컨트랙트에 eventHash가 기록된다.
- txHash와 hash 검증 상태를 대시보드에서 확인할 수 있다.
- 정상 허용, 예산 초과 차단, 승인 필요 시나리오가 모두 시연 가능하다.

## 제출 전 안정화 기준

8월 21일부터 8월 27일까지는 새 기능을 추가하지 않는다.

이 기간에는 다음만 수행한다.

- 버그 수정
- README 보강
- API 사용 예시 정리
- 결과보고서 작성
- 3분 시연 영상 촬영
- 라이선스 점검
- secret 포함 여부 점검
- 제출 파일 정리

## 대회 제출용 설명

AgentPay Guard는 AI Agent가 유료 API나 외부 서비스를 사용할 때, 기존 사용량 기반 과금이나 단순 limit만으로는 확인하기 어려운 사용자 의도, 예산, 허용 서비스, 위험 요소를 사전에 검증한다. 또한 허용·승인·차단 판단과 mock 결제 결과를 감사 로그로 저장하고, 핵심 로그 hash를 블록체인에 기록해 사후 조작 여부를 검증할 수 있도록 한다.
