-- Hibernate는 AttributeConverter<SiteType, Integer>를 INT 컬럼으로 기대한다(ddl-auto: validate).
-- V2에서 TINYINT UNSIGNED로 만들었더니 "wrong column type" 검증 오류가 나서 INT로 맞춘다.
ALTER TABLE product MODIFY COLUMN site_type INT NOT NULL;
ALTER TABLE crawl_target MODIFY COLUMN site_type INT NOT NULL DEFAULT 1;
