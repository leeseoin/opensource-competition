# AgentPay Sample Agent — Estimate / Pay 분리 구현 가이드

> FastAPI 처음이라면 각 섹션을 순서대로 읽고, 코드를 직접 타이핑해보세요.  
> 복붙보다 직접 치는 게 훨씬 빨리 익힙니다.

---

## 1. 현재 구조의 문제

지금 `/agent/run` 하나가 아래를 모두 처리합니다.

```
POST /agent/run
  ├─ 1. Intent(정책) 조회
  ├─ 2. Merchant Quote(비용) 조회
  ├─ 3. Rule Check (예산 초과 여부 등)
  ├─ 4. AI 평가 (Claude)
  ├─ 5. Guard에 Payment Request 생성
  ├─ 6. Guard 정책 평가
  └─ 7. 실제 결제 실행  ← 여기까지 한 번에 다 됨
```

**문제점:** 비용/결과를 미리 확인하고 싶어도 바로 결제까지 실행됩니다.

---

## 2. 목표 구조

두 단계로 분리합니다.

```
POST /agent/estimate        POST /agent/pay/{id}
  ├─ 1. Intent 조회           ├─ 6. Guard 정책 평가
  ├─ 2. Quote 조회            └─ 7. 실제 결제 실행
  ├─ 3. Rule Check
  ├─ 4. AI 평가
  └─ 5. Payment Request 생성 (결제 X)
       └─ payment_request_id 반환
```

**흐름:**
```
클라이언트
  │
  ├─ POST /agent/estimate  →  { payment_request_id, quote, final_decision, ... }
  │                                         ↓
  │                           사용자/에이전트가 결과 확인
  │
  └─ POST /agent/pay/{payment_request_id}  →  { 결제 결과 }
```

---

## 3. FastAPI 핵심 개념 (처음이라면 꼭 읽기)

### 3-1. 엔드포인트란?

```python
@router.post("/estimate")          # HTTP 메서드 + URL 경로
async def estimate(body: AgentRunRequest) -> AgentRunResponse:
    ...
```

- `@router.post` : POST 요청을 받겠다는 데코레이터
- `body: AgentRunRequest` : 요청 Body를 자동으로 파싱 (Pydantic 모델)
- `-> AgentRunResponse` : 응답 형태 명시 (자동 직렬화)

### 3-2. Pydantic 모델이란?

요청/응답의 데이터 구조를 정의하는 클래스입니다.

```python
# schemas.py 에 있는 예시
class AgentRunRequest(BaseModel):
    intent_id: str               # 필수
    agent_id: str = ""           # 기본값 있으면 선택
    resource: str                # 필수
    reason: str                  # 필수
```

FastAPI는 이 클래스를 보고 자동으로 요청 Body를 검증하고 파싱합니다.

### 3-3. URL 경로 파라미터

```python
@router.post("/pay/{payment_request_id}")
async def pay(payment_request_id: str):   # URL의 {payment_request_id}가 인자로 들어옴
    ...
```

예: `POST /agent/pay/mock-payreq-abc123` → `payment_request_id = "mock-payreq-abc123"`

### 3-4. async/await

FastAPI는 비동기 기반입니다. 외부 API 호출할 때 `await`를 씁니다.

```python
async def estimate(...):
    intent = await self._guard.get_intent(req.intent_id)   # await 필수
    quote  = await self._guard.get_merchant_quote(req.resource)
```

---

## 4. 파일 구조 (바꿀 파일들)

```
agentpay-guard-sample-agent/
  app/
    models/
      schemas.py          ← EstimateResponse 모델 추가
    services/
      agent_service.py    ← estimate(), pay() 메서드 추가
    routers/
      agent.py            ← /estimate, /pay/{id} 엔드포인트 추가
```

---

## 5. 단계별 구현

### Step 1 — schemas.py에 새 응답 모델 추가

`app/models/schemas.py` 파일 하단에 추가합니다.

```python
# 기존 AgentRunResponse 아래에 추가

class EstimateResponse(BaseModel):
    payment_request_id: str           # 나중에 pay()에 쓸 ID
    quote: MerchantQuote              # 예상 비용
    rule_check: RuleCheckResult       # 규칙 검사 결과
    ai_assessment: AIAssessment       # AI 평가 결과
    preliminary_decision: PolicyDecision  # 예상 결정 (ALLOW/DENY/REQUIRE_APPROVAL)
    message: str


class PayResponse(BaseModel):
    payment_request_id: str
    guard_decision: str | None = None
    final_decision: PolicyDecision
    message: str
    error: str | None = None
```

**포인트:**
- `EstimateResponse`는 `payment_request_id`를 반드시 포함 → 이걸로 `pay()`를 호출
- `PayResponse`는 최종 결제 결과만 담음

---

### Step 2 — agent_service.py에 메서드 분리

`app/services/agent_service.py`의 `run()` 메서드를 두 개로 쪼갭니다.

#### 2-1. estimate() 메서드

```python
async def estimate(self, req: AgentRunRequest) -> EstimateResponse:
    agent_id = req.agent_id or "agent-sample-001"

    # 1. Intent 조회
    try:
        intent = await self._guard.get_intent(req.intent_id)
    except GuardApiError as exc:
        # 에러 처리는 라우터에서 HTTPException으로 올림 (아래 Step 3 참고)
        raise

    if intent.status != "ACTIVE":
        raise ValueError(f"Intent is not active (status={intent.status})")

    # 2. 머천트 Quote 조회
    quote = await self._guard.get_merchant_quote(req.resource)

    # 3. Rule Check
    rule_check = self._rule_check(intent, quote)

    # 4. AI 평가
    ai_assessment = await self._ai.assess_payment(intent, quote, req.reason)

    # 5. 예상 결정 계산
    preliminary_decision = self._merge_decision(rule_check, ai_assessment)

    # 6. Guard에 Payment Request 생성 (결제는 아직 X)
    pr = await self._guard.create_payment_request(
        intent_id=req.intent_id,
        agent_id=agent_id,
        quote=quote,
        reason=req.reason,
        ai_reasoning=ai_assessment.reasoning,
    )

    message = self._build_message(preliminary_decision, ai_assessment, rule_check)

    return EstimateResponse(
        payment_request_id=pr.id,
        quote=quote,
        rule_check=rule_check,
        ai_assessment=ai_assessment,
        preliminary_decision=preliminary_decision,
        message=message,
    )
```

#### 2-2. pay() 메서드

```python
async def pay(self, payment_request_id: str) -> PayResponse:
    # Guard 정책 평가
    try:
        guard_decision = await self._guard.evaluate_payment_request(payment_request_id)
    except GuardApiError as exc:
        return PayResponse(
            payment_request_id=payment_request_id,
            final_decision=PolicyDecision.DENY,
            message=f"Guard evaluation failed: {exc.detail}",
            error=str(exc),
        )

    # Guard가 ALLOW일 때만 결제 실행
    effective = PolicyDecision(guard_decision) if guard_decision in PolicyDecision._value2member_map_ else PolicyDecision.DENY

    message = f"Guard decision: {guard_decision}"

    if effective == PolicyDecision.ALLOW:
        try:
            await self._guard.trigger_payment(payment_request_id)
            message += " | Payment executed successfully."
        except GuardApiError as exc:
            message += f" | Payment trigger failed: {exc.detail}"

    return PayResponse(
        payment_request_id=payment_request_id,
        guard_decision=guard_decision,
        final_decision=effective,
        message=message,
    )
```

**포인트:**
- `pay()`는 payment_request_id만 받음 → 이미 `estimate()`에서 생성된 것을 씀
- Guard 평가와 결제만 담당

---

### Step 3 — agent.py에 엔드포인트 추가

`app/routers/agent.py`에 두 엔드포인트를 추가합니다.

```python
from app.models.schemas import (
    AgentRunRequest,
    AgentRunResponse,
    EstimateResponse,   # 새로 추가
    PayResponse,        # 새로 추가
    PaymentStatusResponse,
)

# 기존 run_agent() 아래에 추가

@router.post(
    "/estimate",
    response_model=EstimateResponse,
    summary="비용 예측 및 정책 평가 (결제 없음)",
    description=(
        "1. Intent 정책 로드\n"
        "2. 머천트 Quote 조회 (예상 비용)\n"
        "3. Rule Check\n"
        "4. AI 평가\n"
        "5. Payment Request 생성 (결제 실행 X)\n"
        "반환된 payment_request_id로 /pay/{id}를 호출하면 실제 결제됩니다."
    ),
)
async def estimate_agent(body: AgentRunRequest) -> EstimateResponse:
    try:
        return await _agent_service.estimate(body)
    except GuardApiError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.post(
    "/pay/{payment_request_id}",
    response_model=PayResponse,
    summary="실제 결제 실행",
    description=(
        "/estimate 로 받은 payment_request_id를 사용해 실제 결제를 실행합니다.\n"
        "Guard 정책 평가 후 ALLOW인 경우에만 결제가 진행됩니다."
    ),
)
async def pay_agent(
    payment_request_id: str = Path(..., description="estimate에서 받은 Payment Request ID"),
) -> PayResponse:
    try:
        return await _agent_service.pay(payment_request_id)
    except GuardApiError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail)
```

**포인트:**
- `HTTPException` : FastAPI에서 에러를 HTTP 응답으로 변환하는 방법
- `Path(...)` : URL 경로 파라미터 명시 (생략 가능하지만 description 달 때 씀)

---

## 6. 테스트 시나리오

서버 실행: `python run.py` (또는 `uvicorn app.main:app --reload`)

### 시나리오 1 — 정상 허용

```bash
# Step 1: 예측
curl -X POST http://localhost:8000/agent/estimate \
  -H "Content-Type: application/json" \
  -d '{
    "intent_id": "intent-allow",
    "resource": "/premium/weather/seoul",
    "reason": "서울 날씨 데이터 수집"
  }'

# 응답에서 payment_request_id 확인
# → "payment_request_id": "mock-payreq-xxxxxxxx"

# Step 2: 결제
curl -X POST http://localhost:8000/agent/pay/mock-payreq-xxxxxxxx
```

### 시나리오 2 — 예산 초과 (결제 안 해도 됨)

```bash
curl -X POST http://localhost:8000/agent/estimate \
  -H "Content-Type: application/json" \
  -d '{
    "intent_id": "intent-budget-exceeded",
    "resource": "/premium/weather/seoul",
    "reason": "날씨 데이터 수집"
  }'

# 응답: preliminary_decision = "DENY"
# → pay() 호출 필요 없음 (차단됨)
```

### 시나리오 3 — 승인 필요

```bash
curl -X POST http://localhost:8000/agent/estimate \
  -H "Content-Type: application/json" \
  -d '{
    "intent_id": "intent-approval",
    "resource": "/premium/company/naver",
    "reason": "기업 데이터 분석"
  }'

# 응답: preliminary_decision = "REQUIRE_APPROVAL"
# → 사람이 승인 후 pay() 호출
```

---

## 7. 사용 가능한 Mock 데이터

### Intent ID 목록

| intent_id | 설명 | 결과 |
|---|---|---|
| `intent-allow` | 정상 허용 시나리오 | ALLOW |
| `intent-approval` | 금액이 승인 임계값 초과 | REQUIRE_APPROVAL |
| `intent-budget-exceeded` | 예산 거의 소진 | DENY |
| `intent-blocked` | 차단된 merchant | DENY |

### Resource(리소스) 목록

| resource | merchant | 비용 |
|---|---|---|
| `/premium/weather/seoul` | weather-api.local | $0.10 |
| `/premium/weather/busan` | weather-api.local | $0.10 |
| `/premium/company/naver` | company-data.local | $1.50 |
| `/premium/news/latest` | news-api.local | $0.30 |
| `/premium/image/generate` | image-api.local | $0.50 |

---

## 8. Swagger UI에서 직접 테스트

서버 실행 후 브라우저에서 `http://localhost:8000/docs` 접속하면  
FastAPI가 자동 생성한 API 문서에서 버튼 클릭으로 테스트할 수 있습니다.

---

## 9. 구현 순서 요약

```
1. schemas.py    → EstimateResponse, PayResponse 클래스 추가
2. agent_service.py → estimate(), pay() 메서드 작성
3. agent.py      → /estimate, /pay/{id} 엔드포인트 추가
4. 서버 실행 후 /docs 에서 테스트
```
