-- 修复购物车「同商品不同规格被合并 / 无法加购」的根因：
-- 旧的 UNIQUE KEY uk_cart_user_product(user_id, product_id) 不允许同一商品出现两行，
-- 导致换规格加购被当作重复行、触发 409 Data conflict。
-- 改为 (user_id, product_id, sku_spec)，并用空串代表「无规格」，使不同规格成为独立行。

-- 1) 删除旧唯一键
ALTER TABLE cart_item DROP INDEX uk_cart_user_product;

-- 2) 历史 NULL 规格统一成空串
UPDATE cart_item SET sku_spec = '' WHERE sku_spec IS NULL;

-- 3) 防御性去重：同一 (用户, 商品, 规格) 仅保留一行（保留 id 最大者），避免新唯一键创建失败。
--    注意：仅对完全相同规格的行去重，不同规格的行互不影响。
DELETE t1 FROM cart_item t1
JOIN cart_item t2
  ON t1.user_id = t2.user_id
     AND t1.product_id = t2.product_id
     AND COALESCE(t1.sku_spec, '') = COALESCE(t2.sku_spec, '')
     AND t1.id < t2.id;

-- 4) sku_spec 改为 NOT NULL DEFAULT ''（空串=无规格），与新唯一键语义一致
ALTER TABLE cart_item MODIFY COLUMN sku_spec VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Selected SKU spec snapshot (empty = no spec)';

-- 5) 新唯一键：同一用户同一商品的不同规格是独立购物车行
ALTER TABLE cart_item ADD UNIQUE KEY uk_cart_user_product (user_id, product_id, sku_spec);
