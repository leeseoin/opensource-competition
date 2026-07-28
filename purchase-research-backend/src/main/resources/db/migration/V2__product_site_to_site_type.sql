-- site(VARCHAR) 대신 site_type(TINYINT 코드)를 저장한다. 코드 매핑은
-- com.purchaseresearch.backend.domain.SiteType과 반드시 일치해야 한다: abcmart=1, 29cm=2.
-- 새 사이트를 추가할 때는 SiteType에 다음 정수를 이어서 추가하고, 여기 UPDATE 문에도
-- 같은 매핑으로 backfill 케이스를 추가한다.

ALTER TABLE product ADD COLUMN site_type TINYINT UNSIGNED NULL AFTER site;

UPDATE product SET site_type = CASE site
    WHEN 'abcmart' THEN 1
    WHEN '29cm' THEN 2
END;

ALTER TABLE product MODIFY COLUMN site_type TINYINT UNSIGNED NOT NULL;
ALTER TABLE product DROP INDEX uk_product_site_source_id;
ALTER TABLE product DROP COLUMN site;
ALTER TABLE product ADD UNIQUE KEY uk_product_site_type_source_id (site_type, source_product_id);

-- crawl_target은 아직 어떤 Java 코드도 참조하지 않지만(§7.4 미구현), product와 같은
-- 코드 체계로 맞춰서 나중에 스키마가 갈라지지 않게 한다.
ALTER TABLE crawl_target ADD COLUMN site_type TINYINT UNSIGNED NULL AFTER site;

UPDATE crawl_target SET site_type = CASE site
    WHEN 'abcmart' THEN 1
    WHEN '29cm' THEN 2
    ELSE 1
END;

ALTER TABLE crawl_target MODIFY COLUMN site_type TINYINT UNSIGNED NOT NULL DEFAULT 1;
ALTER TABLE crawl_target DROP INDEX uk_crawl_target;
ALTER TABLE crawl_target DROP COLUMN site;
ALTER TABLE crawl_target ADD UNIQUE KEY uk_crawl_target (site_type, target_type, keyword_or_category);
