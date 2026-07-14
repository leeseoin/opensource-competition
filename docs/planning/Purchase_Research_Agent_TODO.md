# Purchase Research Agent 구현 계획

작성일: 2026-07-13
상태: planned

## Phase 0: 구조와 계약

- [x] 이전 프로젝트 구성요소 제거
- [x] Go Collector / Python Backend / Codex Plugin / React 책임 분리
- [x] 하이브리드 아키텍처 문서 작성
- [x] 저장소 기본 디렉토리 구조 반영
- [ ] 첫 판매처 선정과 공개 접근 범위 확인
- [ ] Go `CollectorResult` JSON schema 확정
- [ ] Python domain model과 Go transport DTO 매핑 확정

완료 기준: 예제 요청·응답만으로 두 서비스의 책임과 실패 상태를 설명할 수 있다.

## Phase 1: Go Collector 기반

- [ ] Go module과 configuration 구성
- [ ] internal HTTP server와 health endpoint
- [ ] 도메인 allowlist와 URL 검증
- [ ] 공통 HTTP client, timeout, retry, rate limiter
- [ ] collector error/status 계약
- [ ] HTML/JSON fixture test 기반

완료 기준: fixture 판매처를 대상으로 search/product/reviews/verify 응답을 반환한다.

## Phase 2: 실제 판매처 한 곳

- [ ] 검색 Adapter
- [ ] 상품 상세·가격·배송 Adapter
- [ ] 옵션·재고·사이즈표 Adapter
- [ ] 공개 리뷰와 사진 여부 Adapter
- [ ] partial/blocked/unsupported 처리
- [ ] opt-in live smoke test

완료 기준: 실제 공개 상품 후보를 찾아 출처와 수집 시각을 포함한 결과를 반환한다.

## Phase 3: Python Backend와 DB

- [ ] Python package와 설정 재정비
- [ ] Go Collector client
- [ ] Pydantic transport schema validation
- [ ] PostgreSQL compose와 migration 도구
- [ ] product/offer/option/snapshot/evidence repository
- [ ] 중복 수집과 transaction 정책
- [ ] 조사 세션과 작업 상태

완료 기준: Go 수집 결과가 검증·정규화되어 PostgreSQL에 재현 가능하게 저장된다.

## Phase 4: 리뷰 분석과 비교

- [ ] 개인정보 제거와 최소 저장 정책
- [ ] 규칙 기반 size/foot-width/fit signal 추출
- [ ] 선택적 LLM structured extraction
- [ ] confidence와 derived 표시
- [ ] 필수 조건 filter
- [ ] 설명 가능한 가중치 점수
- [ ] 주장과 evidence 연결

완료 기준: 후보 3개를 점수 구성, 근거, 주의사항과 함께 비교한다.

## Phase 5: MCP와 Codex Plugin

- [ ] Python MCP SDK와 stdio server
- [ ] `search_products`
- [ ] `collect_product`
- [ ] `collect_reviews`
- [ ] `compare_products`
- [ ] `verify_offer`
- [ ] `get_evidence`
- [ ] Plugin skill의 질문·근거·재검증 workflow 연결
- [ ] Plugin validation과 로컬 설치 검증

완료 기준: Codex에서 “면접용 구두” 요청을 실제 수집부터 재검증까지 수행한다.

## Phase 6: React Web

- [ ] Vite + React + TypeScript 구조
- [ ] 구매 조건 대화와 profile panel
- [ ] FastAPI research session API
- [ ] SSE 수집 진행 상태
- [ ] 상품 비교와 evidence panel
- [ ] 선택 상품 verification 화면
- [ ] API/E2E test

완료 기준: 브라우저에서 Codex와 같은 application 흐름을 수행한다.

## Phase 7: 확장

- [ ] 두 번째 판매처 Adapter
- [ ] 동일 상품 매칭
- [ ] 사용자 치수·선호 프로필
- [ ] 검색·수집 캐시 정책
- [ ] 주문·결제 지원 범위 재검토

## 구현 우선순위

```text
계약
→ Go fixture collector
→ 실제 판매처 한 곳
→ Python DB 적재
→ 리뷰 분석·비교
→ MCP/Codex
→ React
```
