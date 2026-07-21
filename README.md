# Purchase Research Agent

사용자의 자연어 구매 요청을 구체화하고, 실제 판매처의 공개 상품·리뷰 정보를 수집해 근거 기반으로 비교한 뒤 선택 상품을 다시 검증하는 구매 조사 Agent PoC이다.

현재 상태는 **아키텍처 설계와 공통 계약 초안 작성 완료, ABC마트·29CM 상품 검색 Collector 구현 완료, 무신사 확장은 보류, 나머지 기능 개발 예정**이다.

## 핵심 구성

```text
Next.js Web           최종 사용자가 보는 구매 조사 챗봇 화면
Codex Plugin          구매 질문 처리 순서와 MCP 도구 사용 방법
Go Collector          판매처 검색·상세·옵션·리뷰 수집과 접근 통제
Python Backend        MCP·FastAPI·정규화·DB 적재·리뷰 분석·상품 비교
Contracts             Go와 Python이 주고받는 JSON 데이터 규격
PostgreSQL            상품·offer·review signal·snapshot·evidence 저장
```

## 목표 흐름

```text
구매 요청
→ 조건 구체화
→ Python MCP/API가 조사 작업 생성
→ Go Collector가 실제 판매처 수집
→ Python이 정규화·분석·저장
→ 근거가 연결된 상품 비교
→ 선택 상품 실시간 재수집·변경 검증
```

## Repository

```text
apps/                             # 최종 사용자가 보는 Next.js 화면(planned)
plugins/                          # Codex가 MCP 기능을 사용하는 방법
contracts/                        # Go와 Python 사이의 JSON 요청·응답 규격
services/                         # 실제 기능을 실행하는 Go·Python 서버
docs/                             # 시스템 구조, 구현 계획, 개발 기록
├── architecture/                 # 최신 시스템 구조와 데이터 수집 설계
├── planning/                     # 구현 TODO와 제출 전 체크리스트
├── development/                  # 구현 위치, 검증 결과, 문제 해결 기록
└── reports/                      # 날짜별 협업·업무 기록
```

개발 전에는 [시스템 구조](docs/architecture/Purchase_Research_Agent_시스템_구조.md)를 먼저 보고, 다음 작업은 [구현 TODO](docs/planning/Purchase_Research_Agent_TODO.md)에서 확인한다. 대회 라이선스와 제출 조건은 당장 개발을 막지 않고 [대회 규정 대응 체크리스트](docs/planning/오픈소스_개발자대회_규정_대응_체크리스트.md)에서 별도로 관리한다.
