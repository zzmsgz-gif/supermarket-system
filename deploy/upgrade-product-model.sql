-- 商品模型升级：为已存在的库补充新字段与新表。
-- 对应 init.sql 的新结构；运行后再重启后端（ddl-auto: validate 要求实体与库结构一致）。
-- 用法：mysql -u root -p supermarket_system < deploy/upgrade-product-model.sql

USE supermarket_system;

ALTER TABLE product
    ADD COLUMN brand VARCHAR(100) DEFAULT NULL COMMENT 'Brand name' AFTER status,
    ADD COLUMN is_hot TINYINT NOT NULL DEFAULT 0 COMMENT '1 = hot product' AFTER brand,
    ADD COLUMN is_new TINYINT NOT NULL DEFAULT 0 COMMENT '1 = new arrival' AFTER is_hot,
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order' AFTER is_new,
    ADD COLUMN tags VARCHAR(255) DEFAULT NULL COMMENT 'Comma separated marketing tags' AFTER sort_no;

CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    url VARCHAR(500) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_image_product (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_image_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product image gallery';

CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    spec_json TEXT,
    sku_code VARCHAR(64) DEFAULT NULL,
    image VARCHAR(500) DEFAULT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_sku_product (product_id),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_sku_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product SKU / specs';

CREATE TABLE IF NOT EXISTS product_attribute (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    attr_name VARCHAR(50) NOT NULL,
    attr_value VARCHAR(200) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_attribute_product (product_id),
    CONSTRAINT fk_product_attribute_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_attribute_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product attributes / spec table';
