# Third-Party Notices

최종 갱신일: 2026-08-13

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

정확한 Go 모듈 해석 결과는 `services/collector/go.mod`와 `services/collector/go.sum`에서 확인한다.

### Python Collector 비교 구현

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Python | 3.12 이상 | Python 크롤러 비교 구현 실행 | [python/cpython](https://github.com/python/cpython) | PSF-2.0 |
| HTTPX | 0.28.1 | ABC마트/29CM 공개 검색 HTTP 요청과 테스트용 MockTransport | [encode/httpx](https://github.com/encode/httpx) | BSD-3-Clause |
| jsonschema | 4.25.1 | `v1-unified` JSON Schema runtime 검증 | [python-jsonschema/jsonschema](https://github.com/python-jsonschema/jsonschema) | MIT |
| Hatchling | 1.27.0 | Python package build backend | [pypa/hatch](https://github.com/pypa/hatch) | MIT |

정확한 Python 의존성 해석 결과는 `services/python-collector/pyproject.toml`과
`services/python-collector/uv.lock`에서 확인한다. `origin/dev-jw`에서 참고한 Python
크롤러는 같은 저장소 협업 브랜치의 코드이며, 선별 이식 범위와 변경 이유는
`services/python-collector/README.md`에 기록한다.

### Python 크롤러 runtime

아래 구성요소는 `origin/dev-jw@e2d863c`에서 선별 이식한 Python 크롤러를
`purchase-research-agent/pyproject.toml`과 `purchase-research-agent/uv.lock`으로
고정한 직접 의존성이다. 실행 Python은 3.12 계열로 제한하며 현재 lock은 Python
3.12.11로 생성했다. 정확한 전이 의존성과 배포 파일 hash는 `uv.lock`에서 확인한다.

| 구성요소 | lock version | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Python | 3.12.11 | Python 크롤러 runtime | [python/cpython](https://github.com/python/cpython) | PSF-2.0 |
| aio-pika | 9.6.2 | RabbitMQ 비동기 연결과 메시지 처리 | [mosquito/aio-pika](https://github.com/mosquito/aio-pika) | Apache-2.0 |
| FastAPI | 0.141.1 | Python 크롤러 HTTP API | [fastapi/fastapi](https://github.com/fastapi/fastapi) | MIT |
| Uvicorn | 0.52.1 | ASGI server | [encode/uvicorn](https://github.com/encode/uvicorn) | BSD-3-Clause |
| HTTPX | 0.28.1 | 29CM 및 ABC마트 상세 JSON 요청 | [encode/httpx](https://github.com/encode/httpx) | BSD-3-Clause |
| Pydantic | 2.13.4 | API 요청/응답 model 검증 | [pydantic/pydantic](https://github.com/pydantic/pydantic) | MIT |
| python-dotenv | 1.2.2 | 환경 변수 파일 로드 | [theskumar/python-dotenv](https://github.com/theskumar/python-dotenv) | BSD-3-Clause |
| Beautiful Soup | 4.15.0 | ABC마트 HTML 파싱 | [wention/BeautifulSoup4](https://github.com/wention/BeautifulSoup4) | MIT |
| Crawl4AI | 0.9.2 | ABC마트 browser 기반 페이지 수집 | [unclecode/crawl4ai](https://github.com/unclecode/crawl4ai) | Apache-2.0 |
| Playwright Python | 1.62.0 | Crawl4AI browser 실행 기반 | [microsoft/playwright-python](https://github.com/microsoft/playwright-python) | Apache-2.0 |
| jsonschema | 4.26.0 | 수집 결과 Contract 검증 | [python-jsonschema/jsonschema](https://github.com/python-jsonschema/jsonschema) | MIT |

정우님 원본 코드의 선별 이식 범위와 기존 Python 비교 구현과의 차이는
`docs/reports/2026-08-04_dev-jw_Python_크롤러_가져오기와_차이_분석.md`에 기록한다.

### Spring Boot Product Backend

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| Java/OpenJDK | 21 | Product Backend 실행 환경 | [OpenJDK](https://openjdk.org/) | GPL-2.0-only WITH Classpath-exception-2.0 |
| Spring Boot | 4.1.0 | Web, JPA, AMQP, Validation, Actuator 기반 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) | Apache-2.0 |
| Spring Dependency Management Plugin | 1.1.7 | Gradle 의존성 버전 관리 | [spring-gradle-plugins/dependency-management-plugin](https://github.com/spring-gradle-plugins/dependency-management-plugin) | Apache-2.0 |
| Flyway | 12.4.0 | PostgreSQL schema migration | [flyway/flyway](https://github.com/flyway/flyway) | Apache-2.0 |
| PostgreSQL JDBC Driver | 42.7.11 | PostgreSQL 연결 | [pgjdbc/pgjdbc](https://github.com/pgjdbc/pgjdbc) | BSD-2-Clause |
| PostgreSQL pg_trgm | PostgreSQL 16 bundled | 상품 검색 trigram 유사도와 GIN index | [PostgreSQL pg_trgm](https://www.postgresql.org/docs/16/pgtrgm.html) | PostgreSQL License |
| pgvector | 0.8.2 | PostgreSQL 1024차원 cosine vector 검색과 HNSW index | [pgvector/pgvector](https://github.com/pgvector/pgvector/tree/v0.8.2) | PostgreSQL License |
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
| Radix UI Select | 2.3.7 | 접근 가능한 공통 select/dropdown UI | [radix-ui/primitives](https://github.com/radix-ui/primitives) | MIT |
| Tailwind CSS | 4.3.3 | UI style | [tailwindlabs/tailwindcss](https://github.com/tailwindlabs/tailwindcss) | MIT |
| TypeScript | 5.9.3 | 정적 type 검사 | [microsoft/TypeScript](https://github.com/microsoft/TypeScript) | Apache-2.0 |
| Model Context Protocol TypeScript SDK | 1.30.0 | Purchase Research stdio MCP Server와 Next.js Agent Gateway client | [modelcontextprotocol/typescript-sdk](https://github.com/modelcontextprotocol/typescript-sdk) | MIT |
| Zod | 4.4.3 | MCP 도구 입력 Schema 검증 | [colinhacks/zod](https://github.com/colinhacks/zod) | MIT |
| ESLint | 9.39.5 | 정적 코드 검사 | [eslint/eslint](https://github.com/eslint/eslint) | MIT |
| eslint-config-next | 16.2.12 | Next.js ESLint 규칙 | [vercel/next.js](https://github.com/vercel/next.js) | MIT |
| Node.js type definitions | 20.19.43 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |
| React type definitions | 19.2.17 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |
| React DOM type definitions | 19.2.3 | TypeScript 개발 type | [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | MIT |

정확한 Node package 해석 결과는 `frontend/purchase-web/package.json`과 `frontend/purchase-web/package-lock.json`에서 확인한다.
Node.js 내장 `node:test`를 Next.js server route와 상품군 선택 단위 테스트에 사용한다.
`next.config.ts`에는 DB 상품 이미지를 화면에 표시하기 위해 ABC마트의
`image.a-rt.com`과 29CM의 `img.29cm.co.kr` HTTPS host만 허용했다.

### 공개 상품 이미지

`frontend/purchase-web/public/images/landing-v2`의 화면 시제품용 상품 이미지는 ABC마트와
29CM의 공개 상품 화면에서 확인한 이미지다. 이미지 저작권과 상표권은 각 판매처, 브랜드 또는
권리자에게 있으며 이 저장소의 오픈소스 라이선스로 재허가되지 않는다. 제출과 배포 전 번들
유지 여부를 팀이 다시 검토하고, 운영 화면은 Product Backend가 보관한 공개 원본 URL을 우선 사용한다.

### 로컬 인프라와 CI

| 구성요소 | 버전 | 용도 | 출처 | 라이선스 |
|---|---:|---|---|---|
| pgvector PostgreSQL Docker Image | 0.8.2-pg16 | 상품 데이터와 vector 검색 저장 | [pgvector/pgvector](https://github.com/pgvector/pgvector/tree/v0.8.2) | pgvector PostgreSQL License / base PostgreSQL PostgreSQL License / image 구성요소별 라이선스 상이 |
| Redis Docker Official Image | 7.2-alpine | 속도 제한, 중복 방지, 진행 상태 | [docker-library/redis](https://github.com/docker-library/redis) | Redis 7.2 BSD-3-Clause / image 내 배포 구성요소별 라이선스 상이 |
| RabbitMQ Docker Official Image | 4.2-management-alpine | 수집 작업과 결과 Queue | [docker-library/rabbitmq](https://github.com/docker-library/rabbitmq) | RabbitMQ MPL-2.0 / image 내 배포 구성요소별 라이선스 상이 |
| actions/checkout | v4 | 문서 동기화 CI의 저장소 checkout | [actions/checkout](https://github.com/actions/checkout) | MIT |

Docker image에는 Alpine Linux와 여러 system package가 포함되므로 위 표의 단일 라이선스만으로 image 전체 구성요소를 대표하지 않는다. 제출용 배포 image를 확정하면 image SBOM과 각 package license를 추가 점검한다.

## Plugin, MCP 및 AI 모델

- `plugins/purchase-research-agent`는 이 저장소에서 직접 작성 중인 Codex Plugin이다.
- `services/mcp-server`는 공식 MCP TypeScript SDK와 Zod를 사용해 stdio 도구를 제공한다.
- 선택적 로컬 embedding adapter는 `BAAI/bge-m3`를 Ollama `bge-m3:567m` tag로 호출한다.
  BGE-M3는 1024차원/다국어 공개 weight model이며 [BAAI model card](https://huggingface.co/BAAI/bge-m3)에
  MIT로 표시돼 있다. weight는 저장소와 Docker image에 포함하지 않으며 제출 전 Ollama manifest
  digest와 배포 artifact의 license metadata를 다시 확인한다.
- Ollama, llama.cpp 및 GPU model server는 계획 단계이며 현재 실행 의존성이나 내장 model이 아니다.
- 외부 AI model을 runtime에 추가하면 이름, 정확한 version, 제공자, 출처, weight 공개 여부, license 및 실행 방식을 이 문서와 `AI_USAGE.md`에 함께 기록한다.

## 갱신 방법

다음 파일에서 의존성, image, plugin, MCP 또는 model 관련 항목을 추가/삭제/변경하면 같은 commit에서 이 문서도 갱신한다.

- `services/collector/go.mod` 및 `services/collector/go.sum`
- `services/python-collector/pyproject.toml` 및 `services/python-collector/uv.lock`
- `services/product-backend/build.gradle` 및 Gradle 설정
- `frontend/purchase-web/package.json` 및 `frontend/purchase-web/package-lock.json`
- `compose.yaml`
- `plugins/` 및 `services/mcp-server/`의 manifest
- 향후 model server와 model 설정

`make docs-check`는 관련 파일이 변경됐는데 이 문서가 함께 변경되지 않은 경우 실패한다. 이 검사는 출처와 라이선스가 정확한지 대신 판단하지 않으므로, 작성자가 공식 저장소와 배포물의 license를 직접 확인해야 한다.
