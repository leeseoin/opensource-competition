# Product Backend

Spring Boot 기반 상품 데이터 서버다.

## 현재 상태

- Spring Boot 4.1.0 기본 프로젝트 생성
- Java 21과 Gradle Wrapper 구성
- Spring Web MVC, Validation, JPA, Flyway, PostgreSQL, RabbitMQ, Actuator 의존성 추가
- PostgreSQL과 RabbitMQ Testcontainers 기본 설정 생성
- 상품 모델, Flyway migration, Queue 연동 및 API는 아직 구현 전

## 책임

- PostgreSQL의 유일한 최종 쓰기 서버
- 상품, 가격 이력, 옵션, 리뷰 및 근거 저장
- 검색과 상품 상세 내부 REST API 제공
- 수집 작업 생성과 상태 관리
- RabbitMQ 작업 발행과 Collector 결과 소비
- Contract 검증과 transaction 관리

MCP 프로토콜 처리와 판매처 접근은 담당하지 않는다.

## 실행

현재 데이터베이스 연결 설정과 migration은 구현 전이다. 기본 컴파일과 테스트는
다음 명령으로 실행한다.

```bash
./gradlew test
```

루트에서는 다음 명령을 사용할 수 있다.

```bash
make product-backend-test
```
