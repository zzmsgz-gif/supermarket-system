-- ============================================================
-- 秒杀独立商品迁移
-- 背景：秒杀场次从「复用原商品」改为「克隆出一件独立的 FLASH 商品」。
-- 这样秒杀商品与原商品是两件不同的商品，库存/名额/列表互不干扰。
-- 本脚本对【存量库】安全、幂等，可重复执行。
-- ============================================================

USE supermarket_system;

-- 1) 补列（幂等：列已存在则跳过）
SET @has_kind = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'supermarket_system' AND table_name = 'product' AND column_name = 'kind');
SET @sql_kind = IF(@has_kind = 0,
    "ALTER TABLE product ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 普通商品 / FLASH 秒杀独立商品'",
    'SELECT 1');
PREPARE stmt_kind FROM @sql_kind;
EXECUTE stmt_kind;
DEALLOCATE PREPARE stmt_kind;

SET @has_src = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'supermarket_system' AND table_name = 'flash_sale' AND column_name = 'source_product_id');
SET @sql_src = IF(@has_src = 0,
    'ALTER TABLE flash_sale ADD COLUMN source_product_id BIGINT DEFAULT NULL COMMENT ''被秒杀的原商品 id''',
    'SELECT 1');
PREPARE stmt_src FROM @sql_src;
EXECUTE stmt_src;
DEALLOCATE PREPARE stmt_src;

-- 2) 已软删的秒杀场次：回填 source_product_id = product_id（不再克隆），保证校验口径一致
UPDATE flash_sale SET source_product_id = product_id WHERE deleted = 1 AND source_product_id IS NULL;

-- 3) 仍在售的秒杀场次：克隆出独立的 FLASH 商品（含规格价），并把 product_id 指向它
DROP PROCEDURE IF EXISTS migrate_flash_to_separate_product;
DELIMITER //
CREATE PROCEDURE migrate_flash_to_separate_product()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE fsid BIGINT;
    DECLARE srcid BIGINT;
    DECLARE tq INT;
    DECLARE newid BIGINT;
    DECLARE cur CURSOR FOR
        SELECT id, product_id, total_quota
        FROM flash_sale
        WHERE deleted = 0 AND source_product_id IS NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO fsid, srcid, tq;
        IF done THEN LEAVE read_loop; END IF;

        -- 克隆原商品成 FLASH 商品：库存=秒杀名额、会员价清空、热/新标记清零
        INSERT INTO product (
            category_id, sku, name, subtitle, description, cover_url,
            price, original_price, stock, low_stock_threshold, sales,
            unit, status, brand, is_hot, is_new, sort_no, tags, deleted, member_price, kind
        )
        SELECT
            category_id,
            CONCAT(sku, '-FS', fsid),
            CONCAT(name, ' 限时秒杀'),
            subtitle, description, cover_url,
            price, original_price,
            tq, low_stock_threshold, 0,
            unit, 'ON_SALE', brand, 0, 0, 0, tags, 0, NULL, 'FLASH'
        FROM product WHERE id = srcid;

        SET newid = LAST_INSERT_ID();

        -- 克隆规格价，保证多规格商品走秒杀时按「规格价 × 折扣率」计算
        INSERT INTO product_sku (product_id, spec_json, sku_code, price, original_price, image, sort_no, deleted)
        SELECT newid, spec_json, sku_code, price, original_price, image, sort_no, 0
        FROM product_sku WHERE product_id = srcid AND deleted = 0;

        -- 把场次指向独立商品，并记录原商品
        UPDATE flash_sale SET product_id = newid, source_product_id = srcid WHERE id = fsid;
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

CALL migrate_flash_to_separate_product();
DROP PROCEDURE IF EXISTS migrate_flash_to_separate_product;

-- 4) 核对：应无 deleted=0 且 source_product_id IS NULL 的场次
SELECT 'migrated flash_sales' AS note, COUNT(*) AS still_unmigrated
FROM flash_sale WHERE deleted = 0 AND source_product_id IS NULL;
SELECT 'flash products (KIND=FLASH)' AS note, COUNT(*) AS cnt FROM product WHERE kind = 'FLASH';
