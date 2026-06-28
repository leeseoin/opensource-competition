# agentpay-guard-audit-anchor

Hardhat + Solidity 기반 AgentPay Guard 감사 hash anchoring 프로젝트이다.

## 역할

Spring Boot API Server가 만든 감사 이벤트의 `eventHash`를 로컬 Hardhat 체인에 기록하는 PoC 컨트랙트 프로젝트이다.

블록체인에는 원문 데이터, 개인정보, 결제 상세 정보가 아니라 `bytes32 eventHash`만 저장한다.

## 구조

```text
contracts/AuditAnchor.sol      # eventHash 저장 컨트랙트
scripts/deploy.ts              # 로컬 체인 배포 스크립트
scripts/anchor-event.ts        # eventHash 기록 스크립트
test/AuditAnchor.test.ts       # 컨트랙트 테스트
hardhat.config.ts              # Hardhat 설정
```

## planned 실행 흐름

```bash
npm install
npm run build
npm test
```

로컬 노드:

```bash
npm run node
```

배포:

```bash
npm run deploy:local
```

eventHash 기록:

```bash
AUDIT_ANCHOR_ADDRESS=0x... EVENT_HASH=0x... npm run anchor:local
```

## 현재 범위

- `AuditAnchor.sol` 컨트랙트 골격
- eventHash 기록
- 중복 eventHash 차단
- 기록 여부 조회
- Hardhat local node 기준 설정

테스트넷 배포와 Spring Boot 연동은 이후 단계에서 구현한다.
