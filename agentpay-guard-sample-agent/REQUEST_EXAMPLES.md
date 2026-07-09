# POST /api/v1/agent/invoke 요청 예시

## 사전 준비

두 서버를 각각 다른 터미널에서 띄운다.

```bash
# 1) sample-agent (agentpay-guard-sample-agent 폴더에서)
python -m uvicorn app.main:app --reload --port 8001

# 2) 임시 mock Guard 서버 (agentpay-guard-sample-agent 폴더에서, 실제 Spring Guard 대신)
python dev/mock_guard_server.py
```

- sample-agent: http://localhost:8001
- Swagger UI: http://localhost:8001/docs
- mock Guard: http://localhost:8080 (내부적으로만 호출됨, 직접 열어볼 필요 없음)

## 요청 스펙

```
POST http://localhost:8001/api/v1/agent/invoke
Content-Type: application/json
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `prompt` | O | AI에게 전달할 텍스트 |
| `agent_id` | X | 생략하면 `.env`의 `DEFAULT_AGENT_ID` 사용 |
| `estimated_cost` | X | 생략하면 프롬프트 길이 기반으로 자동 계산됨. 값을 직접 넣으면 그 값으로 Guard 판단을 강제할 수 있음 |

mock Guard의 판정 기준(`dev/mock_guard_server.py`):

```
estimated_cost >= 0.01        -> DENY
0.001 <= estimated_cost < 0.01 -> REQUIRE_APPROVAL
estimated_cost < 0.001        -> ALLOW
```

`estimated_cost`를 직접 지정하면 세 가지 케이스를 원하는 대로 재현할 수 있다.

요청 JSON 파일은 `examples/` 폴더에 미리 만들어 두었다 (`agentpay-guard-sample-agent` 폴더에서 실행하는 것 기준 경로).

## 예시 1. ALLOW (실제 Claude 응답까지 받음)

`examples/invoke_allow.json`:

```json
{
  "prompt": "오늘 날씨 알려줘",
  "agent_id": "agent-sample-001",
  "estimated_cost": 0.0005
}
```

```bash
curl -X POST http://localhost:8001/api/v1/agent/invoke \
  -H "Content-Type: application/json" \
  -d @examples/invoke_allow.json
```

예상 응답:

```json
{
  "status": "success",
  "model_used": "claude-haiku-4-5-20251001",
  "answer": "...(Claude가 생성한 실제 답변)...",
  "estimated_cost": 0.0005,
  "actual_cost": 0.0000312,
  "guard_decision": {
    "decision": "ALLOW",
    "reason_code": "WITHIN_BUDGET",
    "reason_message": "예산 범위 내 요청입니다."
  }
}
```

`actual_cost`는 Claude 응답의 실제 입력/출력 토큰 수 × 실제 요금표로 계산된 값이다 (`estimated_cost`는 호출 전 추정치, `actual_cost`는 호출 후 실측치).

## 예시 2. REQUIRE_APPROVAL (AI 호출 없이 승인 대기로 종료)

`examples/invoke_require_approval.json`:

```json
{
  "prompt": "이 문서를 분석해줘",
  "agent_id": "agent-sample-001",
  "estimated_cost": 0.005
}
```

```bash
curl -X POST http://localhost:8001/api/v1/agent/invoke \
  -H "Content-Type: application/json" \
  -d @examples/invoke_require_approval.json
```

예상 응답:

```json
{
  "status": "pending_approval",
  "model_used": null,
  "answer": null,
  "estimated_cost": 0.005,
  "actual_cost": null,
  "guard_decision": {
    "decision": "REQUIRE_APPROVAL",
    "reason_code": "APPROVAL_THRESHOLD",
    "reason_message": "예상 비용 0.005는 승인이 필요합니다."
  }
}
```

## 예시 3. DENY (AI 호출 없이 차단)

`examples/invoke_deny.json`:

```json
{
  "prompt": "대규모 데이터 분석 요청",
  "agent_id": "agent-sample-001",
  "estimated_cost": 0.05
}
```

```bash
curl -X POST http://localhost:8001/api/v1/agent/invoke \
  -H "Content-Type: application/json" \
  -d @examples/invoke_deny.json
```

예상 응답:

```json
{
  "status": "denied",
  "model_used": null,
  "answer": null,
  "estimated_cost": 0.05,
  "actual_cost": null,
  "guard_decision": {
    "decision": "DENY",
    "reason_code": "BUDGET_EXCEEDED",
    "reason_message": "예상 비용 0.05가 허용 한도를 초과했습니다."
  }
}
```

## Guard 서버가 안 떠 있을 때 (mock Guard도 안 켠 경우)

크래시 대신 아래처럼 503으로 응답한다 (정상 동작):

```json
{
  "status": "error",
  "message": "AgentPay Guard 서버에 연결할 수 없습니다: All connection attempts failed"
}
```

## Swagger UI에서 테스트하기

1. http://localhost:8001/docs 접속
2. "AI Agent" 태그 섹션을 펼치고 `POST /api/v1/agent/invoke` 클릭
3. "Try it out" 클릭 후 위 예시 중 하나의 JSON을 Request body에 붙여넣기
4. "Execute" 클릭 → 아래에서 실제 응답 확인

## 참고

- `.env`의 `AGENT_API_KEY`를 비워두면 인증 없이 호출 가능. 값을 채웠다면 요청에
  `-H "X-API-Key: <값>"` 헤더를 추가해야 한다.
- `dev/mock_guard_server.py`는 실제 `agentpay-guard-api-server`(Spring Boot)에
  `/api/v1/guard/validate`가 구현되면 더 이상 필요 없으니 지워도 된다.
