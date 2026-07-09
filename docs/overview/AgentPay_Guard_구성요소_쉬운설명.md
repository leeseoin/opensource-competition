# AgentPay Guard 구성요소 쉬운 설명

작성일: 2026-06-28

이 문서는 AgentPay Guard의 주요 디렉토리가 각각 무엇을 담당하는지 쉽게 설명한다. 현재 구현된 내용과 planned 항목을 구분한다.

## 한 줄 요약

AgentPay Guard는 AI Agent가 돈이 드는 외부 리소스를 쓰기 전에 Spring Boot API Server가 정책을 검사하고, Python Sample Agent가 그 흐름을 시연하며, Audit Anchor가 감사 기록의 hash를 블록체인에 남기는 PoC이다.

## Spring Boot API Server

디렉토리:

```text
agentpay-guard-api-server/
```

역할:

Spring Boot API Server는 AgentPay Guard의 중심 서버다. 단순 CRUD 서버가 아니라, Agent의 유료 리소스 사용 요청을 받아서 "허용할지, 사용자 승인이 필요한지, 차단할지" 판단하는 정책 게이트웨이다.

담당하는 일:

- Agent, 사용자 intent, 결제 요청 정보를 API로 받는다.
- PostgreSQL에 요청, 정책 판단, 승인, 결제 결과, 감사 이벤트를 저장한다.
- Policy Engine으로 예산, 허용 merchant, 위험도, 승인 필요 조건을 검사한다.
- 1차 PoC에서는 Mock Merchant를 내부 모듈로 포함한다.
- 실제 결제가 아니라 Payment Simulator로 결제 성공/실패 흐름을 만든다.
- 감사 이벤트를 canonical JSON으로 정리하고 eventHash를 만든다.
- planned: Audit Anchor 컨트랙트에 eventHash를 기록하고 txHash를 저장한다.
- Dashboard가 볼 수 있는 API를 제공한다.

쉽게 말하면:

```text
"AI Agent가 이 돈 드는 작업을 해도 되는지 검사하고,
결과와 이유를 DB와 감사 로그에 남기는 서버"
```

## Python Sample Agent

디렉토리:

```text
agentpay-guard-sample-agent/
```

역할:

Python Sample Agent는 실제 AI Agent를 흉내 내는 시연용 클라이언트다. 운영 서버라기보다는 "Agent가 유료 API를 쓰려고 할 때 어떤 흐름으로 Guard를 거치는지" 보여주는 샘플 프로그램이다.

담당하는 일:

- 사용자가 입력한 목표를 바탕으로 유료 리소스 사용이 필요하다고 가정한다.
- Mock Merchant에 quote를 요청하거나, API Server가 제공하는 mock merchant endpoint를 호출한다.
- 가격, merchant, 사용 목적을 포함해 API Server에 payment request를 만든다.
- API Server의 정책 판단 결과를 받는다.
- `ALLOW`면 작업을 계속 진행한다.
- `REQUIRE_APPROVAL`이면 승인 대기 상태를 보여준다.
- `DENY`면 외부 리소스를 호출하지 않고 중단한다.

쉽게 말하면:

```text
"돈 드는 API를 쓰려는 AI Agent 역할을 연기하는 시연용 프로그램"
```

## Audit Anchor

디렉토리:

```text
agentpay-guard-audit-anchor/
```

역할:

Audit Anchor는 Hardhat과 Solidity로 만드는 감사 기록용 블록체인 프로젝트다. 결제 기능을 담당하지 않고, 돈을 옮기지도 않는다. API Server가 만든 감사 이벤트의 `eventHash`만 블록체인에 기록한다.

담당하는 일:

- `eventHash`를 컨트랙트에 저장한다.
- 같은 hash가 언제 기록됐는지 확인할 수 있게 한다.
- API Server가 받은 transaction hash를 DB에 저장할 수 있게 한다.
- 나중에 DB의 감사 이벤트를 다시 hash로 만들고, 블록체인에 기록된 hash와 비교해 변조 여부를 확인한다.

쉽게 말하면:

```text
"DB 감사 기록이 나중에 바뀌지 않았다는 걸 증명하기 위해 hash만 박아두는 장부"
```

## 전체 관계

```text
Python Sample Agent
  "이 유료 리소스를 쓰고 싶어요"
        |
        v
Spring Boot API Server
  "정책을 확인할게요"
  "허용 / 승인 필요 / 차단"
  "요청과 결과를 DB에 기록할게요"
        |
        v
Audit Anchor
  "이 감사 기록의 hash를 블록체인에 남길게요"
```

## 각 컴포넌트가 하지 않는 일

Spring Boot API Server는 실제 카드 결제, 계좌 이체, PG 연동을 하지 않는다. PoC에서는 mock 결제만 처리한다.

Python Sample Agent는 실제 운영용 AI Agent가 아니다. Guard 연동 흐름을 보여주는 샘플이다.

Audit Anchor는 원문 데이터, 개인정보, 결제 상세 정보를 저장하지 않는다. 블록체인에는 `eventHash`만 저장한다.

## PoC에서 필요한 이유

- Spring Boot API Server가 없으면 정책 판단과 감사 기록의 중심이 없다.
- Python Sample Agent가 없으면 Agent가 Guard를 거치는 end-to-end 흐름을 시연하기 어렵다.
- Audit Anchor가 없으면 "감사 기록이 나중에 바뀌지 않았다"는 검증 포인트가 약해진다.

1차 PoC의 목표는 이 세 요소를 연결해서 정상 허용, 예산 초과 차단, 승인 필요 시나리오를 보여주는 것이다.
