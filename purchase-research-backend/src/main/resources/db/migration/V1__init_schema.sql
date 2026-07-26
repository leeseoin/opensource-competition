-- 상품 기본 정보 (가격의 "현재값"만 가짐 — 이력은 price_history에 별도 적재)
CREATE TABLE product (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    site              VARCHAR(20)     NOT NULL,
    source_product_id VARCHAR(50)     NOT NULL,
    title             VARCHAR(300)    NOT NULL,
    brand             VARCHAR(100),
    price             INT,
    price_original    INT,
    discount_percent  INT,
    image_url         VARCHAR(500),
    style_code        VARCHAR(50),
    link              VARCHAR(500)    NOT NULL,
    review_count      INT             NOT NULL DEFAULT 0,
    collected_at      DATETIME        NOT NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_site_source_id (site, source_product_id)
);

-- 가격 이력 (재수집 때마다 새 row를 INSERT, product.price는 최신값으로만 갱신)
CREATE TABLE price_history (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT          NOT NULL,
    price             INT             NOT NULL,
    price_original    INT,
    discount_percent  INT,
    collected_at      DATETIME        NOT NULL,
    CONSTRAINT fk_price_history_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    KEY idx_price_history_product_collected (product_id, collected_at)
);

-- 상품 옵션 (색상/사이즈)
CREATE TABLE product_option (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT      NOT NULL,
    option_type   VARCHAR(20) NOT NULL,
    option_value  VARCHAR(50) NOT NULL,
    CONSTRAINT fk_product_option_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- 상품 리뷰
-- 작성자 식별 정보(닉네임/회원ID 등)는 수집하지 않는다. review_source_id는 리뷰 자체의
-- 일련번호(ABC마트 prdtRvwSeq)일 뿐 개인정보가 아니며, 재수집 시 중복 판별 용도로만 쓴다.
CREATE TABLE product_review (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT      NOT NULL,
    review_source_id  VARCHAR(50) NOT NULL,
    content           TEXT,
    score             DECIMAL(3,1),
    review_date       DATE,
    size              VARCHAR(20),
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    UNIQUE KEY uk_product_review_source (product_id, review_source_id)
);

-- 주기적으로 재크롤링할 대상 (검색어/카테고리 단위 — 개별 상품 링크(product.link)와는 별개)
-- 스케줄러 연동은 아직 미구현(설계서 §7.4, Open Questions #19) — 테이블만 우선 준비해둔다.
CREATE TABLE crawl_target (
    id                        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    site                      VARCHAR(20)  NOT NULL DEFAULT 'abcmart',
    target_type               VARCHAR(20)  NOT NULL,
    keyword_or_category       VARCHAR(100) NOT NULL,
    request_url                VARCHAR(500),
    max_items                  INT         NOT NULL DEFAULT 200,
    recrawl_interval_minutes   INT         NOT NULL DEFAULT 1440,
    last_crawled_at            DATETIME,
    enabled                    BOOLEAN     NOT NULL DEFAULT true,
    created_at                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_crawl_target (site, target_type, keyword_or_category)
);
