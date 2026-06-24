# AgentPay Guard 고도화 방향

작성일: 2026-06-22  
상태: draft

## 목적

이 문서는 AgentPay Guard가 1차 PoC를 통과했다고 가정했을 때, 이후 어떤 방향으로 고도화할 수 있는지 러프하게 정리한다.

PoC 단계에서는 mock 결제, 단순 정책 엔진, 기본 감사 hash 기록에 집중한다. 고도화 단계에서는 실제 Agent 결제 생태계와 연결할 수 있는 정책, 보안, 감사, 연동 기능을 확장한다.

## 1. x402 연동 고도화

PoC에서는 x402-style mock merchant만 구현한다.

고도화 단계에서는 Coinbase x402 구조를 더 깊게 분석하고, 실제 SDK 또는 sandbox 수준의 연동을 검토한다.

할 일:

- x402 request/response 구조 분석
- payment required 응답 형식 반영
- quote와 payment request binding 강화
- x402 SDK 일부 연동 실험
- mock merchant를 x402 호환 형태로 개선

## 2. 사용자 Intent 서명 구조

PoC에서는 사용자의 intent를 DB에 저장하는 수준이다.

고도화 단계에서는 사용자가 위임한 결제 조건을 서명된 구조로 남기는 방식을 검토한다.

할 일:

- intent hash 생성
- 사용자 서명 필드 추가
- intent 변경 이력 관리
- intent 만료와 폐기 처리
- AP2 Mandate 구조 참고

## 3. 정책 엔진 고도화

PoC에서는 단순 규칙 기반 정책만 사용한다.

고도화 단계에서는 더 정교한 정책 모델을 도입한다.

할 일:

- 정책 버전 관리
- merchant별 정책
- category별 예산
- agent별 권한
- 시간대별 사용 제한
- risk score 계산
- 정책 변경 이력 감사

## 4. Prompt Injection 방어 강화

PoC에서는 의심 문구 기반의 단순 탐지만 한다.

고도화 단계에서는 Agent 결제 요청이 외부 문서나 API 응답에 의해 조작되는 상황을 더 구체적으로 방어한다.

할 일:

- 결제 요청 reason과 원본 사용자 intent 비교
- 외부 응답에서 추출된 지시와 사용자 지시 분리
- 위험 문구 룰셋 확장
- LLM 기반 보조 평가 검토
- 공격 시나리오 테스트셋 작성

## 5. 감사 로그 고도화

PoC에서는 이벤트별 eventHash를 컨트랙트에 기록한다.

고도화 단계에서는 대량 이벤트를 효율적으로 검증할 수 있도록 구조를 개선한다.

할 일:

- batch hash anchoring
- Merkle root 기반 anchoring
- 이벤트 포함 증명
- 감사 리포트 생성
- hash 검증 자동화

## 6. Agent 지갑 및 권한 관리

PoC에서는 실제 지갑 관리를 하지 않는다.

고도화 단계에서는 Agent가 결제 요청을 생성할 수 있는 권한과 한도를 더 명확히 관리한다.

할 일:

- Agent별 권한 설정
- Agent별 결제 한도
- Agent별 허용 merchant
- API key와 결제 권한 분리
- 결제 요청 replay 방지

## 7. 승인 워크플로우 확장

PoC에서는 단순 승인/거절만 구현한다.

고도화 단계에서는 실제 조직에서 쓸 수 있는 승인 흐름을 추가한다.

할 일:

- 다중 승인
- 금액별 승인자 분리
- 승인 만료
- 승인 사유 기록
- Slack/Email 알림
- 긴급 차단 기능

## 8. 대시보드 고도화

PoC 대시보드는 시연용 상태 확인 화면이다.

고도화 단계에서는 운영자가 실제로 사용할 수 있는 분석 화면을 추가한다.

할 일:

- intent별 사용량
- agent별 결제 요청 통계
- merchant별 사용량
- 차단 사유 통계
- 남은 예산 표시
- txHash와 explorer 링크
- 감사 검증 상태 요약

## 9. 실제 서비스 적용 시나리오 확장

PoC에서는 날씨 API 같은 단순 mock merchant를 사용한다.

고도화 단계에서는 더 현실적인 유료 리소스 사용 시나리오를 추가한다.

후보:

- 리서치 Agent의 뉴스/논문/기업 데이터 API 사용
- 쇼핑 Agent의 상품 후보 결제 요청 검증
- 업무 Agent의 LLM API/이미지 API 사용량 통제
- 데이터 구매 Agent의 유료 데이터셋 접근 통제

## 10. 보안 위협 모델 작성

고도화 단계에서는 프로젝트의 보안 가정을 명확히 문서화해야 한다.

검토할 위협:

- prompt injection
- API key leakage
- 결제 요청 replay
- quote 위조
- merchant spoofing
- audit log tampering
- private key leakage
- policy bypass

## 우선순위 제안

1. x402-style mock을 실제 x402 구조에 가깝게 개선
2. 사용자 intent 서명 구조 추가
3. 정책 엔진 고도화
4. prompt injection 방어 강화
5. batch/Merkle 기반 감사 로그 개선
6. 승인 워크플로우 확장
7. 대시보드 분석 기능 추가
8. 실제 서비스 적용 시나리오 확장

## 장기 목표

AgentPay Guard의 장기 목표는 AI Agent가 유료 리소스를 사용할 때 필요한 결제 전 검증, 권한 통제, 감사 로그 무결성 검증을 제공하는 오픈소스 보안 레이어가 되는 것이다.

즉 결제 rail을 새로 만드는 것이 아니라, 기존 결제·과금 시스템 앞에서 Agent의 유료 행동을 안전하게 통제하는 것을 목표로 한다.
