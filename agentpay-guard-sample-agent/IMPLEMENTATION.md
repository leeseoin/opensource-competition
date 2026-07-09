# agentpay-guard-sample-agent 구현 내용

작성일: 2026-07-05
상태: `structure.md` 스캐폴딩 기반 FastAPI 서버 1차 구현 완료

## 개요

`structure.md`에 정의된 디렉토리 구조를 기준으로 비어있던 파일들을 채워 넣어, 실제로
`uvicorn app.main:app` 으로 기동 가능한 FastAPI 서버로 만들었다.

흐름: 사용자 프롬프트 입력 → LLM Router가 모델/예상 비용 결정 → Guard API에 예산·정책 승인 요청
→ `ALLOW` / `REQUIRE_APPROVAL` / `DENY` 분기 → `ALLOW`인 경우에만 Anthropic Claude 호출.

## 파일별 구현 내용

### app/core

- `config.py` — `pydantic-settings` 기반 `Settings`. `.env`에서 Guard URL, Anthropic API
  key/모델명, agent 기본값, 선택적 `AGENT_API_KEY`를 읽는다. `get_settings()`는
  `lru_cache`로 싱글턴처럼 사용.
- `exceptions.py` — `GuardUnavailableError`(Guard 연결 실패 → 503), `AIProviderError`
  (Anthropic 호출 실패 → 502)와 FastAPI 예외 핸들러 등록 함수.
- `security.py` — `verify_api_key` 의존성. `AGENT_API_KEY`가 설정되지 않으면 인증을
  건너뛴다(PoC 기본값), 설정되어 있으면 `X-API-Key` 헤더를 검사한다.

### app/models

- `request.py` — `AgentInvokeRequest(prompt, agent_id?, estimated_cost?)`
- `response.py` — `GuardDecision(decision, reason_code?, reason_message?)`,
  `AgentInvokeResponse(status, model_used?, answer?, estimated_cost, guard_decision)`

### app/services

- `llm_router.py` — 프롬프트 길이/키워드(분석·요약·전략·코드 리뷰·설계)로 default/premium
  모델과 티어를 정하고, 대략적인 예상 비용을 계산한다(문자 수 기반 근사치).
- `agent_logic.py` — `handle_invoke()`가 전체 흐름을 조율한다.
  1. `llm_router.route()`로 라우팅 결정
  2. `GuardAPIClient.validate_request()` 호출 → 네트워크/HTTP 에러는
     `GuardUnavailableError`로 변환
  3. `decision == DENY` → `status="denied"`로 즉시 반환 (AI 호출 없음)
  4. `decision == REQUIRE_APPROVAL` → `status="pending_approval"`로 즉시 반환
  5. `decision == ALLOW` → `AnthropicClient.generate()` 호출, 실패 시
     `AIProviderError`로 변환

### app/clients

- `base_client.py` — 기존 구현 유지 (httpx 세션, 공통 에러 처리).
- `guard_api_client.py` — 기존 구현 유지. `POST /api/v1/guard/validate`로
  `{agentId, intent, estimatedCost}` 전송.
- `openai_client.py` → **`anthropic_client.py`로 교체**. `.env`와 `requirements.txt`가
  이미 OpenAI가 아닌 Anthropic(`anthropic` 패키지, `ANTHROPIC_API_KEY`)을 쓰도록 되어
  있었기 때문에 실제 설정에 맞춰 이름과 구현을 정리했다. Anthropic 공식 SDK
  (`AsyncAnthropic`)가 자체적으로 재시도/타임아웃을 처리하므로 `BaseClient`는
  상속하지 않는다.

### app/api

- `dependencies.py` — `get_app_settings`, `get_guard_client`, `get_ai_client`
  (`request.app.state`에서 싱글턴 클라이언트를 꺼내온다).
- `endpoints/health.py` — `GET /health` → `{"status": "ok"}`
- `endpoints/agent.py` — `POST /api/v1/agent/invoke` (`verify_api_key` 의존성 적용)

### app/main.py

- `lifespan`에서 `GuardAPIClient`, `AnthropicClient`를 생성해 `app.state`에 저장하고,
  종료 시 각각 `close()` 호출.
- 예외 핸들러 등록(`register_exception_handlers`).
- 라우터 연결: `/health`, `/api/v1/agent`.
- **버그 수정**: 기존 코드가 lifespan에서 🚀 이모지를 `print()`했는데, Windows 콘솔
  기본 코드페이지(cp949)에서 `UnicodeEncodeError`로 서버 기동이 죽는 문제가 있었다.
  파일 상단에서 stdout/stderr를 UTF-8로 `reconfigure`하도록 수정.

### 기타

- `.gitignore` 신규 생성 (`__pycache__/`, `.env`, `venv/` 등).
- `.env` 신규 생성 (`.env.example` 값 그대로, git에는 커밋되지 않음).
- `.env.example` — `ANTHROPIC_MODEL_DEFAULT`, `ANTHROPIC_MODEL_PREMIUM`,
  `AGENT_API_KEY` 항목 추가.
- `structure.md` — `openai_client.py` → `anthropic_client.py`, `endpoints/health.py`,
  `endpoints/agent.py` 반영.

## 실행 방법

```bash
pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8001
```

- `GET /` — 헬스 메시지
- `GET /health` — 헬스 체크
- `POST /api/v1/agent/invoke` — body 예시:
  ```json
  { "prompt": "hello" }
  ```

## 검증 완료 사항

venv에 의존성을 설치하고 서버를 직접 기동해 확인함:

- `GET /`, `GET /health` 정상 응답
- `GET /docs`, `GET /openapi.json` 정상 (스키마 유효)
- Guard 서버가 없는 상태에서 `POST /api/v1/agent/invoke` 호출 시, 크래시 없이
  `503 {"status":"error","message":"AgentPay Guard 서버에 연결할 수 없습니다: ..."}`
  반환 확인

## 알려진 제약 / 다음 단계

- `agentpay-guard-api-server`(Spring Boot)에 `/api/v1/guard/validate` 등 실제 엔드포인트가
  아직 구현되어 있지 않다. 지금은 Guard가 꺼져 있으면 항상 503으로 우아하게 실패하는
  것까지만 확인 가능하다.
- `ANTHROPIC_API_KEY`를 실제 키로 채우지 않으면 `ALLOW` 이후 Anthropic 호출 단계에서
  502(`AIProviderError`)가 발생한다.
- `README.md`의 "CLI로 재현" 계획(Mock Merchant 402 quote, payment request 생성 등)은
  이번 구현 범위에 포함되지 않았다. 현재는 `structure.md` 기준 FastAPI + LLM
  router + Guard 예산 승인 흐름만 구현된 상태이며, CLI/결제 플로우는 별도 작업이 필요하다.
