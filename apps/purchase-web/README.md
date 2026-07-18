# Purchase Web

구매 조건 대화, 수집 진행 상태, 상품 비교, 근거, 재검증 결과를 제공할 Next.js + React + TypeScript 서비스다.

최종 사용자는 이 챗봇 화면만 사용한다. PoC에서는 질문을 Next.js server의 Codex Gateway가 Codex로 전달하고, Codex가 Plugin과 MCP를 통해 Python Research Backend의 구매 조사 기능을 호출한다.

현재는 planned 상태다. Codex 인증정보와 실행 권한, 내부 MCP·Collector 주소는 browser에 노출하지 않는다. 장기 서비스에서는 Codex Gateway를 일반 OpenAI API Agent로 교체할 수 있도록 UI와 구매 조사 use case를 분리한다.
