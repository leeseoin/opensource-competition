# Third-Party Notices

최종 갱신일: 2026-08-05

## 이 문서를 공개하는 이유

[2026년 오픈소스 개발자대회 운영규정](https://api.osscontest.kr/static/uploads/b3b4491a-3bbe-454e-a1d8-6ed475b01b14.pdf)은 다음 내용을 요구한다.

- 5쪽 제8조 제5항: 다른 사람의 저작물을 이용한 경우 출처와 라이선스를 명시한다.
- 6쪽 제8조 제6항: 사용한 오픈소스 라이브러리, 프레임워크 및 모델의 출처와 라이선스를 명확히 공개한다.

운영규정이 `THIRD_PARTY_NOTICES.md`라는 파일명이나 아래 표 형식을 지정한 것은 아니다. 이 프로젝트는 위 요구사항을 공개 저장소에서 지속적으로 확인할 수 있도록 이 파일을 공개 문서로 사용한다.

이 문서는 프로젝트 자체 라이선스를 정하는 `LICENSE`를 대신하지 않는다. 저장소의 자체 라이선스는 팀 협의 후 별도로 추가해야 한다.

## 현재 직접 사용하는 구성요소

아래 버전은 manifest와 lock 파일에서 확인한 직접 의존성 기준이다. 간접 의존성은 각 생태계의 lock 파일 및 해석 결과를 함께 기준으로 하며, 제출 전 전체 목록을 다시 점검한다.

### Go Collector

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Go | 1.25 | Collector 개발 및 실행 | [go.dev](https://go.dev/) | BSD-3-Clause |
| RabbitMQ AMQP 0-9-1 Go Client | 1.10.0 | RabbitMQ 작업과 결과 메시지 처리 | [rabbitmq/amqp091-go](https://github.com/rabbitmq/amqp091-go) | BSD-3-Clause |
| Google Chrome 또는 Chromium | 시스템 설치 버전 | ABC마트 공개 검색 화면의 JavaScript rendering HTML 검증 | [Chromium](https://www.chromium.org/) / [Chrome 약관](https://www.google.com/chrome/terms/) | Chromium BSD-3-Clause 기반 / Chrome 배포물은 Google 약관 적용 |
| Swagger UI | 5.32.6 | Go Collector OpenAPI 브라우저 테스트 화면 | [swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui/tree/v5.32.6) | Apache-2.0 |

정확한 Go 모듈 해석 결과는 `services/collector/go.mod`와 `services/collector/go.sum`에서 확인한다.

### Spring Boot Product Backend

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Java/OpenJDK | 21 | Product Backend 실행 환경 | [OpenJDK](https://openjdk.org/) | GPL-2.0-only WITH Classpath-exception-2.0 |
| Spring Boot | 4.1.0 | Web, JPA, AMQP, Validation, Actuator 기반 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) | Apache-2.0 |
| Spring Dependency Management Plugin | 1.1.7 | Gradle 의존성 버전 관리 | [spring-gradle-plugins/dependency-management-plugin](https://github.com/spring-gradle-plugins/dependency-management-plugin) | Apache-2.0 |
| Flyway | 12.4.0 | PostgreSQL schema migration | [flyway/flyway](https://github.com/flyway/flyway) | Apache-2.0 |
| PostgreSQL JDBC Driver | 42.7.11 | PostgreSQL 연결 | [pgjdbc/pgjdbc](https://github.com/pgjdbc/pgjdbc) | BSD-2-Clause |
| Lombok | 1.18.46 | Java 반복 코드 생성 | [projectlombok/lombok](https://github.com/projectlombok/lombok) | MIT |
| springdoc-openapi | 3.0.3 | OpenAPI 문서 자동 생성과 Swagger UI 연결 | [springdoc/springdoc-openapi](https://github.com/springdoc/springdoc-openapi) | Apache-2.0 |
| Swagger UI | 5.32.2 | browser에서 내부 API 조회와 수동 호출 | [swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui) | Apache-2.0 |
| Testcontainers | 2.0.5 | PostgreSQL/RabbitMQ 통합 테스트 | [testcontainers/testcontainers-java](https://github.com/testcontainers/testcontainers-java) | MIT |

Spring Framework, Hibernate, Jackson, JUnit 등 간접 의존성은 Gradle이 해석한다. 정확한 제출 목록은 `services/product-backend/gradle.lockfile` 도입 여부를 결정한 뒤 Gradle dependency report와 함께 확정한다.

### Next.js Web

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Next.js | 16.2.12 | 사용자 화면과 server 기능 | [vercel/next.js](https://github.com/vercel/next.js) | MIT |
| React | 19.2.4 | UI component | [facebook/react](https://github.com/facebook/react) | MIT |
| React DOM | 19.2.4 | browser rendering | [facebook/react](https://github.com/facebook/react) | MIT |
| Tailwind CSS | 4.3.3 | UI style | [tailwindlabs/tailwindcss](https://github.com/tailwindlabs/tailwindcss) | MIT |
| TypeScript | 5.9.3 | 정적 type 검사 | [microsoft/TypeScript](https://github.com/microsoft/TypeScript) | Apache-2.0 |
| ESLint | 9.39.5 | 정적 코드 검사 | [eslint/eslint](https://github.com/eslint/eslint) | MIT |
| eslint-config-next | 16.2.12 | Next.js ESLint 규칙 | [vercel/next.js](https://github.com/vercel/next.js) | MIT |
| Node.js type definitions | 20.19.43 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |
| React type definitions | 19.2.17 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |
| React DOM type definitions | 19.2.3 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |

정확한 Node package 해석 결과는 `frontend/purchase-web/package.json`과 `frontend/purchase-web/package-lock.json`에서 확인한다.
이번 변경은 새 Node package를 추가하지 않았으며 Node.js 내장 `node:test`를 Next.js server route
테스트에 사용한다. `next.config.ts`에는 DB 상품 이미지를 화면에 표시하기 위해 ABC마트의
`image.a-rt.com`과 29CM의 `img.29cm.co.kr` HTTPS host만 허용했다.

### 공개 상품 이미지

`frontend/purchase-web/public/images/landing-v2`의 화면 시제품용 상품 이미지는 ABC마트와
29CM의 공개 상품 화면에서 확인한 이미지다. 이미지 저작권과 상표권은 각 판매처, 브랜드 또는
권리자에게 있으며 이 저장소의 오픈소스 라이선스로 재허가되지 않는다. 제출과 배포 전 번들
유지 여부를 팀이 다시 검토하고, 운영 화면은 Product Backend가 보관한 공개 원본 URL을 우선 사용한다.

### 로컬 인프라와 CI

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| PostgreSQL Docker Official Image | 16-alpine | 상품 데이터 저장 | [docker-library/postgres](https://github.com/docker-library/postgres) | image packaging MIT / PostgreSQL PostgreSQL License |
| Redis Docker Official Image | 7.2-alpine | 속도 제한, 중복 방지, 진행 상태 | [docker-library/redis](https://github.com/docker-library/redis) | Redis 7.2 BSD-3-Clause / image 내 배포 구성요소별 라이선스 상이 |
| RabbitMQ Docker Official Image | 4.2-management-alpine | 수집 작업과 결과 Queue | [docker-library/rabbitmq](https://github.com/docker-library/rabbitmq) | RabbitMQ MPL-2.0 / image 내 배포 구성요소별 라이선스 상이 |
| actions/checkout | v4 | 문서 동기화 CI의 저장소 checkout | [actions/checkout](https://github.com/actions/checkout) | MIT |

Docker image에는 Alpine Linux와 여러 system package가 포함되므로 위 표의 단일 라이선스만으로 image 전체 구성요소를 대표하지 않는다. 제출용 배포 image를 확정하면 image SBOM과 각 package license를 추가 점검한다.

## Plugin, MCP 및 AI 모델

- `plugins/purchase-research-agent`는 이 저장소에서 직접 작성 중인 Codex Plugin이다.
- `services/mcp-server`는 현재 README와 경계만 있으며 외부 MCP SDK 및 runtime 의존성은 아직 없다.
- Ollama, llama.cpp 및 GPU model server는 계획 단계이며 현재 실행 의존성이나 내장 model이 아니다.
- 외부 AI model을 runtime에 추가하면 이름, 정확한 version, 제공자, 출처, weight 공개 여부, license 및 실행 방식을 이 문서와 `AI_USAGE.md`에 함께 기록한다.

## 갱신 방법

다음 파일에서 의존성, image, plugin, MCP 또는 model 관련 항목을 추가/삭제/변경하면 같은 commit에서 이 문서도 갱신한다.

- `services/collector/go.mod` 및 `services/collector/go.sum`
- `services/product-backend/build.gradle` 및 Gradle 설정
- `frontend/purchase-web/package.json` 및 `frontend/purchase-web/package-lock.json`
- `compose.yaml`
- `plugins/` 및 `services/mcp-server/`의 manifest
- 향후 model server와 model 설정

`make docs-check`는 관련 파일이 변경됐는데 이 문서가 함께 변경되지 않은 경우 실패한다. 이 검사는 출처와 라이선스가 정확한지 대신 판단하지 않으므로, 작성자가 공식 저장소와 배포물의 license를 직접 확인해야 한다.
