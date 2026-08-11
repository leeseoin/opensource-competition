# MCP Server

Codex, Claude Code 및 이후 로컬 LLM이 구매 조사 기능을 같은 방식으로 호출하도록
제공하는 독립 MCP 서버다.

## 현재 상태

TypeScript와 공식 `@modelcontextprotocol/sdk` 기반 stdio server를 구현했다. 현재 도구는
다음 아홉 개다.

- `create_research_session`: AI 구매 조건을 DRAFT로 저장
- `confirm_purchase_conditions`: 사용자가 확인한 조건을 CONFIRMED로 전환
- `search_product_candidates`: 확인된 세션의 PostgreSQL 후보 최대 5개 조회
- `get_product`: 상품과 최신 가격/재고/옵션 및 최신성 조회
- `get_evidence`: 상품 사실의 공개 출처와 수집 근거 조회
- `compare_products`: 상품 2개에서 5개의 가격/재고/판매처/최신성 비교
- `request_collection`: DB 결과가 없거나 오래된 검색 조건의 수집 요청
- `verify_offer`: 선택 상품의 우선순위 재검증 요청
- `get_verification_status`: 재검증 작업과 변경 항목 조회

`request_collection`은 기본 24시간 최신성 기준을 사용하며 데이터가 최신이면 수집하지
않는다. `verify_offer`는 현재 검색 수집 계약으로 상품명을 우선 재검색하는
`PRIORITY_SEARCH_REFRESH` 전략을 사용한다. 검색 결과에서 동일 상품이 확인되지 않으면
정확한 상세 검증으로 추측하지 않고 `NOT_FOUND`를 반환한다.

```bash
cd services/mcp-server
npm install
npm test
npm run build
npm start
```

`PRODUCT_BACKEND_BASE_URL`의 기본값은 `http://127.0.0.1:8080`이다. stdout은 MCP stdio
protocol에만 사용하며 일반 로그를 출력하지 않는다.

## 책임

- MCP tool 입력과 출력 검증
- Product Backend 내부 REST API 호출
- 상품 검색, 비교, 수집 요청, 작업 상태 및 구매 전 재검증 도구 제공
- 모델에 구조화된 상품 사실과 근거 전달

## 하지 않는 일

- PostgreSQL 직접 접근
- RabbitMQ 직접 접근
- 판매처 직접 접근
- 상품 데이터 최종 저장

Product Backend가 제공하지 않은 상품 사실을 만들지 않는다.
