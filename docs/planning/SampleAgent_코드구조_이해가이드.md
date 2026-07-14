# Sample Agent 코드 구조 이해 가이드

FastAPI 처음 접하는 사람 기준으로, 현재 구현된 코드가 어떻게 생겼는지 파일별로 설명합니다.

---

## 디렉토리 구조

```
agentpay-guard-sample-agent/
│
├── run.py                        # 서버 실행 진입점
├── .env                          # 환경변수 (API 키 등, git에 올리면 안 됨)
│
└── app/
    ├── main.py                   # FastAPI 앱 생성, 라우터 등록
    ├── config.py                 # .env 파일 읽어서 설정값 제공
    │
    ├── models/
    │   └── schemas.py            # 요청/응답 데이터 구조 정의 (Pydantic)
    │
    ├── routers/
    │   ├── agent.py              # /agent/* 엔드포인트
    │   └── mock.py               # /mock/* 엔드포인트 (테스트용 목록 조회)
    │
    └── services/
        ├── agent_service.py      # 전체 흐름 오케스트레이션
        ├── ai_advisor.py         # Claude API 호출
        ├── guard_api_client.py   # Spring Boot Guard API HTTP 클라이언트
        └── mock_guard_client.py  # Guard API 대신 인메모리로 동작하는 Mock
```

---

## 파일별 역할

### `run.py` — 서버 실행

```python
uvicorn.run("app.main:app", host="0.0.0.0", port=8001, reload=True)
```

- `python run.py` 하면 서버가 뜸
- `reload=True` : 코드 수정하면 자동 재시작

---

### `app/config.py` — 환경변수 설정

```python
class Settings(BaseSettings):
    guard_api_base_url: str = "http://localhost:8080"  # Spring Boot 주소
    anthropic_api_key: str = ""                         # Claude API 키
    agent_server_port: int = 8001
    use_mock: bool = True                               # True면 Spring Boot 없이 동작
```

- `.env` 파일에서 값을 읽어옴
- `use_mock=True` 이면 `MockGuardClient`를 씀 (Spring Boot 없어도 됨)

---

### `app/main.py` — FastAPI 앱 생성

```python
app = FastAPI(title="AgentPay Guard — Sample Agent Server")

app.include_router(agent.router)   # /agent/* 경로 등록
app.include_router(mock.router)    # /mock/*  경로 등록
```

- FastAPI 앱을 만들고 라우터를 붙이는 곳
- `http://localhost:8001/docs` 접속하면 자동 생성된 API 문서 확인 가능

---

### `app/models/schemas.py` — 데이터 구조 (Pydantic 모델)

API의 요청/응답 형태를 클래스로 정의합니다.

```
요청 모델
└── AgentRunRequest          POST /agent/run 의 Body

응답 모델
├── AgentRunResponse         POST /agent/run 의 응답
└── PaymentStatusResponse    GET  /agent/requests/{id} 의 응답

내부 데이터 모델
├── IntentPolicy             사용자 정책 (예산, 허용 merchant 등)
├── MerchantQuote            머천트가 제시한 견적 (비용)
├── AIAssessment             Claude의 평가 결과
└── RuleCheckResult          규칙 기반 검사 결과

Guard API 응답 모델 (Spring Boot 원본 구조)
├── GuardIntentResponse      Spring Boot에서 오는 Intent JSON
└── GuardPaymentRequestResponse  Spring Boot에서 오는 Payment Request JSON
```

---

### `app/routers/agent.py` — API 엔드포인트

현재 엔드포인트 3개:

```
POST /agent/run                       전체 흐름 실행 (평가 + 결제까지)
GET  /agent/requests/{id}             Payment Request 상태 조회
GET  /agent/intents/{intent_id}/policy  Intent 정책 미리 보기
```

코드 구조:
```python
router = APIRouter(prefix="/agent")   # 모든 경로 앞에 /agent 가 붙음

@router.post("/run")
async def run_agent(body: AgentRunRequest) -> AgentRunResponse:
    return await _agent_service.run(body)   # 실제 로직은 service에 위임
```

- 라우터는 요청을 받아서 service에 넘기고 응답만 반환
- 에러가 나면 `HTTPException`으로 변환해서 HTTP 상태코드와 함께 반환

---

### `app/routers/mock.py` — 테스트용 조회 엔드포인트

```
GET /mock/intents     사용 가능한 intent_id 목록 확인
GET /mock/resources   사용 가능한 resource 목록 확인
GET /mock/scenarios   시나리오별 예시 요청 확인
```

개발/테스트 중 "어떤 값 넣어야 하지?" 할 때 여기서 확인.

---

### `app/services/agent_service.py` — 전체 흐름 오케스트레이션

`POST /agent/run` 을 받으면 아래 순서로 실행:

```
1. Guard에서 Intent(정책) 조회
      ↓
2. Intent 상태 확인 (ACTIVE 아니면 DENY)
      ↓
3. 머천트에서 Quote(비용 견적) 조회
      ↓
4. Rule Check — 예산 초과? 차단 merchant? 등 규칙 검사
      → DENY이면 여기서 바로 반환
      ↓
5. AI 평가 — Claude에게 "이 결제 괜찮아?" 질문
      ↓
6. Rule + AI 결합해서 agent 판단 결정
      ↓
7. Guard에 Payment Request 생성 (기록)
      ↓
8. Guard 자체 정책 평가 실행
      ↓
9. Guard + Agent 중 더 엄격한 결정 채택
      ↓
10. ALLOW면 실제 결제 실행
      ↓
11. 결과 반환
```

핵심 메서드:
- `run()` : 위 전체 흐름
- `_rule_check()` : 예산/merchant 규칙 검사
- `_merge_decision()` : rule + AI 결과 합산
- `_effective_decision()` : agent 결정 vs Guard 결정 중 더 엄격한 것 선택

---

### `app/services/ai_advisor.py` — Claude AI 호출

Claude에게 결제 요청을 평가하게 합니다.

```python
# 프롬프트에 Intent 정책 + 결제 요청 정보를 넣어서 Claude에게 전달
message = self._client.messages.create(
    model="claude-haiku-4-5-20251001",
    system="너는 결제 보안 어드바이저야...",
    messages=[{"role": "user", "content": 평가_요청_내용}]
)
```

Claude 응답 형식 (JSON):
```json
{
    "aligns_with_intent": true,
    "risk_level": "LOW",
    "has_injection_risk": false,
    "recommendation": "ALLOW",
    "reasoning": "날씨 데이터 조회는 시장 조사 목적과 일치합니다."
}
```

---

### `app/services/guard_api_client.py` — Spring Boot API 클라이언트

Spring Boot가 실행 중일 때 실제로 HTTP 요청을 보내는 클라이언트.

```
get_intent(intent_id)                    GET  /api/intents/{id}
get_merchant_quote(resource_path)        GET  /mock-merchant/resources/{id}
create_payment_request(...)              POST /api/payment-requests
evaluate_payment_request(id)             POST /api/payment-requests/{id}/evaluate
trigger_payment(id)                      POST /api/payment-requests/{id}/pay
get_payment_request(id)                  GET  /api/payment-requests/{id}
```

---

### `app/services/mock_guard_client.py` — Mock 클라이언트

Spring Boot 없이 테스트할 수 있도록 메모리에 데이터를 갖고 있는 가짜 클라이언트.
`config.py`의 `use_mock=True`이면 이걸 씁니다.

**Mock Intent 시나리오:**

| intent_id | 설명 | 예상 결과 |
|---|---|---|
| `intent-allow` | 정상 허용 | ALLOW |
| `intent-approval` | 승인 임계값 초과 | REQUIRE_APPROVAL |
| `intent-budget-exceeded` | 예산 거의 소진 | DENY |
| `intent-blocked` | 차단된 merchant | DENY |

**Mock Resource (비용):**

| resource | merchant | 비용 |
|---|---|---|
| `/premium/weather/seoul` | weather-api.local | $0.10 |
| `/premium/weather/busan` | weather-api.local | $0.10 |
| `/premium/company/naver` | company-data.local | $1.50 |
| `/premium/news/latest` | news-api.local | $0.30 |
| `/premium/image/generate` | image-api.local | $0.50 |

---

## 요청 흐름 전체 그림

```
클라이언트 (curl / Swagger UI)
    │
    │  POST /agent/run { intent_id, resource, reason }
    ▼
agent.py (라우터)
    │  body를 AgentRunRequest로 파싱
    │  _agent_service.run(body) 호출
    ▼
agent_service.py (오케스트레이션)
    ├── mock_guard_client.get_intent()       → IntentPolicy
    ├── mock_guard_client.get_merchant_quote() → MerchantQuote
    ├── _rule_check()                        → RuleCheckResult
    ├── ai_advisor.assess_payment()          → AIAssessment  (Claude 호출)
    ├── mock_guard_client.create_payment_request()
    ├── mock_guard_client.evaluate_payment_request()
    └── mock_guard_client.trigger_payment()
    │
    ▼
AgentRunResponse 반환
    │
    ▼
클라이언트에게 JSON 응답
```

---

## 앞으로 할 것 (Estimate / Pay 분리)

현재 `/agent/run` 이 위 흐름을 한 번에 다 처리합니다.

이걸 아래처럼 분리할 예정:

```
POST /agent/estimate   →  1~7단계만 (결제 없이 평가 + payment_request_id 반환)
POST /agent/pay/{id}   →  8~11단계만 (실제 결제 실행)
```

수정할 파일:
1. `schemas.py` — `EstimateResponse`, `PayResponse` 모델 추가
2. `agent_service.py` — `estimate()`, `pay()` 메서드 추가
3. `agent.py` — `/estimate`, `/pay/{id}` 엔드포인트 추가

구현 가이드: `AgentPay_Estimate_Pay_분리_구현가이드.md` 참고
