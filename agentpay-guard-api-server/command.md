# agentpay-guard-api-server commands

이 문서는 AgentPay Guard API Server 개발 중 자주 쓰는 Spring Boot / Gradle 명령을 정리한다.

## 위치 이동

```bash
cd /Users/iseoin/Golang_project/opensource-competition/agentpay-guard-api-server
```

## 의존성 확인

Gradle이 의존성을 정상적으로 해석하는지 확인한다.

```bash
./gradlew dependencies --configuration runtimeClasspath
```

특정 의존성이 들어왔는지 확인한다.

```bash
./gradlew dependencyInsight --dependency springdoc --configuration runtimeClasspath
```

PostgreSQL driver 확인:

```bash
./gradlew dependencyInsight --dependency postgresql --configuration runtimeClasspath
```

## 컴파일

테스트 실행 없이 Java 컴파일만 확인한다.

```bash
./gradlew compileJava
```

테스트 코드 컴파일까지 확인한다.

```bash
./gradlew compileTestJava
```

## 테스트

전체 테스트를 실행한다.

```bash
./gradlew test
```

현재 skeleton 상태에서는 datasource 설정이 없으면 `contextLoads()`가 실패할 수 있다.
PostgreSQL 설정 또는 test profile을 추가한 뒤 통과시키는 것을 목표로 한다.

## 애플리케이션 실행

Spring Boot 앱을 실행한다.

```bash
./gradlew bootRun
```

기본 포트는 별도 설정이 없으면 `8080`이다.

```text
http://localhost:8080
```

## OpenAPI / Swagger 확인

`springdoc-openapi-starter-webmvc-ui` 의존성을 추가한 뒤 앱이 실행되면 아래 주소를 확인한다.

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI 의존성 확인:

```bash
./gradlew dependencyInsight --dependency springdoc-openapi-starter-webmvc-ui --configuration runtimeClasspath
```

## 빌드

테스트 포함 전체 빌드:

```bash
./gradlew build
```

테스트를 제외하고 빌드:

```bash
./gradlew build -x test
```

## 클린 빌드

빌드 산출물을 지우고 다시 빌드한다.

```bash
./gradlew clean build
```

테스트를 제외한 클린 빌드:

```bash
./gradlew clean build -x test
```

## Gradle task 목록

사용 가능한 Gradle task를 확인한다.

```bash
./gradlew tasks
```

## PostgreSQL 설정 후 실행 예시

나중에 `application.yaml`에 DB 설정을 추가하면 아래처럼 실행한다.

```bash
./gradlew bootRun
```

환경 변수로 DB 정보를 넣는 방식도 사용할 수 있다.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agentpay_guard \
SPRING_DATASOURCE_USERNAME=agentpay \
SPRING_DATASOURCE_PASSWORD=agentpay \
./gradlew bootRun
```

민감한 실제 비밀번호는 커밋하지 않는다.

## Docker PostgreSQL 확인

루트 디렉토리에서 PostgreSQL 컨테이너를 실행한다.

```bash
cd /Users/iseoin/Golang_project/opensource-competition
docker compose up -d postgres
docker compose ps
```

DB role 목록을 확인한다.

```bash
docker exec -it agentpay-guard-postgres psql -U postgres -d agentpay_guard -c "\du"
```

Spring Boot와 같은 접속 정보로 Docker PostgreSQL에 접속되는지 확인한다.

```bash
PGPASSWORD=agentpay_guard psql \
  -h 127.0.0.1 \
  -p 5432 \
  -U agentpay_guard_seoin \
  -d agentpay_guard \
  -c "select current_user, current_database();"
```

`localhost`가 macOS 로컬 PostgreSQL 또는 IPv6 주소로 잡히면 Docker 컨테이너가 아닌 다른 DB에 붙을 수 있다.
그래서 개발용 JDBC URL은 `127.0.0.1`을 사용한다.

## 자주 보는 파일

```text
build.gradle
settings.gradle
src/main/resources/application.yaml
src/main/java/com/agentpayguard/api/AgentpayGuardApiServerApplication.java
```

## 현재 주의 사항

- `build/`, `.gradle/`, `bin/`은 커밋하지 않는다.
- 실제 DB 비밀번호, API key, private key는 커밋하지 않는다.
- OpenAPI가 보이지 않으면 먼저 `./gradlew dependencyInsight --dependency springdoc --configuration runtimeClasspath`로 의존성 해석 여부를 확인한다.
- 앱 실행이 datasource에서 실패하면 PostgreSQL 설정 또는 테스트용 profile을 먼저 추가한다.
