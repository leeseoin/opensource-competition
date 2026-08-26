# Python/Go 크롤러 비교 계약 v1-unified

작성일: 2026-08-03
상태: 비교용 호환 계약

## 목적

이 디렉토리는 Python 크롤러와 Go 크롤러의 결과를 같은 모양으로 비교하기 위한
상품 단위 JSON 계약을 보관한다. 정우님이 작성한 Python 결과와 전달받은
`unified-product.schema.json`을 기준 자료로 사용한다.

이 계약은 Product Backend 저장 계약을 대체하지 않는다.

```text
판매처 원본 JSON
  ├── Python 판매처 Adapter
  └── Go 판매처 Adapter
            ↓
언어별 운영 결과
            ↓ 비교 Adapter
v1-unified 상품 배열
            ↓
정확성 및 성능 비교
```

## 계약 파일

- `unified-product.schema.json`: 비교 결과의 상품 한 건 규격
- `examples/unified_구두_top20_20260803_002024.json`: ABC마트 10건과 29CM 10건 기준 예제

## 운영 계약과 다른 이유

운영 경계는 [`contracts/collector/v1`](../v1/README.md)의 `CollectorResult`를 사용한다.
운영 계약은 가격을 정수와 통화 코드로 표현하고 상품 사실마다 출처 URL, 수집 시각,
Collector 버전을 보존한다.

`v1-unified`는 기존 Python 결과와 비교하기 위해 가격을 `"19,000원"` 문자열로
표현한다. 또한 상품 단위 비교에 집중하므로 요청 ID, 수집 상태, 경고, 오류와
`provenance`를 포함하지 않는다. 이 때문에 `v1-unified` 결과를 Product Backend에
직접 저장하면 안 된다.

## 핵심 필드 매핑

| 운영 `CollectorResult` | 비교 `v1-unified` | 변환 규칙 |
|---|---|---|
| `externalId` | `source_product_id` | 문자열 그대로 사용 |
| `name` | `title` | 문자열 그대로 사용 |
| `price.amount` | `price` | KRW 정수를 쉼표와 `원`이 있는 문자열로 변환 |
| `imageUrls[0]` | `image_url` | 첫 이미지가 없으면 빈 문자열 |
| `imageUrls` | `images` | 공개 이미지 URL 목록 그대로 사용 |
| `productUrl` | `link` | 문자열 그대로 사용 |
| `categoryPath` | `category_path` | ` > `로 연결 |
| `categoryPath` 마지막 값 | `category` | 경로가 없으면 빈 문자열 |
| `stockStatus` | `in_stock` | `available=true`, `out_of_stock=false`, 불명확하면 `null` |
| `options[].color` | `options.colors` | 빈 값을 제거하고 중복 제거 |
| `options[].size` | `options.sizes` | 빈 값을 제거하고 중복 제거 |
| `reviews[].externalId` | `reviews[].review_source_id` | 값이 없으면 `null` |
| `reviews[].hasImage` | `reviews[].images` | URL을 모르면 빈 배열을 사용하고 URL을 만들어내지 않음 |

운영 계약에 없는 `price_original`, `discount_percent`, `style_code`,
`helpful_count`는 판매처 원본에서 확인한 경우에만 채운다. 비교 Adapter가 확인할 수
없으면 Schema가 허용하는 빈 문자열, `null`, `0` 또는 빈 배열을 사용하되 성능
보고서의 필드 완전성에 누락으로 계산한다.

## 개인정보 및 리뷰 이미지

리뷰 작성자 이름, 프로필과 계정 식별정보는 포함하지 않는다. 리뷰 이미지 파일은
다운로드하지 않는다. 공개 응답에 이미지 URL이 있는 경우 비교 예제에서 참조할 수
있지만 운영 `CollectorResult`에는 사진 존재 여부인 `hasImage`만 저장한다.

## 검증

Schema는 상품 한 건을 정의하고 예제 파일은 상품 배열이다. 배열의 각 항목을
개별적으로 Schema에 검증해야 한다. Python과 Go 구현은 같은 예제 20건을 모두
통과시키는 contract test를 각각 제공해야 한다.
