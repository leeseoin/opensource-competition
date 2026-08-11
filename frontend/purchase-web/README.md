# Purchase Web

Purchase Research Agent의 Next.js 사용자 화면이다.

Figma V2 기반 랜딩, `/chat` 구매 질문과 `/compare` 상품 비교 화면을 제공한다.
`/chat`은 Next.js server Agent Gateway에서 Codex CLI와 Purchase Research Plugin 규칙으로
질문을 구조화한다. 사용자가 조건을 확인하기 전에는 상품을 검색하지 않으며, 확인 후에는
공식 MCP client가 stdio MCP Server를 거쳐 Spring Boot Product Backend의 PostgreSQL 후보를
최대 5개 상품군으로 조회한다. browser에는 Codex 인증정보와 내부 server 주소를 노출하지 않는다.
`/admin/collections`는 Product Backend의 `/internal/v1/dashboard/summary` 집계를 최근 24시간
기준으로 job/작업 상태, 판매처별 수집 상품 수, JSON/HTML 검증 일치율 카드로 보여준다.

## Getting Started

저장소 루트에서는 다음 명령으로 실행한다.

```bash
make web-dev
make web-dev WEB_PORT=2500
```

이 디렉토리에서 직접 실행할 수도 있다.

```bash
npm ci
npm run dev
```

기본 주소는 [http://localhost:3000](http://localhost:3000)이다. 화면 진입점은
`app/page.tsx`다. DB 상품을 확인하려면 PostgreSQL과 Product Backend를 먼저 실행하고
로컬 계정에서 Codex CLI 로그인을 완료해야 한다.

```bash
make infra-up
make product-backend-run
make web-dev WEB_PORT=2500
```

`make web-dev`는 stdio MCP Server를 먼저 빌드한다. Next.js server가 사용할 Product Backend
주소, Codex CLI 경로와 timeout은 루트 `.env`의 `PRODUCT_BACKEND_BASE_URL`,
`CODEX_CLI_PATH`, `CODEX_GATEWAY_TIMEOUT_MS`로 변경할 수 있다. 이 값에는 `NEXT_PUBLIC_`
접두사를 붙이지 않는다.

## 검증

```bash
npm run lint
npm test
npm run build
```

실제 E2E에서는 `/chat`에서 Codex를 선택하고 질문을 보낸 뒤 AI 조건 카드를 수정하거나
확인한다. `이 조건으로 검색`을 누른 뒤에만 MCP와 PostgreSQL 검색 단계가 실행된다.
