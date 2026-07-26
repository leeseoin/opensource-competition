# Purchase Research Agent 문서

## 문서 읽는 순서

1. [시스템 구조](architecture/Purchase_Research_Agent_시스템_구조.md): 개발 전 전체 구성, 데이터 흐름, 폴더별 책임을 확인하는 기준 문서
2. [Go Collector 데이터 변환 동작 설명](architecture/Go_Collector_데이터_변환_동작_설명.md): HTTP 요청부터 판매처 JSON 해석, 정규화, 공통 Product 변환, Contract 검사까지 실제 코드 실행 순서
3. [판매처 데이터 수집, DB 적재와 확장 설계](architecture/판매처_데이터_수집_DB_적재와_확장_설계.md): ABC마트·29CM 수집 방식, RabbitMQ 작업 Queue, Redis 상태·속도 제한, PostgreSQL 적재와 새 판매처 추가 방법
4. [판매처 공통 수집 데이터 명세](architecture/판매처_공통_수집_데이터_명세.md): ABC마트·29CM 데이터를 같은 형식으로 바꾸기 위한 검색·상품·가격·옵션·배송·리뷰 공통 필드 기준
5. [구현 TODO](planning/Purchase_Research_Agent_TODO.md): 앞으로 구현할 작업과 완료 조건을 확인하는 체크리스트
6. [개발 진행 관리](development/Purchase_Research_Agent_개발_진행_관리.md): 실제 구현 위치, 검증 결과, 발생한 문제와 해결 방법을 기록하는 문서

## 제출 준비 문서

- [오픈소스 개발자대회 규정 대응 체크리스트](planning/오픈소스_개발자대회_규정_대응_체크리스트.md): 현재 개발을 막지 않고, 라이선스·AI 사용·외부 구성요소·참가 자격을 제출 전에 확인하기 위한 보류 체크리스트

## 기록 문서

- [2026-07-19 무신사 수집 PoC 결과와 대안 제안](reports/2026-07-19_무신사_수집_PoC_결과와_대안_제안.md): 무신사 수집이 어려운 이유, 현재 구현 방식과 대회용 대상 사이트 변경 제안
- [2026-07-18 협업 공유용 개발 구조와 진행 제안](reports/2026-07-18_협업_공유용_개발_구조와_진행_제안.md): 특정 날짜의 협업 설명과 제안 기록

`reports/` 문서는 당시 논의 기록이므로 최신 구조의 기준으로 사용하지 않는다. 구조가 바뀌면 시스템 구조와 TODO를 먼저 갱신한다.
