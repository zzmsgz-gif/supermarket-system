-- 补回电商演示数据（商品 35 = ceshi 已验证缺 sku/attribute/brand/images）。
-- 仅插入演示用的商品扩展数据，不影响其他业务表。
-- 用法：mysql -u root -p supermarket_system --default-character-set=utf8mb4 < deploy/seed-demo-product-data.sql

USE supermarket_system;

-- 商品 35 基础字段：品牌 / 热门 / 新品 / 标签
UPDATE product
SET brand = '测试品牌',
    is_hot = 1,
    is_new = 1,
    sort_no = 100,
    tags = '热销,新品'
WHERE id = 35;

-- 图集（复用已有 OSS 封面图，两张）
INSERT INTO product_image (product_id, url, sort_no, deleted) VALUES
(35, 'https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ba0ddc7fd9f642599ba87d9c84c20160.png', 0, 0),
(35, 'https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ba0ddc7fd9f642599ba87d9c84c20160.png', 1, 0);

-- 多规格 SKU：红色/M、蓝色/L
INSERT INTO product_sku (product_id, spec_json, sku_code, image, sort_no, deleted) VALUES
(35, '{"color":"red","size":"M"}', 'SKU-RED-M', NULL, 0, 0),
(35, '{"color":"blue","size":"L"}', 'SKU-BLUE-L', NULL, 1, 0);

-- 商品参数 / 属性
INSERT INTO product_attribute (product_id, attr_name, attr_value, sort_no, deleted) VALUES
(35, '产地', '中国', 0, 0),
(35, '材质', '纯棉', 1, 0);
