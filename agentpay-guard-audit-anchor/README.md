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

이 프로젝트는 Hardhat v2 기반이다. Hardhat은 최신 Node.js 버전을 바로 지원하지 않을 수 있으므로, 로컬 테스트는 Node.js 22 LTS 기준으로 실행한다.

이 디렉토리에는 `.nvmrc`가 있으며 값은 `22`이다.

### Node 버전 설정

`nvm`을 이미 사용할 수 있으면 아래처럼 실행한다.

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-audit-anchor
nvm install
nvm use
node -v
```

`node -v`가 `v22.x.x`로 나오면 정상이다.

Homebrew로 `nvm`을 설치했지만 현재 터미널에서 `nvm: command not found`가 나오면, 현재 터미널 세션에서만 아래 명령으로 `nvm`을 로드한다.

```bash
mkdir -p ~/.nvm
export NVM_DIR="$HOME/.nvm"
. /opt/homebrew/opt/nvm/nvm.sh
```

그 다음 다시 실행한다.

```bash
nvm install
nvm use
node -v
```

위 방식은 현재 터미널 세션에만 적용된다. 다른 터미널이나 다른 프로젝트의 기본 Node.js 버전은 바꾸지 않는다.

### 의존성 설치와 테스트

```bash
npm install
npm run build
npm test
```

`npm test` 실행 시 Node.js 25 같은 미지원 버전을 사용하면 Hardhat 경고가 출력될 수 있다. 이 경우 `.nvmrc` 기준으로 `nvm use`를 실행한 뒤 다시 테스트한다.

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
