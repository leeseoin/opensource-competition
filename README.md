# Purchase Research Agent

사용자의 자연어 구매 요청을 구체화하고, 실제 판매처의 공개 상품·리뷰 정보를 수집해 근거 기반으로 비교한 뒤 선택 상품을 다시 검증하는 구매 조사 Agent PoC이다.

현재 상태는 **하이브리드 아키텍처 설계 완료, 기능 구현 예정**이다.

## 핵심 구성

```text
Codex Plugin          사용자 대화, 조건 구체화, 도구 선택, 근거 설명
Go Collector          판매처 검색·상세·옵션·리뷰 수집과 접근 통제
Python Backend        MCP·FastAPI·정규화·DB 적재·리뷰 분석·상품 비교
React Web             대화, 수집 진행률, 상품 비교, 근거, 재검증 화면
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
services/collector/               # Go 수집 서비스
services/research-backend/        # Python MCP·API·DB·분석 서비스
apps/purchase-web/                # React 화면(planned)
plugins/purchase-research-agent/  # Codex plugin
docs/                             # 아키텍처와 구현 계획
```

자세한 책임과 데이터 흐름은 [시스템 구조](docs/architecture/Purchase_Research_Agent_시스템_구조.md)를 참고한다.
