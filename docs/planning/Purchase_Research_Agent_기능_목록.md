# Purchase Research Agent 기능 목록

작성일: 2026-07-31
최종 점검일: 2026-08-04
최종 점검 범위: `COLLECTOR-006` JSON/HTML 교차 검증 구현 감사
상태: 선택 기능 구현 완료/검증 필요

## 목적

이 문서는 아키텍처에 정의된 기능을 안정적인 기능 ID로 관리한다. 기능의 세부 구현
작업은 [구현 TODO](Purchase_Research_Agent_TODO.md), 실제 코드 근거와 현재 완성도는
[개발 진행 관리](../development/Purchase_Research_Agent_개발_진행_관리.md), 완료된
변경 이력은 [코드트래커](../reports/코드트래커/INDEX.md)에서 관리한다.

## 원본 아키텍처

- [시스템 구조](../architecture/Purchase_Research_Agent_시스템_구조.md)
- [Go Collector 데이터 변환](../architecture/Go_Collector_데이터_변환_동작_설명.md)
- [판매처 공통 수집 데이터 명세](../architecture/판매처_공통_수집_데이터_명세.md)
- [판매처 데이터 수집과 DB 적재 설계](../architecture/판매처_데이터_수집_DB_적재와_확장_설계.md)
- [현재 수집 데이터와 DB 저장 흐름](../architecture/현재_수집_데이터와_DB_저장_흐름.md)
- [Python/Go 크롤러 확장성과 성능 비교 설계](../architecture/Python_Go_크롤러_확장성과_성능_비교_설계.md)

## 관리 규칙

- 기능 ID는 발급 후 변경하거나 재사용하지 않는다.
- 구현 파일 하나가 아니라 검증 가능한 기능 결과를 기준으로 ID를 부여한다.
- 세부 작업은 기능 ID를 새로 만들지 않고 TODO 체크리스트로 나눈다.
- 아키텍처에서 제거된 기능도 삭제하지 않고 `보류`로 남겨 변경 이유를 추적한다.
- 상태는 `feature-progress`가 실제 코드와 테스트를 확인한 뒤 갱신한다.
- 초기 상태는 현재 문서와 코드의 1차 대조 결과이며 전체 감사 결과는 아니다.

## 상태 기준

| 상태 | 기준 |
|---|---|
| 계획 | 설계와 기능 목록만 존재 |
| 진행 중 | 코드 변경을 시작했지만 독립적인 정상 경로가 없음 |
| 부분 구현 | 정상 경로 일부는 동작하지만 필수 범위나 실패 처리 및 테스트가 남음 |
| 구현 완료/검증 필요 | 구현은 갖춰졌지만 계약, 통합, 실제 환경 또는 문서 검증이 남음 |
| 완료 | 완료 기준, 실패 경로, 테스트, 문서와 검증 결과를 모두 확인 |
| 차단 | 외부 정책, 권한, 합의 또는 환경 변화 없이는 진행할 수 없음 |
| 보류 | 팀 결정으로 현재 개발 대상에서 제외 |

## 우선순위 기준

| 우선순위 | 의미 |
|---|---|
| P0 | 현재 수직 흐름과 대회 PoC에 반드시 필요 |
| P1 | PoC 품질과 확장성에 중요 |
| P2 | 기본 수직 흐름 이후 확장 |

## 기능 목록

### 계약과 데이터

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `CONTRACT-001` | 검색 요청과 결과 공통 계약 | 부분 구현 | P0 | 검색 조건, 상품, 가격, 재고, 출처, 전체 개수와 다음 페이지 | JSON Schema, Go DTO와 Java DTO가 같은 정상/실패 예제로 자동 검증됨 | 공통 수집 데이터 명세 / Collector 변환 설명 | Go/Java 자동 계약 검증과 호환성 정책 확정 |
| `CONTRACT-002` | 상세/리뷰/재검증 계약 | 계획 | P1 | 상품 상세, 옵션, 리뷰 pagination과 현재 offer 재검증 | 각 operation의 요청/응답과 실패 상태를 Schema 및 예제로 검증함 | 공통 수집 데이터 명세 / 시스템 구조 | 상세/리뷰/재검증 Schema 작성 |

### Collector와 판매처

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `COLLECTOR-001` | Collector 공통 HTTP 실행 기반 | 부분 구현 | P0 | 설정, health, lifecycle, timeout, retry, 응답 제한과 오류 상태 | 외부 네트워크 없이 정상/실패/lifecycle 테스트가 통과하고 공통 제한 정책이 적용됨 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | 공통 retry, URL 안전성, 동시성 제한 |
| `COLLECTOR-002` | ABC마트 공개 상품 검색 | 완료 | P0 | 공개 JSON 검색, 상품 기본정보, 가격, 사이즈 재고, 전체 개수와 다음 페이지 | fixture 테스트와 낮은 빈도의 opt-in smoke test가 통과하고 출처가 포함됨 | Collector 변환 설명 / 데이터 수집과 DB 적재 설계 | 상세/리뷰 기능과 분리해 유지 |
| `COLLECTOR-003` | 29CM 공개 상품 검색 | 완료 | P0 | 공개 검색 응답의 상품 기본정보와 pagination 변환 | fixture 테스트와 낮은 빈도의 opt-in smoke test가 통과하고 출처가 포함됨 | Collector 변환 설명 / 공통 수집 데이터 명세 | 상세/리뷰 기능과 분리해 유지 |
| `COLLECTOR-004` | 상품 상세와 옵션 수집 | 계획 | P1 | 상세 설명, 가격, 배송, 옵션, 사이즈표와 재고 | ABC마트/29CM 상세 결과가 공통 계약으로 변환되고 fixture 테스트가 통과함 | 공통 수집 데이터 명세 / 데이터 수집과 DB 적재 설계 | 공개 상세 구조와 요청 예산 확인 |
| `COLLECTOR-005` | 공개 리뷰 수집 | 계획 | P1 | 리뷰 pagination, 평점, 본문, 구매 옵션과 사진 존재 여부 | 작성자 식별정보와 이미지를 저장하지 않고 제한된 요청으로 fixture 테스트가 통과함 | 공통 수집 데이터 명세 / 데이터 수집과 DB 적재 설계 | 리뷰 공개 범위와 pagination 구조 확인 |
| `COLLECTOR-006` | JSON/HTML 수집 결과 교차 검증 | 완료 | P1 | JSON을 기본 상품값으로 사용하고 ABC마트 렌더링 HTML 및 29CM 상세 JSON-LD와 전수 비교, 원본 파일과 검증 상태 저장 | 공통 계약/Go 구현/Spring 저장/fixture 테스트가 일치하고 ABC마트/29CM 실제 소량 검증에서 `MATCHED` 또는 설명 가능한 차이를 반환함 | 시스템 구조 / 공통 수집 데이터 명세 / 현재 DB 저장 흐름 | 판매처 구조 변화 감지와 원본 보존 정책은 별도 운영 기능으로 진행 |
| `MERCHANT-001` | 판매처 추가와 접근 안전성 | 부분 구현 | P1 | Registry, host allowlist, robots 확인, URL/DNS/redirect 검증과 판매처별 제한 | 새 Adapter가 공통 등록 절차로 추가되고 private network와 접근 제한 우회가 차단됨 | 시스템 구조 / Collector 변환 설명 | 공통 allowlist와 redirect 안전성 구현 |

### Queue와 Redis

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `QUEUE-001` | 검색 작업 계약과 Go Worker | 부분 구현 | P0 | RabbitMQ topology, 작업/결과 계약, Go 소비, retry와 DLQ | fixture 작업의 성공/실패/재시도/ACK/DLQ 통합 테스트가 통과함 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | ACK/DLQ 통합 테스트와 복구 검증 |
| `QUEUE-002` | Spring Boot 작업 발행과 결과 저장 | 완료 | P0 | Product Backend producer, 결과 consumer, 검증 실패 처리와 DB transaction 연결 | Product Backend가 작업을 발행하고 Go 결과를 검증해 PostgreSQL에 저장함 | 시스템 구조 / 현재 DB 저장 흐름 | ABC마트/29CM 실제 E2E와 job 상태 조회 완료 / 결과 중복 방지는 별도 기능으로 유지 |
| `QUEUE-003` | 여러 검색어와 페이지 수집 | 부분 구현 | P1 | batch, pagination, request budget, priority와 Worker 수 제한 | 여러 판매처 작업이 상한 안에서 분배되고 결과 수량과 실패가 보고됨 | 데이터 수집과 DB 적재 설계 / 현재 DB 저장 흐름 | 여러 검색어 batch와 작업 예산 구현 |
| `REDIS-001` | 속도 제한/중복 방지/짧은 상태 | 부분 구현 | P1 | 판매처 전체 limiter, 중복 key, 진행 상태와 cache | 여러 Worker에서도 요청 간격과 중복 차단이 일관되고 만료 정책 테스트가 통과함 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | application adapter와 key 정책 구현 |

### Product Backend와 PostgreSQL

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `BACKEND-001` | Collector 결과와 상품 snapshot 저장 | 부분 구현 | P0 | Flyway schema, Java DTO 검증, 요청별 검색어/filters, 상품 upsert, 가격/재고/옵션/근거 snapshot과 최신 상품 조회 | fixture 통합 테스트와 실제 ABC마트/29CM 결과 저장에서 상품 중복 없이 snapshot이 추가되고 수집 검색어로 조회됨 | 시스템 구조 / 현재 DB 저장 흐름 | 동시 최초 저장 충돌 처리 |
| `BACKEND-002` | 수집 작업 영구 상태 관리 | 부분 구현 | P0 | collection job/task, 상태 전이, 성공/실패 수량과 소요시간 | 장애 후에도 PostgreSQL 기준 최종 상태를 복구하고 Redis 상태와 일치함 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | 실제 RUNNING event, Redis 상태 일치와 장애 복구 검증 |

### MCP와 AI 실행

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `MCP-001` | Product Backend 상품 검색 MCP 도구 | 계획 | P0 | MCP stdio server, 상품 검색과 근거 조회 도구, REST API client | Codex/Claude Code가 MCP를 통해 DB 상품과 출처를 조회하고 계약 테스트가 통과함 | 시스템 구조 | MCP SDK와 언어 확정 |
| `MCP-002` | 구매 조사 Agent 실행 경계 | 부분 구현 | P1 | Codex Plugin workflow, 질문 구조화, 사용자 조건 확인, 도구 호출과 답변 근거 연결 | Plugin이 판매처나 DB를 직접 호출하지 않고 MCP 도구로 근거 있는 답변을 구성함 | 시스템 구조 | Codex CLI 구조화 출력과 MCP 도구 연결 |

### Next.js Web

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `WEB-001` | Next.js 공통 화면 기반 | 부분 구현 | P1 | routing, layout, theme, 환경변수와 서버 전용 인증정보 경계 | build/lint가 통과하고 browser에 비밀값이 노출되지 않음 | 시스템 구조 | Astryx 기반 공통 layout 확정 |
| `WEB-002` | 사용자 구매 채팅 화면 | 부분 구현 | P1 | 질문 입력, streaming 답변, 후보 비교, 근거와 재검증 표시 | 사용자가 질문부터 근거 확인까지 한 흐름으로 완료하고 E2E가 통과함 | 시스템 구조 | Agent Gateway와 MCP 연결 / 실제 브라우저와 접근성 검증 |
| `WEB-003` | 수집 관리 화면 | 계획 | P1 | 작업 등록, 상태, 실패 원인, 수량, 재시도와 중단 | 관리자가 수집 작업의 시작부터 종료까지 상태와 결과를 확인함 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | CollectionJob API 확정 |

### 분석과 재검증

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `ANALYSIS-001` | 리뷰 신호 추출과 상품 비교 | 계획 | P1 | 리뷰 신호, confidence, 점수, 근거와 주의사항 | 후보 비교 값이 공개 출처 또는 derived 표기와 confidence를 포함함 | 시스템 구조 / 공통 수집 데이터 명세 | 리뷰 저장 계약과 비교 규칙 |
| `VERIFY-001` | 구매 전 상품 재검증 | 계획 | P1 | 현재 가격/재고/옵션 재수집과 추천 snapshot 비교 | 변경 항목, 확인 불가 항목과 최신 출처를 분리해 반환함 | 시스템 구조 / 공통 수집 데이터 명세 | 재검증 요청/응답 계약 |

### 운영과 실행 환경

| 기능 ID | 기능명 | 상태 | 우선순위 | 범위 | 완료 기준 | 설계 근거 | 다음 작업 |
|---|---|---|---|---|---|---|---|
| `OPS-001` | 로컬 인프라와 루트 개발 명령 | 완료 | P0 | Compose PostgreSQL/Redis/RabbitMQ, 환경변수 예제와 Make 명령 | 루트 명령으로 인프라와 각 서비스를 실행/검증할 수 있음 | 시스템 구조 | 배포 환경 분리 전까지 유지 |
| `OPS-002` | CI/보안/관측 가능성 | 부분 구현 | P1 | 계약 검사, unit/integration, 문서 동기화, 로그, metric과 보안 점검 | PR에서 필수 검사가 자동 실행되고 운영 오류를 request/job ID로 추적함 | 시스템 구조 / 데이터 수집과 DB 적재 설계 | 계약 CI와 구조화 로그 |
| `OPS-003` | 기능 ID 기반 개발 추적 | 완료 | P1 | 기능 목록, 코드트래커, 진행상황 감사 스킬과 AGENTS 실행 규칙 | 세 스킬 형식 검증이 통과하고 하나의 기능 ID로 계획/변경/상태를 연결함 | [기능 ID 추적 프로세스](../development/기능_ID_기반_개발_추적_프로세스.md) | 새 기능 구현마다 코드트래커와 진행상황 감사를 같은 흐름으로 유지 |
| `OPS-004` | Python/Go 크롤러 확장성과 성능 비교 | 완료 | P1 | 동일 비교 Contract, 최대 10,000개 pagination/checkpoint 수집, parser 및 E2E benchmark | Python/Go가 같은 fixture 계약을 통과하고 판매처별 실제 수집량/중복/오류/시간/CPU/메모리 보고서를 재현함 | [Python/Go 크롤러 비교 설계](../architecture/Python_Go_크롤러_확장성과_성능_비교_설계.md) | 판매처 구조 변화 감지와 Redis 전역 rate limiter는 별도 기능으로 진행 |
| `RUNTIME-001` | 여러 AI 실행 환경 지원 | 계획 | P2 | Codex/Claude Code CLI, Ollama, llama.cpp와 GPU model server adapter | 동일 MCP 도구 계약을 유지하며 실행 환경을 설정으로 교체하고 평가 결과를 비교함 | 시스템 구조 | PoC 완료 후 runtime adapter 설계 |

## 다음 우선순위

1. `BACKEND-001` 실제 전체 Queue 경로 PostgreSQL 적재 E2E
2. `BACKEND-002` 수집 작업 영구 상태
3. `MCP-001` 상품 검색 MCP 도구
4. `WEB-002` 사용자 구매 채팅 화면
