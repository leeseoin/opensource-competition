# Collector Contract v1

작성일: 2026-07-14
최종 수정일: 2026-07-20

## 목적

이 디렉토리는 Go Collector와 Python Research Backend가 공유하는 언어 중립 JSON 계약을 정의한다. Go는 실제 판매처 수집 결과를 이 계약으로 반환하고, Python은 DB 저장이나 분석 전에 동일 계약으로 응답을 검증한다.

쉽게 말하면 Contract는 **Go와 Python이 주고받는 요청서와 결과서의 공통 양식**이다. 실제 상품을 수집하는 코드는 아니며, 두 서비스가 서로 이해할 수 있는 데이터 모양을 정한다.

```text
services/collector           = 실제 쇼핑몰 정보를 수집하는 코드
services/research-backend    = 수집 결과를 검사·저장·비교하는 코드
contracts                    = 두 서비스가 데이터를 주고받는 규격
```

## 왜 필요한가

이 프로젝트는 Go Collector와 Python Research Backend가 서로 다른 프로그램으로 실행된다. Go의 `struct`와 Python의 Pydantic model을 직접 공유할 수 없기 때문에, 두 언어 모두 이해할 수 있는 JSON Schema를 공통 기준으로 사용한다.

Spring Boot에 비유하면 Controller의 요청·응답 DTO 규격을 다른 서비스와 공유하는 것과 비슷하다. 차이점은 Java class를 공유하는 대신 Go와 Python이 함께 사용할 수 있는 JSON 문서로 규격을 작성한다는 것이다.

### Contract가 없으면 생기는 문제

Go가 가격을 다음처럼 반환할 수 있다.

```json
{
  "price": 69000
}
```

그런데 Python은 다음 형태를 예상할 수 있다.

```json
{
  "productPrice": "69,000원"
}
```

두 데이터는 같은 가격을 뜻하지만 필드 이름과 값의 형태가 달라서 Python이 처리할 수 없다. 이런 차이는 실행 중 오류, 잘못된 DB 저장, 가격 비교 실패로 이어진다.

Contract는 다음 내용을 미리 정해 이러한 문제를 막는다.

- 필수로 전달해야 하는 필드
- 각 필드의 이름과 자료형
- 가격과 통화 표현 방법
- 재고와 수집 상태에서 사용할 수 있는 값
- 오류와 부분 실패를 표현하는 방법
- 상품 정보의 출처 URL과 수집 시각
- 알 수 없는 필드를 허용할지 여부

### 이 프로젝트에서 더 중요한 이유

구매 정보는 가격과 재고가 자주 바뀌며, 쇼핑몰 접근이 차단되거나 일부 정보만 수집될 수도 있다. Contract가 없으면 아래 상황을 구분하기 어렵다.

```text
검색 결과가 정말 없는 경우
수집 중 일부 페이지가 실패한 경우
로그인이나 CAPTCHA로 접근이 차단된 경우
Collector가 지원하지 않는 페이지인 경우
```

그래서 정상 결과뿐만 아니라 `partial`, `blocked`, `unsupported`와 같은 실패 상태도 Contract로 정한다. 또한 가격·재고·옵션에는 출처와 수집 시각을 포함하도록 하여 오래된 정보를 현재 정보처럼 사용하지 않게 한다.

### 실제 동작 흐름

```text
1. Python이 search-request 규격으로 검색 요청을 만든다.
2. Go가 요청을 읽고 실제 쇼핑몰을 수집한다.
3. Go가 collector-result 규격으로 결과를 반환한다.
4. Python이 결과가 Schema에 맞는지 검사한다.
5. 검사에 통과한 데이터만 정규화·저장·비교한다.
6. 규격에 맞지 않으면 조용히 저장하지 않고 오류로 처리한다.
```

Contract의 목적은 Go와 Python 내부 코드를 똑같이 만드는 것이 아니다. 각 서비스는 내부 구조를 자유롭게 바꿀 수 있지만, 서비스 경계를 통과하는 JSON 형식은 함께 지키도록 만드는 것이다.

## 현재 Contract 범위

현재 작성된 계약 초안:

- `search-request.schema.json`: Python이 Go에 전달하는 판매처 상품 검색 요청
- `collector-result.schema.json`: 검색·상품 상세·공개 리뷰 수집 결과
- `verification-result.schema.json`: 구매 후보의 현재 가격·재고·옵션 재검증 결과

검색 결과에는 판매처가 알려준 전체 상품 수 `totalCount`와 다음 페이지 여부 `hasNext`를 선택 필드로 포함한다. 판매처가 값을 제공하지 않거나 요청이 실패하면 `null`을 사용한다. 두 값은 Collector가 반환한 상품 배열 길이가 아니라 로컬 필터 적용 전 판매처 검색 결과를 기준으로 한다.

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

Python은 이 값이 없는 상품 사실을 최신 판매처 정보로 저장하지 않는다.

### 부분 실패

Collector는 실패를 빈 정상 결과로 위장하지 않고 다음 상태 중 하나를 반환한다.

- `success`: 요청 범위 수집 성공
- `partial`: 일부 필드 또는 페이지 수집 실패
- `blocked`: 로그인, CAPTCHA 또는 접근 통제로 수집 중단
- `unsupported`: 현재 parser가 페이지를 지원하지 않음
- `temporarily_unavailable`: timeout 또는 일시적인 원격 오류

부분 실패의 세부 내용은 `warnings`와 `errors`에 기록한다.

### 개인정보 최소화

리뷰 계약에는 작성자 이름, 프로필 URL, 계정 ID를 포함하지 않는다. 이미지도 저장하지 않고 `hasImage`만 반환한다.

### 금액

금액은 부동소수점이 아닌 최소 통화 단위의 정수와 ISO 4217 통화 코드로 표현한다.

```json
{
  "amount": 89000,
  "currency": "KRW"
}
```

## 예제

유효한 예제:

- `examples/search-request.valid.json`
- `examples/collector-result.success.json`
- `examples/collector-result.partial.json`
- `examples/verification-result.changed.json`

검증 실패를 확인하기 위한 예제:

- `examples/invalid/collector-result.missing-provenance.json`

## 검증

`check-jsonschema`를 일회성 `uvx` 도구로 실행한다.

```bash
cd contracts/collector/v1

uvx check-jsonschema \
  --schemafile search-request.schema.json \
  examples/search-request.valid.json

uvx check-jsonschema \
  --schemafile collector-result.schema.json \
  examples/collector-result.success.json \
  examples/collector-result.partial.json

uvx check-jsonschema \
  --schemafile verification-result.schema.json \
  examples/verification-result.changed.json
```

잘못된 예제는 검증에 실패해야 한다.

```bash
uvx check-jsonschema \
  --schemafile collector-result.schema.json \
  examples/invalid/collector-result.missing-provenance.json
```

## 변경 정책

- v1 파일의 기존 필드 의미나 enum 값은 호환성을 깨뜨리는 방식으로 변경하지 않는다.
- 필수 필드 추가, 필드 제거, 의미 변경은 새 버전 디렉토리에서 진행한다.
- 선택 필드 추가는 Go와 Python 양쪽이 알 수 없는 필드를 처리하는 정책을 함께 검토한 뒤 반영한다.
- 예제와 검증 명령은 Schema 변경과 같은 커밋에서 갱신한다.
