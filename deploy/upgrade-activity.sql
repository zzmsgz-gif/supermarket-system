-- 第2阶·营销活动：新建 activity 表，并为 orders 表补充活动减免相关列。
-- 运行后再重启后端（ddl-auto: validate 要求实体与库结构一致）。
-- 用法：mysql -u root -p supermarket_system < deploy/upgrade-activity.sql

USE supermarket_system;

CREATE TABLE IF NOT EXISTS activity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(120) NOT NULL COMMENT 'Activity name',
    type VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCTION | DISCOUNT',
    scope VARCHAR(20) NOT NULL COMMENT 'ALL | CATEGORY | PRODUCT',
    category_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Scope=CATEGORY',
    product_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Scope=PRODUCT',
    threshold DECIMAL(10, 2) DEFAULT NULL COMMENT 'FULL_REDUCTION threshold',
    discount DECIMAL(10, 2) DEFAULT NULL COMMENT 'FULL_REDUCTION amount | DISCOUNT rate(0.9=9折)',
    start_time DATETIME NOT NULL COMMENT 'Active from',
    end_time DATETIME NOT NULL COMMENT 'Active to',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    priority INT NOT NULL DEFAULT 0 COMMENT 'Higher = matched first on tie',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_scope (scope),
    KEY idx_activity_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动';

DROP PROCEDURE IF EXISTS add_order_activity_columns;
DELIMITER $$
CREATE PROCEDURE add_order_activity_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'supermarket_system' AND table_name = 'orders' AND column_name = 'activity_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN activity_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Applied activity id';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'supermarket_system' AND table_name = 'orders' AND column_name = 'activity_discount'
    ) THEN
        ALTER TABLE orders ADD COLUMN activity_discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Activity discount amount';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'supermarket_system' AND table_name = 'orders' AND column_name = 'activity_name'
    ) THEN
        ALTER TABLE orders ADD COLUMN activity_name VARCHAR(120) DEFAULT NULL COMMENT 'Applied activity name snapshot';
    END IF;
END$$
DELIMITER ;

CALL add_order_activity_columns();
DROP PROCEDURE IF EXISTS add_order_activity_columns;
