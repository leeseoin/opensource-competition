# Collector Contract v1

작성일: 2026-07-14
최종 수정일: 2026-07-30

## 목적

이 디렉토리는 Go Collector와 Spring Boot Product Backend가 공유하는 언어 중립 JSON 계약을 정의한다.

```text
services/collector          = 실제 판매처 정보를 수집하는 코드
services/product-backend    = 결과를 검사, 저장, 조회하는 코드
contracts/collector/v1      = 두 서비스 사이의 요청과 응답 규격
```

Go의 `struct`와 Java의 DTO는 서로 직접 공유할 수 없다. JSON Schema를 공통 기준으로 사용하면 두 서비스가 독립적으로 개발되어도 경계의 데이터 형식을 맞출 수 있다.

## 왜 필요한가

같은 가격도 서비스마다 다르게 표현할 수 있다.

```json
{
  "price": 69000
}
```

```json
{
  "productPrice": "69,000원"
}
```

필드 이름과 자료형이 다르면 실행 중 오류, 잘못된 DB 저장, 가격 비교 실패가 발생한다. Contract는 다음 내용을 미리 정한다.

- 필수 필드와 자료형
- 가격과 통화 표현
- 재고와 수집 상태 값
- 오류와 부분 실패 표현
- 출처 URL과 수집 시각
- 알 수 없는 필드의 허용 여부

## 실제 동작 흐름

```text
1. Product Backend가 search-request 규격으로 요청을 만든다.
2. Go Collector가 공개 판매처 정보를 수집한다.
3. Go Collector가 collector-result 규격으로 결과를 반환한다.
4. Product Backend가 Java DTO와 Schema 기준으로 결과를 검사한다.
5. 검사에 통과한 결과만 정규화하고 PostgreSQL에 저장한다.
```

현재 Spring Boot의 작업 발행, 결과 DTO 검증과 PostgreSQL 저장 경로가 구현돼 있다.
JSON Schema를 런타임에 직접 적용하는 검증과 전체 계약 CI는 남아 있다.

## 현재 Contract 범위

- `search-request.schema.json`: Product Backend가 Go에 전달하는 판매처 상품 검색 요청
- `collector-result.schema.json`: 검색, 상품 상세, 공개 리뷰 수집 결과
- `verification-result.schema.json`: 구매 후보의 최신 가격, 재고, 옵션 재검증 결과

Python과 Go의 언어별 결과를 같은 상품 모양으로 비교하는 호환 계약은
[`../unified`](../unified/README.md)에 둔다. 비교 계약은 가격 문자열과 기존 Python
필드를 유지하므로 이 운영 계약이나 Product Backend 저장 입력을 대체하지 않는다.

검색 결과는 판매처가 알려준 전체 상품 수 `totalCount`와 다음 페이지 여부 `hasNext`를 선택 필드로 포함한다. 값을 제공하지 않거나 요청이 실패하면 `null`을 사용한다.

## 핵심 원칙

### 출처 추적

상품, 배송, 옵션, 실측, 리뷰에는 필요한 수준의 `provenance`를 포함한다.

```json
{
  "sourceUrl": "https://merchant.example/products/123",
  "collectedAt": "2026-07-14T10:00:00+09:00",
  "collectorVersion": "merchant-v1"
}
```

Product Backend는 출처가 없는 판매처 사실을 최신 정보로 저장하지 않는다.

### 부분 실패

Collector는 실패를 빈 정상 결과로 바꾸지 않고 다음 상태 중 하나를 반환한다.

- `success`: 요청 범위 수집 성공
- `partial`: 일부 필드 또는 페이지 수집 실패
- `blocked`: 로그인, CAPTCHA 또는 접근 통제로 수집 중단
- `unsupported`: 현재 parser가 페이지를 지원하지 않음
- `temporarily_unavailable`: timeout 또는 일시적인 원격 오류

### 개인정보 최소화

리뷰 계약에는 작성자 이름, 프로필 URL, 계정 ID를 포함하지 않는다. 이미지도 저장하지 않고 `hasImage`만 반환한다.

## 변경 규칙

- 필수 필드 삭제와 의미 변경은 v1에서 바로 적용하지 않는다.
- 선택 필드 추가도 Go DTO와 Java DTO의 알 수 없는 필드 정책을 함께 검토한다.
- Schema, 예제, Go DTO, Java DTO, contract test를 같은 작업에서 갱신한다.

## 검증

```bash
cd contracts/collector/v1
uvx check-jsonschema --schemafile search-request.schema.json examples/search-request.valid.json
uvx check-jsonschema --schemafile collector-result.schema.json examples/collector-result.success.json
```
