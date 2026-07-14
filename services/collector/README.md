# Collector

Go 기반 판매처 데이터 수집 서비스다.

예정 책임:

- 상품 검색
- 상품 상세·가격·배송 수집
- 옵션·재고·사이즈표 수집
- 공개 리뷰 수집
- 요청 빈도, 동시성, timeout, retry 통제
- 로그인·CAPTCHA·접근 제한 감지
- Python Research Backend에 내부 HTTP JSON 제공

DB에 쓰거나 상품 추천을 수행하지 않는다. Go module과 실제 코드는 Phase 1에서 추가한다.
