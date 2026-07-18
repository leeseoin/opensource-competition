# Collector

Go 기반 판매처 데이터 수집 서비스다.

현재 구현:

- ABC마트 공개 검색 결과의 상품 검색
- 상품 번호, 상품명, 브랜드, 가격, 상품 URL 수집
- 검색 결과에 공개된 사이즈와 사이즈별 재고 수집
- `POST /internal/v1/collect/search`
- `GET /internal/v1/health`
- 요청 JSON 검증과 지원하지 않는 판매처 상태 반환
- timeout, 응답 크기 제한, A-RT 외부 redirect 차단
- ABC마트 요청 사이의 최소 1초 간격 제한
- 역할별로 분리된 단위 테스트
- `tests/integration`의 opt-in 실제 ABC마트 검색 테스트

판매처 범위:

- ABC마트: 공개 검색 수집 지원
- 무신사: 목표 판매처이지만 현재 일반 Collector 접근이 robots 정책에 의해 차단되어 구현 보류

아직 구현되지 않은 책임:

- 상품 상세 페이지의 가격·배송 수집
- 상품 상세 페이지의 전체 옵션·재고·사이즈표 수집
- 공개 리뷰 수집
- 동시성 상한과 retry 통제
- 로그인·CAPTCHA·접근 제한 감지
- Python Research Backend의 Collector API 호출

DB에 쓰거나 상품 추천을 수행하지 않는다.

## 현재 구조

```text
collector/
├── cmd/server/                 # Collector 실행
├── internal/
│   ├── collector/              # 공통 요청·결과 형식
│   ├── config/                 # 실행 설정
│   ├── merchants/abcmart/      # ABC마트 수집기
│   └── transport/http/         # HTTP API
├── testdata/abcmart/           # 저장 HTML
└── tests/                      # unit, integration 테스트
```

## 검증

빠른 단위 테스트와 기본 검증은 실제 쇼핑몰에 접속하지 않는다.

```bash
cd services/collector
go test ./...
go test -race ./...
go vet ./...
```

테스트 파일 구성:

- `tests/unit/`: 저장 자료와 가짜 HTTP 응답을 사용하는 빠른 단위 테스트
- `testdata/`: 단위 테스트용 저장 HTML
- `tests/integration/`: 실제 외부 서비스에 접속하는 통합 테스트

`internal/`에는 실행에 사용되는 실제 코드만 둔다.

실제 ABC마트 공개 검색 결과 확인은 아래 명령을 명시적으로 실행한 경우에만 동작한다.

```bash
cd services/collector
ABCMART_LIVE_SMOKE=1 go test -count=1 -run TestABC마트실제검색 -v ./tests/integration
```
