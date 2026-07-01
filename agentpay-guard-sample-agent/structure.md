````markdown
```text
agentpay-guard-sample-agent/
├── app/
│   ├── api/                 # 📍 Spring: Controller (엔드포인트 라우팅)
│   │   ├── endpoints/       # 실제 API 경로들 (예: agent.py, health.py)
│   │   └── dependencies.py  # 의존성 주입 (Spring의 @Autowired 역할 일부)
│   │
│   ├── core/                # 📍 Spring: Config, ExceptionHandler, Security
│   │   ├── config.py        # 환경변수(.env) 설정 및 로드 (pydantic-settings)
│   │   ├── exceptions.py    # 커스텀 예외 처리
│   │   └── security.py      # 인증/인가 로직 (토큰 검증 등)
│   │
│   ├── models/              # 📍 Spring: DTO (요청/응답 스키마)
│   │   ├── request.py       # 클라이언트로부터 받는 데이터 모델
│   │   └── response.py      # 클라이언트에게 주는 데이터 모델
│   │
│   ├── services/            # 📍 Spring: Service (핵심 비즈니스 로직)
│   │   ├── llm_router.py    # 어떤 AI 모델을 쓸지 판단하는 로직
│   │   └── agent_logic.py   # 사용자의 요청을 분석하고 프롬프트를 구성하는 로직
│   │
│   ├── clients/             # 📍 Spring: WebClient, RestTemplate, FeignClient
│   │   ├── base_client.py       # 💡 공통 핵심! (통신 세션, 에러 처리, 재시도 로직)
│   │   ├── guard_api_client.py  # BaseClient 상속: Java Guard 통신 전담
│   │   └── openai_client.py     # BaseClient 상속: 외부 AI API 통신 전담
│   │
│   └── main.py              # 📍 Spring: @SpringBootApplication (앱 진입점)
│
├── .env                     # 환경 변수 (API Key, Base URL 등)
├── .gitignore               # Git 업로드 제외 목록
└── requirements.txt         # 설치할 패키지 목록 (또는 pyproject.toml)
```
`