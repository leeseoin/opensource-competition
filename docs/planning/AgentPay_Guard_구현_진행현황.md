# AgentPay Guard 구현 진행 현황

작성일: 2026-07-05  
상태: active

## 목적

이 문서는 AgentPay Guard 구현을 진행하면서 현재 동작하는 기능, 검증한 명령, 다음 연결 작업을 계속 갱신하기 위한 진행 현황 문서이다.

상세 ToDo는 `AgentPay_Guard_디렉토리별_TODO.md`에 남기고, 이 문서에는 실제 구현/검증 상태를 중심으로 기록한다.

## 갱신 규칙

- 기능을 구현했으면 `구현됨` 또는 `부분 구현됨`으로 구분한다.
- 아직 코드가 없거나 연동되지 않은 내용은 `planned`로 표시한다.
- 테스트나 실행을 직접 확인한 경우 실행 명령과 결과를 함께 적는다.
- 날짜는 `YYYY-MM-DD` 형식으로 쓴다.
- 실제 결제, 카드, 계좌, PG, 메인넷 자산 이동은 PoC 범위에 포함하지 않는다.
- 블록체인에는 원문 데이터나 개인정보를 올리지 않고 `eventHash`만 기록한다.
- 기능을 추가할 때 클래스와 함수의 역할 주석을 함께 갱신한다.
- 특히 정책 판단, 감사 hash, 블록체인 연동, 상태 전이, 외부 API/RPC 호출은 구현 의도를 주석으로 남긴다.
- 단순 DTO나 반복적인 boilerplate에는 의미 없는 설명 주석을 강제하지 않는다.

## 현재 구현 요약

### agentpay-guard-api-server

상태: 부분 구현됨

구현됨:

- Spring Boot API server 프로젝트
- PostgreSQL datasource 설정
- Flyway 초기 schema migration
- Mock Merchant quote API
- Payment Request 생성/조회 API
- 규칙 기반 Policy Engine 1차 구현
- Approval approve/reject scaffold
- Audit event canonical JSON 생성
- SHA-256 eventHash 생성
- Noop audit anchor client
- web3j 기반 AuditAnchor 컨트랙트 호출 구현
- Payment Request 생성 시 `eventHash` 온체인 기록
- Audit anchor 결과의 `chainId`, `contractAddress`, `txHash` 응답 포함
- Jackson `ObjectMapper` Bean 명시 등록
- Swagger/OpenAPI 의존성

검증됨:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-api-server
./gradlew compileJava
```

결과:

```text
BUILD SUCCESSFUL
```

기본 Noop anchoring 기동 검증:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-api-server
./gradlew bootRun
```

결과:

```text
Tomcat started on port 8080
Started AgentpayGuardApiServerApplication
```

web3j anchoring end-to-end 검증:

사전 조건:

```text
PostgreSQL container running
Hardhat local node running at http://127.0.0.1:8545
AuditAnchor deployed to 0x5FbDB2315678afecb367f032d93F642f64180aa3
agentpay.audit-anchor.enabled=true
```

Swagger에서 `POST /api/payment-requests` 요청:

```json
{
  "intentId": "11111111-1111-1111-1111-111111111111",
  "agentId": "22222222-2222-2222-2222-222222222222",
  "quoteId": "quote-test-001",
  "merchant": "openai",
  "resource": "gpt-api",
  "category": "llm",
  "amount": 10.00,
  "currency": "USD",
  "reason": "web3j anchor test"
}
```

확인된 응답 요약:

```json
{
  "status": "ALLOWED",
  "auditEvent": {
    "eventHash": "sha256:32805e15caab8a43ec58d732e41bff68f56814f66589c0e21b3186513c382ea7",
    "anchorStatus": "ANCHORED",
    "anchor": {
      "verifyStatus": "ANCHORED",
      "chainId": "31337",
      "contractAddress": "0x5fbdb2315678afecb367f032d93f642f64180aa3",
      "txHash": "0x794c648eae1b85cdd3f25f052221bfba9e1dad434eb35223088604d7b14981dc"
    }
  }
}
```

planned:

- DB entity/repository 기반 영속화
- Payment Request 목록 API
- Intent/Agent API
- Approval 상태 전이 실제 반영
- Mock payment simulator
- Audit event DB 저장 고도화
- 온체인 hash 조회/검증 API
- anchoring 실패 시 API 응답 정책 정리

### agentpay-guard-audit-anchor

상태: 부분 구현됨

구현됨:

- Hardhat + Solidity 프로젝트
- `AuditAnchor.sol` 컨트랙트
- `eventHash` 기록
- 중복 `eventHash` 차단
- 기록 여부 조회
- 컨트랙트 단위 테스트
- 로컬 Hardhat node 실행
- 로컬 배포 스크립트
- 로컬 `eventHash` 기록 스크립트
- Node.js 22 LTS 기준 `.nvmrc`
- Spring Boot API server에서 web3j로 호출 가능한 `anchor(bytes32)` 함수 검증

검증됨:

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-audit-anchor
nvm use
npm test
```

결과:

```text
AuditAnchor
  ✔ anchors an event hash
  ✔ rejects duplicate event hashes

2 passing
```

로컬 체인 배포/기록 검증:

```bash
npm run node
```

다른 터미널:

```bash
nvm use
npm run deploy:local
```

확인된 배포 주소 예시:

```text
0x5FbDB2315678afecb367f032d93F642f64180aa3
```

`eventHash` 기록:

```bash
AUDIT_ANCHOR_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3 \
EVENT_HASH=0x1111111111111111111111111111111111111111111111111111111111111111 \
npm run anchor:local
```

확인된 txHash 예시:

```text
0x4cc0c1079d54bbcc44f294a39f77df6c636a6e177dd7e819544168361ce84942
```

planned:

- API server 연동용 contract address 관리 방식 정리
- txHash DB 저장
- 온체인 기록 조회/검증 API
- 테스트넷 배포는 optional

### agentpay-guard-dashboard

상태: 초기 scaffold

구현됨:

- React + TypeScript + Vite 프로젝트
- 기본 Vite 화면
- `package.json`, `package-lock.json`

planned:

- Vite 기본 화면 제거
- API client
- Intent/Payment Request 화면
- Approval 조작 화면
- Audit Anchor txHash/hash verify 표시

### agentpay-guard-sample-agent

상태: planned

구현됨:

- README placeholder

planned:

- Python CLI 프로젝트 구조
- Mock Merchant quote 요청
- Guard Payment Request 생성
- 정책 결과별 처리
- demo scenario 스크립트

## 현재 핵심 흐름 이해

현재는 API server와 AuditAnchor가 각각 부분적으로 동작한다.

```text
API server
  Payment Request 생성
  Policy Engine 판단
  Audit eventHash 생성
  web3j로 AuditAnchor 컨트랙트에 eventHash 기록
  txHash를 API 응답에 포함

AuditAnchor
  Hardhat local node 실행
  AuditAnchor 컨트랙트 배포
  eventHash 기록
  txHash 확인
```

현재 연결 흐름은 Swagger 요청 기준으로 end-to-end 검증됐다. 다음 큰 작업은 생성된 audit event와 txHash를 DB에 저장하고, API에서 온체인 기록을 조회/검증하는 것이다.

현재 검증된 요청 흐름:

```text
POST /api/payment-requests
  -> RuleBasedPolicyEngine: ALLOW
  -> AuditEventService: canonicalJson 생성
  -> EventHashService: sha256 eventHash 생성
  -> Web3jAuditAnchorClient: anchor(bytes32) tx 전송
  -> AuditAnchor.sol: eventHash 기록
  -> API response: anchorStatus ANCHORED, txHash 반환
```

## 다음 작업 후보

우선순위 P0:

- audit event와 anchor 결과 DB 저장
- Payment Request 상세 조회 시 audit event와 txHash 조회
- 온체인 `isAnchored(bytes32)` 조회 API 구현
- DB eventHash와 온체인 anchoring 상태 비교
- anchoring 실패 시 Payment Request 생성 정책 결정
  - 요청 자체 실패
  - 요청은 성공시키고 anchorStatus FAILED 기록
  - 재시도 큐로 보류

우선순위 P1:

- API server 테스트 보강
- Audit event 저장 구조 정리
- README 빠른 시작 최신화
- 전체 demo 실행 순서 문서화
- `application.yaml`의 local Hardhat 테스트 키 처리 방식 정리

## 로컬 설정 주의사항

현재 web3j 연동 검증에는 Hardhat local node가 출력하는 공개 테스트 계정 #0 private key를 사용했다.

```text
Account #0 private key:
Hardhat local node 실행 로그의 Account #0 private key
```

이 키는 Hardhat local node 전용 공개 테스트 키다. 실제 테스트넷, 메인넷, 운영 환경에서는 절대 사용하지 않는다.

YAML에서 `0x...` 값은 반드시 문자열로 감싼다.

```yaml
contract-address: "0x..."
private-key: "0x..."
```

따옴표 없이 쓰면 YAML 파서가 hex 값을 문자열이 아닌 숫자성 값으로 해석할 수 있고, web3j 서명 단계에서 private key 오류가 발생할 수 있다.

## 변경 로그

### 2026-07-06

- API server에 web3j 의존성 추가
- `Web3jAuditAnchorClient` 구현
- `AuditAnchorClient` / `AnchorResult` / audit service 계층 역할 주석 추가
- `JacksonConfig` 추가로 `ObjectMapper` Bean 누락 문제 해결
- `NoopAuditAnchorClient` 조건을 `agentpay.audit-anchor.enabled=false` 기준으로 정리
- Swagger `POST /api/payment-requests`에서 web3j anchoring end-to-end 성공 확인
- 응답에서 `anchorStatus=ANCHORED`, `chainId=31337`, `txHash` 반환 확인
- YAML `0x...` 설정값은 문자열 quoting이 필요함을 확인

### 2026-07-05

- AuditAnchor Hardhat 테스트 통과 확인
- Hardhat local node 실행 확인
- AuditAnchor local deploy 확인
- `eventHash` 기록 txHash 확인
- API server `compileJava` 성공 확인
- Hardhat Node.js 버전 기준을 Node.js 22 LTS로 정리
- 새 클래스/함수 추가 시 역할 주석을 남기는 코드 작성 규칙 추가
