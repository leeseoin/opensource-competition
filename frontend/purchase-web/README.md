# Purchase Web

Purchase Research Agent의 Next.js 사용자 화면이다.

Figma V2 기반 랜딩, `/chat` 구매 질문과 `/compare` 상품 비교 화면을 제공한다.
`/chat`과 `/compare`는 Next.js server route를 통해 Spring Boot Product Backend의
PostgreSQL 상품 후보를 조회한다. browser는 Go Collector, PostgreSQL 또는 Product
Backend 내부 주소를 직접 호출하지 않는다. Agent Gateway와 `/admin/collections`는
아직 구현 전이다.

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
`app/page.tsx`다. DB 상품을 확인하려면 PostgreSQL과 Product Backend를 먼저 실행한다.

```bash
make infra-up
make product-backend-run
make web-dev WEB_PORT=2500
```

Next.js server가 사용할 Product Backend 주소와 timeout은 루트 `.env`의
`PRODUCT_BACKEND_BASE_URL`, `PRODUCT_BACKEND_REQUEST_TIMEOUT_MS`로 변경할 수 있다.
두 값에는 `NEXT_PUBLIC_` 접두사를 붙이지 않는다.

## 검증

```bash
npm run lint
npm test
npm run build
```
