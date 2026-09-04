-- 订单/购物车 sku_spec 快照落库：为已存在的库补充 sku_spec 列。
-- 对应 init.sql 的新结构；运行后再重启后端（ddl-auto: validate 要求实体与库结构一致）。
-- 用法：mysql -u root -p supermarket_system < deploy/upgrade-sku-spec.sql

USE supermarket_system;

DROP PROCEDURE IF EXISTS add_sku_spec_columns;
DELIMITER $$
CREATE PROCEDURE add_sku_spec_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'supermarket_system' AND table_name = 'cart_item' AND column_name = 'sku_spec'
    ) THEN
        ALTER TABLE cart_item
            ADD COLUMN sku_spec VARCHAR(255) DEFAULT NULL COMMENT 'Selected SKU spec snapshot';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'supermarket_system' AND table_name = 'order_item' AND column_name = 'sku_spec'
    ) THEN
        ALTER TABLE order_item
            ADD COLUMN sku_spec VARCHAR(255) DEFAULT NULL COMMENT 'Selected SKU spec snapshot';
    END IF;
END$$
DELIMITER ;

CALL add_sku_spec_columns();
DROP PROCEDURE IF EXISTS add_sku_spec_columns;
