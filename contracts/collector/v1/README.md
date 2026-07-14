# Collector Contract v1

작성일: 2026-07-14

## 목적

이 디렉토리는 Go Collector와 Python Research Backend가 공유하는 언어 중립 JSON 계약을 정의한다. Go는 실제 판매처 수집 결과를 이 계약으로 반환하고, Python은 DB 저장이나 분석 전에 동일 계약으로 응답을 검증한다.

현재 구현된 계약:

- `search-request.schema.json`: Python이 Go에 전달하는 판매처 상품 검색 요청
- `collector-result.schema.json`: 검색·상품 상세·공개 리뷰 수집 결과
- `verification-result.schema.json`: 구매 후보의 현재 가격·재고·옵션 재검증 결과

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
