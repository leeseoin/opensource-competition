# AgentPay Guard 작업 목록

작성일: 2026-06-22  
업데이트: 2026-06-24  
PoC 완성 목표일: 2026-08-20  
최종 제출 목표일: 2026-08-27  
상태: planned

## 기준

8월 27일 제출을 위해 실제 PoC 구축은 8월 20일까지 끝내는 것을 목표로 한다. 8월 21일부터 8월 27일까지는 새 기능을 추가하지 않고 안정화, 문서, 영상, 제출물 정리에 사용한다.

이번 PoC의 핵심은 다음 3개 시나리오가 동작하는 것이다.

1. 정상 허용: 허용된 merchant, 예산 안의 요청을 허용한다.
2. 예산 초과 차단: 총 예산을 넘는 요청을 차단한다.
3. 승인 필요: 일정 금액을 넘는 요청은 사용자 승인 후 처리한다.

## 확정된 개발 방향

- DB는 PostgreSQL을 사용한다.
- Dashboard는 React + TypeScript로 구현한다.
- Mock Merchant는 1차 PoC에서 Spring Boot API server 내부 모듈로 구현한다.
- Blockchain은 Hardhat local node를 기준으로 구현한다.
- 테스트넷 배포는 필수 범위가 아니라 optional로 둔다.
- eventHash는 정렬된 canonical JSON envelope를 SHA-256으로 해시한다.
- 구현 프로젝트는 `opensource-competition` 저장소 루트 하위 디렉토리로 둔다.

```text
agentpay-guard-api-server
agentpay-guard-dashboard
agentpay-guard-sample-agent
agentpay-guard-audit-anchor
```

## 개발 리스트

### 1. 저장소/개발 환경

- 루트 `README.md` 작성
- `.gitignore` 작성
- `.env.example` 작성
- `docker-compose.yml` 작성
- PostgreSQL 로컬 실행 설정
- API server 환경 변수 정리
- dashboard 환경 변수 정리
- contracts 환경 변수 정리
- 전체 실행 순서 문서화

### 2. Backend API Server

- `agentpay-guard-api-server` Spring Boot 프로젝트 정리
- Java/Spring Boot 버전 확정
- PostgreSQL 연결
- JPA 설정
- 공통 예외 응답 구조 작성
- 공통 validation 구조 작성
- seed data 또는 demo data 생성 방식 작성
- API 요청/응답 DTO 작성

### 3. Data Model

- `users` 모델 구현
- `agents` 모델 구현
- `payment_intents` 모델 구현
- `payment_requests` 모델 구현
- `policy_decisions` 모델 구현
- `approvals` 모델 구현
- `payment_results` 모델 구현
- `audit_events` 모델 구현
- `audit_anchors` 모델 구현
- enum 상태값 구현
- 테이블 관계와 인덱스 정리

### 4. Intent / Agent / Payment Request API

- intent 생성 API
- intent 목록 API
- intent 상세 API
- agent 생성 API
- agent 목록 API
- payment request 생성 API
- payment request 목록 API
- payment request 상세 API
- payment request evaluate API
- API 요청 예시 작성

### 5. Policy Engine

- intent 활성 상태 검사
- intent 만료 검사
- 총 예산 검사
- 단건 한도 검사
- merchant allowlist 검사
- merchant blocklist 검사
- category 검사
- 승인 필요 금액 검사
- quote 중복 검사
- prompt injection 의심 문구 검사
- `ALLOW` 상태 전이
- `REQUIRE_APPROVAL` 상태 전이
- `DENY` 상태 전이
- 정책 판단 사유 저장

### 6. Approval / Payment Simulator

- 승인 API 구현
- 거절 API 구현
- 승인자와 승인 시간 저장
- mock 결제 실행 API 구현
- simulated transaction id 생성
- mock 결제 성공 처리
- mock 결제 실패 처리
- payment result 저장
- 중복 결제 방지

### 7. Mock Merchant

- API server 내부 `merchantmock` 모듈 생성
- 유료 리소스 요청 API 구현
- `402 Payment Required` 스타일 quote 응답 구현
- quoteId 생성
- quoteHash 생성
- mock payment token 또는 simulated transaction id 확인
- 결제 완료 후 resource 응답 구현

### 8. Audit / Hash

- 이벤트 타입 정의
- canonical JSON serializer 구현
- 공통 event envelope 구현
- SHA-256 eventHash 생성
- intent eventHash 생성
- payment request eventHash 생성
- policy decision eventHash 생성
- approval eventHash 생성
- payment result eventHash 생성
- hash 재계산 검증 로직 구현

### 9. Smart Contract / Blockchain

- `agentpay-guard-audit-anchor` Hardhat 프로젝트 생성
- `AuditAnchor.sol` 작성
- `anchorEvent` 함수 구현
- `getEventHash` 함수 구현
- 이벤트 emit 구현
- 컨트랙트 테스트 작성
- Hardhat local node 실행 문서화
- 로컬 배포 스크립트 작성
- ABI 추출
- contract address 관리

### 10. Backend - Blockchain 연동

- RPC URL 설정
- contract address 설정
- private key 환경 변수 설정
- 컨트랙트 호출 방식 결정
- eventHash 온체인 기록 API 구현
- txHash 저장
- 온체인 hash 조회
- DB hash와 온체인 hash 비교
- 실패 처리

### 11. Dashboard

- `agentpay-guard-dashboard` React + TypeScript 프로젝트 생성
- API client 작성
- 기본 레이아웃 작성
- Intent 목록 화면
- Intent 상세 화면
- Payment Request 목록 화면
- Payment Request 상세 화면
- 정책 판단 결과 표시
- 승인/거절 버튼
- mock 결제 실행 버튼
- Audit Anchor 목록 화면
- txHash 표시
- hash verify 결과 표시

### 12. Python Sample Agent

- `agentpay-guard-sample-agent` 생성
- Python 의존성 정의
- 설정 파일 작성
- intent id 입력 처리
- mock merchant 호출
- 402 quote 파싱
- Guard payment request 생성
- 정책 결과 확인
- `ALLOW`이면 mock 결제 요청
- `REQUIRE_APPROVAL`이면 대기 또는 종료
- `PAID` 후 merchant 재요청
- CLI 결과 출력

### 13. 통합 테스트/시연

- 정상 허용 시나리오 통합 테스트
- 예산 초과 차단 시나리오 통합 테스트
- 승인 필요 시나리오 통합 테스트
- merchant allowlist 테스트
- merchant blocklist 테스트
- prompt injection 문구 차단 테스트
- hash verify 테스트
- 컨트랙트 테스트
- Python Agent end-to-end 테스트
- README 따라 새 환경 실행 테스트

### 14. 제출 문서/영상

- README 완성
- 설치 방법 작성
- 실행 방법 작성
- API 사용 예시 작성
- 아키텍처 다이어그램 정리
- 3분 시연 영상 스크립트 작성
- 결과보고서 초안 작성
- 라이선스 명시
- 오픈소스 사용 목록 작성
- secret 포함 여부 점검

## 반드시 할 것

### 1. 기획/범위 확정

- 프로젝트 한 줄 정의 확정
- 실제 결제가 아니라 mock 결제 PoC임을 명확히 정의
- MVP 시나리오 3개 확정
- 제외 범위 확정
  - 실제 금융 결제 제외
  - 카드/계좌/PG 연동 제외
  - 메인넷 자산 이동 제외
  - 완전한 x402/AP2 구현 제외
- 대회 제출용 개발 목적 정리
- 기대효과 정리

### 2. 프로젝트 구조

- 루트 README 작성
- `agentpay-guard-api-server` 생성
- `agentpay-guard-dashboard` 생성
- `agentpay-guard-sample-agent` 생성
- `agentpay-guard-audit-anchor` 생성
- `.gitignore` 작성
- `.env.example` 작성
- 실행 순서 문서화

### 3. 기술 스택 확정

- Spring Boot 버전 결정
- Java 버전 결정
- DB는 PostgreSQL로 확정
- JPA 사용 여부 결정
- 대시보드는 React + TypeScript로 확정
- Hardhat 사용 확정
- 로컬 체인은 Hardhat node로 확정
- 테스트넷은 optional로 보류
- Python Agent 의존성 결정

### 4. 데이터 모델

- `users` 모델
- `agents` 모델
- `payment_intents` 모델
- `payment_requests` 모델
- `policy_decisions` 모델
- `approvals` 모델
- `payment_results` 모델
- `audit_anchors` 모델
- 상태값 enum 정의
- 테이블 관계 정의

### 5. API

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

### 6. 정책 엔진

- intent 활성 상태 검사
- intent 만료 검사
- 총 예산 검사
- 단건 한도 검사
- 허용 merchant 검사
- 차단 merchant 검사
- category 검사
- 승인 필요 금액 검사
- 간단한 prompt injection 의심 문구 검사
- `ALLOW` 처리
- `REQUIRE_APPROVAL` 처리
- `DENY` 처리
- 정책 판단 사유 저장

### 7. Mock Merchant

- 유료 API 요청 시 x402-style `402 Payment Required` 응답
- quote 생성
- quote hash 생성
- mock payment token 또는 simulated transaction id 확인
- mock 결제 완료 후 resource 응답

### 8. Payment Simulator

- mock 결제 성공 처리
- mock 결제 실패 처리
- simulated transaction id 생성
- payment result 저장
- payment request 상태 변경
- 결제 중복 처리 방지

### 9. Approval Flow

- 승인 필요 상태 생성
- 승인 API 구현
- 거절 API 구현
- 승인 후 mock 결제 연결
- 거절 시 결제 차단
- 승인자와 승인 시간 저장

### 10. Audit / Hash

- 이벤트 타입 정의
- canonical JSON 규칙 정의
- intent hash 생성
- payment request hash 생성
- policy decision hash 생성
- approval hash 생성
- payment result hash 생성
- eventHash 저장
- hash 재계산 검증 로직 구현

### 11. Smart Contract

- Hardhat 프로젝트 생성
- `AuditAnchor.sol` 작성
- `anchorEvent` 함수 구현
- `getEventHash` 함수 구현
- 이벤트 emit 구현
- 컨트랙트 테스트 작성
- 로컬 배포 스크립트 작성
- ABI 추출
- contract address 관리

### 12. Spring Boot - Blockchain 연동

- web3j 의존성 추가
- RPC URL 설정
- contract address 설정
- private key 환경 변수 설정
- eventHash 온체인 기록 API 구현
- txHash 저장
- 온체인 hash 조회
- DB hash와 온체인 hash 비교
- 실패 처리

### 13. Python Sample Agent

- 설정 파일 작성
- intent id 입력 받기
- mock merchant 호출
- 402 quote 파싱
- AgentPay Guard에 payment request 생성
- 정책 결과 확인
- `ALLOW`이면 mock 결제 요청
- `REQUIRE_APPROVAL`이면 대기 또는 종료
- `PAID` 후 merchant 재요청
- 결과 출력

### 14. 대시보드

- Intent 목록 화면
- Intent 상세 화면
- PaymentRequest 목록 화면
- PaymentRequest 상세 화면
- 정책 판단 결과 표시
- 승인/거절 버튼
- mock 결제 실행 버튼
- AuditAnchor 목록 화면
- txHash 표시
- hash verify 결과 표시

### 15. 테스트/검증

- 정상 허용 시나리오 테스트
- 예산 초과 차단 테스트
- 승인 필요 테스트
- merchant allowlist 테스트
- blocklist 테스트
- prompt injection 문구 차단 테스트
- hash verify 테스트
- 컨트랙트 테스트
- Python Agent end-to-end 테스트
- README 따라 실행 테스트

### 16. 문서/제출물

- README 작성
- 프로젝트 개요 작성
- 설치 방법 작성
- 실행 방법 작성
- API 사용 예시 작성
- 아키텍처 다이어그램 작성
- 3분 시연 영상 스크립트 작성
- 결과보고서 초안 작성
- 라이선스 명시
- 오픈소스 사용 목록 작성
- 발표용 핵심 문장 정리

## 하면 좋은 것

### 1. 정책 고도화

- 정책 버전 관리
- risk score 계산
- 중복 quote 검사 고도화
- merchant quote와 payment request binding
- prompt injection 탐지 문구 확장
- category별 예산 관리

### 2. x402/AP2 연계 조사

- x402 SDK 일부 실험
- x402 실제 request/response 구조 조사
- AP2 Mandate 구조 조사
- 사용자 intent 서명 구조 초안 작성

### 3. 블록체인 고도화

- 테스트넷 배포
- batch hash anchoring
- Merkle root 기반 anchoring
- 컨트랙트 이벤트 조회 화면
- 중복 eventId 처리 정책 고도화

### 4. 대시보드 고도화

- 시연용 seed data 버튼
- Agent별 결제 요청 통계
- intent별 남은 예산 표시
- merchant별 사용량 표시
- 정책 판단 사유 시각화

### 5. 개발 편의

- Docker Compose
- Swagger/OpenAPI 문서
- Postman 또는 HTTP request 예시
- 샘플 데이터 자동 생성 스크립트
- CI 기본 테스트

## 버릴 것

### 1. 실제 결제

- 카드 결제
- 계좌 이체
- PG 연동
- 실제 USDC 결제
- 실제 x402 결제 처리
- 메인넷 결제

### 2. 금융 서비스 수준 기능

- KYC
- AML
- 제재 주소 필터링
- 실사용 지갑 관리
- 수수료 정산
- 환불 정산
- 회계 시스템 연동

### 3. 과도한 Agent 기능

- 완전 자율 Agent
- 복잡한 쇼핑 Agent
- 다중 Agent 협상
- Agent-to-Agent 실결제
- 실서비스 API 자동 구매

### 4. 과도한 보안/정책 기능

- 완전한 prompt injection 방어
- LLM 기반 정책 판단 자동화
- 복잡한 RBAC
- 조직별 승인 체계 전체 구현
- 컴플라이언스 리포트 자동 생성

### 5. 과도한 블록체인 기능

- 자체 토큰 발행
- 자체 결제 컨트랙트
- escrow 결제
- 멀티체인 지원
- NFT/증명서 발급
- 온체인 개인정보 저장

## 일정

### 1주차: 2026-06-22 ~ 2026-06-28

목표: 범위, 기술 선택, 저장소 뼈대 확정

- 프로젝트 정의 확정
- MVP 시나리오 3개 확정
- DB 모델 초안 확정
- API 목록 확정
- PostgreSQL 사용 확정
- React + TypeScript Dashboard 확정
- Mock Merchant 내부 모듈 구현 확정
- Hardhat local node 기준 확정
- canonical JSON + SHA-256 규칙 확정
- Spring Boot 프로젝트 생성
- React + TypeScript Dashboard 프로젝트 생성
- Hardhat 프로젝트 생성
- Python Agent 디렉터리 생성
- README 초안 작성
- `.gitignore`, `.env.example`, `docker-compose.yml` 초안 작성

### 2주차: 2026-06-29 ~ 2026-07-05

목표: Backend 기본 API와 PostgreSQL 모델 구현

- entity/model 구현
- PostgreSQL 연결
- JPA repository 구현
- intent API 구현
- agent API 구현
- payment request API 구현
- 기본 validation 구현
- 공통 에러 응답 구현
- API 요청 예시 작성

### 3주차: 2026-07-06 ~ 2026-07-12

목표: 정책 엔진 구현

- 예산 검사 구현
- 단건 한도 검사 구현
- merchant allowlist/blocklist 검사 구현
- category 검사 구현
- 만료 시간 검사 구현
- prompt injection 의심 문구 검사 구현
- ALLOW / REQUIRE_APPROVAL / DENY 상태 전이 구현
- policy decision 저장 구현
- 정책 엔진 단위 테스트 작성

### 4주차: 2026-07-13 ~ 2026-07-19

목표: 승인 흐름과 mock 결제 구현

- 승인 API 구현
- 거절 API 구현
- Payment Simulator 구현
- payment result 저장
- 상태 전이 보강
- 중복 결제 방지 구현
- 정상 허용/승인 필요/거절 케이스 수동 테스트

### 5주차: 2026-07-20 ~ 2026-07-26

목표: Mock Merchant와 Python Sample Agent 구현

- API server 내부 mock merchant 모듈 구현
- 402 Payment Required 응답 구현
- quote/hash 생성
- Python Agent 구현
- Agent end-to-end 흐름 테스트

### 6주차: 2026-07-27 ~ 2026-08-02

목표: Audit / Hash와 AuditAnchor 컨트랙트 구현

- canonical JSON serializer 구현
- eventHash 생성 로직 구현
- `AuditAnchor.sol` 구현
- Hardhat 테스트 작성
- 로컬 배포 스크립트 작성
- ABI/contract address 정리
- eventHash 기록/조회 테스트

### 7주차: 2026-08-03 ~ 2026-08-09

목표: API 서버와 Hardhat local chain 연동

- 컨트랙트 호출 방식 확정
- eventHash 생성 규칙 구현
- 온체인 기록 API 구현
- txHash 저장
- verify API 구현
- 실패 처리

### 8주차: 2026-08-10 ~ 2026-08-16

목표: React Dashboard와 통합 시나리오 완성

- React + TypeScript 앱 구현
- Intent 화면
- PaymentRequest 화면
- 정책 판단 결과 화면
- 승인/거절 버튼
- txHash/verify 화면
- 3개 MVP 시나리오 통합 테스트

### 9주차: 2026-08-17 ~ 2026-08-20

목표: PoC 기능 완료

- 버그 수정
- end-to-end 재검증
- README 보강
- API 사용 예시 정리
- 샘플 데이터 정리
- 시연 스크립트 작성
- 새 환경 실행 테스트
- 기능 동결

### 10주차: 2026-08-21 ~ 2026-08-27

목표: 제출 안정화

- 새 기능 추가 금지
- 버그 수정
- 결과보고서 작성
- 3분 영상 촬영
- 라이선스 점검
- secret 포함 여부 점검
- 제출 파일 정리

## 최종 완료 기준

- 정상 허용, 예산 초과 차단, 승인 필요 시나리오가 동작한다.
- Python Agent가 mock merchant의 402 응답을 받고 Guard에 결제 요청을 생성한다.
- 정책 엔진이 결제 요청을 판단한다.
- mock 결제 결과가 저장된다.
- 감사 eventHash가 컨트랙트에 기록된다.
- txHash와 hash verify 결과를 대시보드에서 확인할 수 있다.
- README만 보고 기본 데모를 실행할 수 있다.
- 결과보고서와 3분 시연 영상 제출 준비가 끝난다.
