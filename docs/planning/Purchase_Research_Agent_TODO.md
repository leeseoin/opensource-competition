# Purchase Research Agent 구현 계획

작성일: 2026-07-13
상태: in progress

## 체크박스 관리 규칙

- `[ ]`: 시작 전 또는 미완료. 작업 중이면 항목 끝에 `**(진행 중)**`을 추가한다.
- `[x]`: 구현, 관련 테스트, 문서 또는 계약 갱신이 끝나고 검증까지 통과한 상태다.
- 완료 항목의 코드 위치와 검증 결과는 [개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md)에 기록한다.
- 문제가 발생하면 원인과 해결 방법을 개발 진행 관리 문서의 문제 기록에 남긴다.

## Phase 0: 구조와 계약

- [x] 이전 프로젝트 구성요소 제거
- [x] Go Collector / Python Backend / Codex Plugin / Next.js 책임 분리
- [x] 하이브리드 아키텍처 문서 작성
- [x] 저장소 기본 디렉토리 구조 반영
- [x] 1차 대상 판매처를 ABC마트와 무신사로 제한
- [x] 검색 요청 JSON Schema 초안과 정상 예제 작성
- [x] 수집 결과 JSON Schema 초안과 성공·부분 성공·무효 예제 작성
- [x] 재검증 결과 JSON Schema 초안과 변경 예제 작성
- [x] 판매처 공통 수집 데이터 v1 초안 문서 작성
- [x] Go Collector 판매처 원본→공통 Product 변환 동작 문서 작성
- [ ] 첫 판매처 선정과 공개 접근 범위 확인 **(진행 중: ABC마트 검색·robots 확인, 상세·리뷰 확인 필요)**
- [ ] Go `CollectorResult` JSON schema 확정 **(공통 수집 데이터 명세의 검색 조건·가격·페이지·재고 상태 반영 필요)**
- [ ] 상품 상세 수집 요청 Schema 작성
- [ ] 리뷰 수집 요청과 pagination Schema 작성
- [ ] 재검증 요청 Schema 작성
- [ ] 저장된 예제를 이용한 JSON Schema 자동 검증 명령 추가
- [ ] Go 응답이 `collector-result.schema.json`을 통과하는 contract test 추가
- [ ] Python domain model과 Go transport DTO 매핑 확정
- [ ] Python Pydantic model이 같은 정상·무효 예제를 검증하는 contract test 추가
- [ ] Schema, Go DTO, Python model 변경을 함께 검사하는 CI 추가
- [ ] v1 필수 필드, 실패 상태, 호환성 정책 최종 검토

완료 기준: 실제 Go 응답과 Python model이 같은 v1 Schema 및 예제로 검증되고, 두 서비스의 책임과 실패 상태를 설명할 수 있다.

## Phase 1: Go Collector 기반

- [x] Go module 구성
- [x] Collector configuration 구성과 단위 테스트
- [x] internal HTTP router와 health endpoint
- [x] HTTP server lifecycle과 graceful shutdown 테스트
- [x] 기존 Go type/function의 한국어 주석 규칙 정비
- [ ] 도메인 allowlist와 URL 검증
- [ ] 공통 HTTP client, timeout, retry, rate limiter **(부분 구현: ABC마트 timeout·응답 제한·요청 간격 적용)**
- [ ] collector error/status 계약 **(부분 구현: blocked·unsupported·temporarily_unavailable 상태 사용)**
- [x] 실제 판매처 HTML/JSON fixture test 기반

완료 기준: fixture 판매처를 대상으로 search/product/reviews/verify 응답을 반환한다.

## Phase 2: 실제 판매처 한 곳

- [x] ABC마트 검색 Adapter
- [x] ABC마트 검색을 `result-total/list` JSON 방식으로 전환
- [x] ABC마트·29CM `totalCount`, `hasNext` 공통 응답 매핑
- [x] ABC마트 요청 최소 1초 간격 제한
- [x] 판매처 Registry로 ABC마트 고정 분기 제거
- [x] 29CM `robots.txt`의 공개 검색·상품 경로 허용 범위 확인
- [x] 29CM 공개 검색 화면의 상품 응답 구조 확인
- [x] 29CM 상품 기본정보 Searcher와 fixture 단위 테스트
- [x] 29CM opt-in live smoke test
- [x] 무신사 현재 robots 정책과 일반 Collector 차단 범위 확인
- [x] 무신사 공개 검색 HTML의 서버 렌더링 JSON 구조 확인
- [x] 무신사 상품 기본정보 Searcher와 opt-in live smoke test
- [ ] 무신사 공식 상품 API·MCP·제휴 Feed 또는 별도 허가 확보 **(보류)**
- [ ] 무신사 장기 운영 수집 범위와 요청 빈도 확정 **(보류)**
- [ ] 상품 상세·가격·배송 Adapter
- [ ] 옵션·재고·사이즈표 Adapter
- [ ] 공개 리뷰와 사진 여부 Adapter
- [ ] 29CM·ABC마트 리뷰 상품 작업 큐와 제한된 고루틴 Worker Pool
- [ ] partial/blocked/unsupported 처리 **(부분 구현: 미등록 판매처 unsupported, 원격 오류 temporarily_unavailable)**
- [x] ABC마트 검색 opt-in live smoke test
- [x] 무신사 검색 opt-in live smoke test

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

## Phase 5: Next.js Codex Gateway, MCP와 Codex Plugin

- [ ] Next.js server 전용 Codex Gateway
- [ ] Codex process/app-server 실행과 stream 중계
- [ ] 대화 session과 timeout·취소 처리
- [ ] Python MCP SDK와 stdio server
- [ ] `search_products`
- [ ] `collect_product`
- [ ] `collect_reviews`
- [ ] `compare_products`
- [ ] `verify_offer`
- [ ] `get_evidence`
- [ ] Plugin skill의 질문·근거·재검증 workflow 연결
- [ ] Plugin validation과 로컬 설치 검증
- [ ] 장기 서비스용 OpenAI API Agent 교체 경계 정의

완료 기준: Codex에서 “면접용 구두” 요청을 실제 수집부터 재검증까지 수행한다.

## Phase 6: Next.js Web

- [ ] Next.js + React + TypeScript 구조
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
- [ ] 판매처별 HTML fixture 변경 비교 자동화
- [ ] live smoke에서 필수 field 누락과 DOM 변경 자동 감지
- [ ] JSON-LD·공개 JSON 우선 추출과 HTML selector fallback
- [ ] parser 수정안 자동 생성과 회귀 테스트 실행
- [ ] 자동 수정안의 사람 승인·배포 절차
- [ ] 주문·결제 지원 범위 재검토

## Phase 8: 여러 AI 실행 환경 지원

- [ ] 공통 `AI Runtime Adapter` 계약 정의
- [ ] Codex CLI 연결부 구현과 계약 테스트
- [ ] Claude Code 연결부 또는 MCP 호환 흐름 검증
- [ ] Codex·Claude Code 공통 규칙과 명령어 원본 위치 결정
- [ ] Rulesync와 AgentSync 최소 예제 비교
- [ ] 선택한 설정 동기화 도구의 CI 검증 추가
- [ ] Ollama REST API 연결부
- [ ] llama.cpp OpenAI 호환 API 연결부
- [ ] GPU 모델 서버 연결부
- [ ] 모델별 한국어 이해와 MCP 기능 선택 평가
- [ ] 모델별 근거 왜곡·누락과 JSON 계약 평가
- [ ] 응답 속도·동시 사용자·GPU 메모리·운영 비용 비교
- [ ] 모델 및 실행 도구 라이선스 점검

완료 기준: 같은 Next.js 화면과 MCP 기능을 유지한 채 설정만 바꿔 Codex, Claude Code, 로컬 모델, GPU 서버 중 지원 대상 환경을 선택해 실행할 수 있다.

## 제출 준비: 대회 규정 대응

이 영역은 현재 기능 개발을 막는 선행 작업이 아니다. 제출 시점의 라이선스, 외부 구성요소, AI 사용, 판매처 정책과 참가 자격은 [오픈소스 개발자대회 규정 대응 체크리스트](오픈소스_개발자대회_규정_대응_체크리스트.md)에서 별도로 관리한다.

## 구현 우선순위

```text
계약
→ Go fixture collector
→ 실제 판매처 한 곳
→ Python DB 적재
→ 리뷰 분석·비교
→ MCP/Codex
→ Next.js
→ 여러 AI 실행 환경
```
