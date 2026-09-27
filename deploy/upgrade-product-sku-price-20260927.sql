-- 规格定价：给商品多规格（product_sku）增加「规格价」列
-- 背景：同一商品不同规格（如矿泉水 350ml / 550ml / 1.5L）此前一律按商品基准价结算，不合逻辑。
-- 语义：price 为 NULL 表示该规格跟随商品基准价（兼容历史数据）；非 NULL 则按该规格价结算。
ALTER TABLE product_sku
    ADD COLUMN price DECIMAL(10, 2) DEFAULT NULL COMMENT '规格价；NULL=跟随商品基准价' AFTER sku_code;
