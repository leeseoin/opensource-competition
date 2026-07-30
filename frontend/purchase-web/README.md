# Purchase Web

Purchase Research Agent의 Next.js 사용자 화면이다.

현재는 `create-next-app` 기본 scaffold 상태다. 이후 `/chat` 구매 채팅 화면과
`/admin/collections` 수집 관리 화면을 구현한다. browser는 Go Collector나
PostgreSQL을 직접 호출하지 않고 Next.js server route를 통해 Agent Gateway 또는
Spring Boot Product Backend와 통신한다.

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
`app/page.tsx`다.

## 검증

```bash
npm run lint
npm run build
```
