-- 第2阶·停留时长埋点：新建 page_dwell 表。
-- 运行后再重启后端（ddl-auto: validate 要求实体与库结构一致）。
-- 用法：mysql -u root -p supermarket_system < deploy/upgrade-dwell.sql

USE supermarket_system;

CREATE TABLE IF NOT EXISTS page_dwell (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Anonymous when null',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    seconds INT NOT NULL COMMENT 'Dwell seconds',
    source VARCHAR(30) DEFAULT NULL COMMENT 'e.g. detail',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_page_dwell_user (user_id),
    KEY idx_page_dwell_product (product_id),
    CONSTRAINT fk_page_dwell_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_page_dwell_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面停留时长埋点';
